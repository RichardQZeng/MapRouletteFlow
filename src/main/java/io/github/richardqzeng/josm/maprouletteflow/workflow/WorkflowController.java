// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.swing.SwingUtilities;

import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.api.UnauthorizedException;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import org.openstreetmap.josm.tools.Logging;

import jakarta.annotation.Nullable;

/**
 * Owns the local MapRoulette workflow and its transient resources.
 *
 * <p>All mutations and notifications are performed on the Swing event dispatch thread. The snapshot intentionally
 * contains no authentication material.</p>
 */
public final class WorkflowController {
    /** Property fired when the observable workflow snapshot changes. */
    public static final String SNAPSHOT_PROPERTY = "snapshot";

    /** Workflow states agreed for the single-task workflow. */
    public enum State {
        DISCONNECTED,
        CHALLENGE_IDLE,
        RESERVED_PREVIEW,
        STARTING_DOWNLOAD,
        ACTIVE_EDITING,
        REDOWNLOADING,
        COMPLETION_DRAFT,
        WAITING_FOR_UPLOAD,
        SUBMITTING,
        RELEASING,
        RECOVERABLE_ERROR
    }

    /**
     * Immutable, credentials-free view of UI-observable workflow state.
     *
     * @param state current workflow state
     * @param activeChallenge selected challenge
     * @param reservedTask reserved candidate that has not entered editing
     * @param activeTask task being edited
     * @param completionDraft pending focused-workflow completion details
     * @param nextMode next-task selection mode
     * @param editLayer layer containing task edits
     * @param auxiliaryRetry post-commit operation that can be retried without resubmitting status
     * @param completionChangesetId correlated OSM changeset retained until Fixed completion finishes
     * @param completedTaskId most recently completed task, retained for Nearby reservation
     * @param reservationStatus latest candidate outcome for panel messaging
     * @param accountOwner non-secret account identity that owns pending work
     * @param completionStatusCommitted whether the status commit point has already passed
     * @param suspended whether transient task operations are paused during map-frame cleanup
     * @param lockedTasks all tasks locked by this workflow
     */
    public record Snapshot(State state, @Nullable Challenge activeChallenge, @Nullable Task reservedTask,
                           @Nullable Task activeTask, @Nullable CompletionDraft completionDraft, NextMode nextMode,
                            @Nullable OsmDataLayer editLayer, @Nullable CompletionAuxiliaryRetry auxiliaryRetry,
                             @Nullable Integer completionChangesetId, @Nullable Long completedTaskId,
                             @Nullable TaskReservationService.Status reservationStatus,
                              @Nullable AccountOwner accountOwner,
                              boolean completionStatusCommitted,
                               boolean suspended, List<Task> lockedTasks) {
    }

    /** Non-secret identity used to prevent task completion under a replacement account. */
    public record AccountOwner(String baseUrl, long mapRouletteUserId, long osmUserId) {
        public AccountOwner {
            baseUrl = AuthenticationManager.normalizeBaseUrl(baseUrl);
        }
    }

    private static final WorkflowController INSTANCE = new WorkflowController();

    private final PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    private final Map<Long, Task> lockedTasks = new TreeMap<>();
    private State state = State.DISCONNECTED;
    private State recoveryState;
    private State releaseReturnState;
    private State releaseRecoveryState;
    private Challenge activeChallenge;
    private Task reservedTask;
    private Task activeTask;
    private CompletionDraft completionDraft;
    private CompletionAuxiliaryRetry auxiliaryRetry;
    private Integer completionChangesetId;
    private Long completedTaskId;
    private TaskReservationService.Status reservationStatus;
    private AccountOwner accountOwner;
    private boolean completionStatusCommitted;
    private boolean suspended;
    private NextMode nextMode = NextMode.RANDOM;
    private OsmDataLayer editLayer;
    private Runnable reservationRefreshCleanup;
    private Runnable operationCleanup;
    private Runnable listenerCleanup;

    private WorkflowController() {
        // Singleton workflow owner.
    }

    public static WorkflowController getInstance() {
        return INSTANCE;
    }

    public Snapshot snapshot() {
        return onEdt(this::snapshotOnEdt);
    }

    public State state() {
        return snapshot().state();
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        listeners.addPropertyChangeListener(Objects.requireNonNull(listener));
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        listeners.removePropertyChangeListener(Objects.requireNonNull(listener));
    }

    /** Enter the authenticated, challenge-selection state. */
    public void connect() {
        mutate(() -> {
            if (state == State.DISCONNECTED) {
                transition(State.CHALLENGE_IDLE);
            }
        });
    }

    /** Bind idle work to an authenticated account without replacing ownership of pending work. */
    public void authenticatedAs(String baseUrl, AuthenticatedUser user) {
        Objects.requireNonNull(baseUrl);
        Objects.requireNonNull(user);
        final var candidate = new AccountOwner(baseUrl, user.id(), user.osmId());
        mutate(() -> {
            if (!hasPendingWorkOnEdt() || accountOwner == null || accountOwner.equals(candidate)) {
                accountOwner = candidate;
                if (state == State.DISCONNECTED) {
                    transition(State.CHALLENGE_IDLE);
                }
            }
        });
    }

    public boolean isOwnedBy(String baseUrl, AuthenticatedUser user) {
        if (user == null) {
            return false;
        }
        final var candidate = new AccountOwner(baseUrl, user.id(), user.osmId());
        return onEdt(() -> accountOwner != null && accountOwner.equals(candidate));
    }

    public boolean isCurrentOwnerAuthenticated() {
        if (io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getInstance() == null) {
            return false;
        }
        final var baseUrl = io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl();
        final var user = io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager
                .getAuthenticatedUser(baseUrl);
        return isOwnedBy(baseUrl, user);
    }

    public void requireCurrentOwnerAuthenticated() throws UnauthorizedException {
        if (snapshot().accountOwner() != null && !isCurrentOwnerAuthenticated()) {
            throw new UnauthorizedException("The MapRoulette account that owns this task is not authenticated");
        }
    }

    /** Leave an idle authenticated workflow. Pending work must be explicitly cleaned up instead. */
    public void disconnect() {
        mutate(() -> {
            requireState(State.CHALLENGE_IDLE);
            requireNoPendingWork();
            activeChallenge = null;
            completedTaskId = null;
            reservationStatus = null;
            accountOwner = null;
            transition(State.DISCONNECTED);
        });
    }

    /** Select or refresh the active challenge without replacing pending work. */
    public void selectChallenge(Challenge challenge) {
        Objects.requireNonNull(challenge);
        mutate(() -> {
            if (activeChallenge != null && activeChallenge.id() != challenge.id()) {
                requireState(State.CHALLENGE_IDLE);
                requireNoPendingWork();
            } else if (activeChallenge == null) {
                requireState(State.CHALLENGE_IDLE);
                requireNoPendingWork();
            }
            if (activeChallenge == null || activeChallenge.id() != challenge.id()) {
                completedTaskId = null;
                reservationStatus = null;
            }
            activeChallenge = challenge;
        });
    }

    /** Clear the selected challenge when no reservation, edit, or completion is pending. */
    public void clearChallenge() {
        mutate(() -> {
            requireState(State.CHALLENGE_IDLE);
            requireNoPendingWork();
            activeChallenge = null;
            completedTaskId = null;
            reservationStatus = null;
        });
    }

    /**
     * Whether calling a server endpoint that replaces the caller's reservation is safe.
     *
     * @return {@code true} only when no local lock or draft can be discarded
     */
    public boolean canRequestCandidate() {
        return onEdt(() -> state == State.CHALLENGE_IDLE && activeChallenge != null && !hasPendingWorkOnEdt());
    }

    /** Whether a status-committed task can safely advance to one next reservation. */
    public boolean canRequestNextCandidate(long challengeId, long completedId) {
        return onEdt(() -> state == State.SUBMITTING && activeChallenge != null
                && activeChallenge.id() == challengeId && activeTask != null && activeTask.id() == completedId
                && completionDraft != null && completionDraft.task().id() == completedId && auxiliaryRetry == null
                && lockedTasks.isEmpty());
    }

    /** Whether challenge metadata can safely replace the current challenge selection. */
    public boolean canSelectChallenge() {
        return onEdt(() -> state == State.CHALLENGE_IDLE && !hasPendingWorkOnEdt());
    }

    /** Accept exactly one server-reserved candidate. */
    public void reserveCandidate(Task task) {
        Objects.requireNonNull(task);
        mutate(() -> {
            if (!canRequestCandidateOnEdt()) {
                throw new IllegalStateException("A candidate cannot replace pending MapRoulette work");
            }
            if (task.parentId() != activeChallenge.id()) {
                throw new IllegalArgumentException("Reserved task does not belong to the active challenge");
            }
            reservedTask = task;
            reservationStatus = TaskReservationService.Status.RESERVED;
            lockedTasks.put(task.id(), task);
            transition(State.RESERVED_PREVIEW);
        });
    }

    /** Start preparing editable OSM data for the reserved task. */
    public void beginDownload(@Nullable Runnable cancelOperation) {
        mutate(() -> {
            requireState(State.RESERVED_PREVIEW);
            requireReservedTask();
            cleanupReservationRefresh();
            replaceOperationCleanup(cancelOperation);
            transition(State.STARTING_DOWNLOAD);
        });
    }

    /** Finish task download and enter active editing. */
    public void activateTask(Task task, OsmDataLayer layer) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(layer);
        mutate(() -> {
            requireState(State.STARTING_DOWNLOAD);
            requireSameTask(reservedTask, task);
            cleanupOperation();
            activeTask = task;
            reservedTask = null;
            editLayer = layer;
            lockedTasks.put(task.id(), task);
            transition(State.ACTIVE_EDITING);
        });
    }

    /** Cancel task download while retaining its reservation. */
    public void cancelDownload() {
        mutate(() -> {
            requireState(State.STARTING_DOWNLOAD);
            cleanupOperation();
            transition(State.RESERVED_PREVIEW);
        });
    }

    /** Start downloading fresh OSM data into the exact active edit layer. */
    public void beginRedownload(@Nullable Runnable cancelOperation) {
        mutate(() -> {
            requireState(State.ACTIVE_EDITING);
            requireActiveTaskAndLayer();
            replaceOperationCleanup(cancelOperation);
            transition(State.REDOWNLOADING);
        });
    }

    /** Retry a failed active-task re-download with a newly owned cancellation handle. */
    public void retryRedownload(@Nullable Runnable cancelOperation) {
        mutate(() -> {
            requireState(State.RECOVERABLE_ERROR);
            if (recoveryState != State.REDOWNLOADING) {
                throw new IllegalStateException("The recoverable operation is not an active task re-download");
            }
            requireActiveTaskAndLayer();
            replaceOperationCleanup(cancelOperation);
            recoveryState = null;
            transition(State.REDOWNLOADING);
        });
    }

    /** Finish an active-task re-download without replacing its task or edit layer. */
    public void redownloadSucceeded(Task task, OsmDataLayer layer) {
        Objects.requireNonNull(task);
        Objects.requireNonNull(layer);
        mutate(() -> {
            requireState(State.REDOWNLOADING);
            requireSameTask(activeTask, task);
            if (editLayer != layer) {
                throw new IllegalArgumentException("Re-download did not use the active task edit layer");
            }
            cleanupOperation();
            transition(State.ACTIVE_EDITING);
        });
    }

    /** Cancel an active-task re-download while retaining its task and exact edit layer. */
    public void cancelRedownload() {
        mutate(() -> {
            requireState(State.REDOWNLOADING);
            cleanupOperation();
            transition(State.ACTIVE_EDITING);
        });
    }

    /** Create the single completion draft for the active task. */
    public void draftCompletion(CompletionDraft draft) {
        Objects.requireNonNull(draft);
        mutate(() -> {
            requireState(State.ACTIVE_EDITING);
            requireSameTask(activeTask, draft.task());
            if (completionDraft != null) {
                throw new IllegalStateException("A completion draft is already pending");
            }
            completionDraft = draft;
            completionStatusCommitted = false;
            transition(State.COMPLETION_DRAFT);
        });
    }

    /** Replace the details of the existing draft without replacing its task. */
    public void updateCompletionDraft(CompletionDraft draft) {
        Objects.requireNonNull(draft);
        mutate(() -> {
            if (state != State.COMPLETION_DRAFT && state != State.WAITING_FOR_UPLOAD
                    && !(state == State.RECOVERABLE_ERROR && completionDraft != null)) {
                throw unexpectedState(State.COMPLETION_DRAFT);
            }
            requireSameTask(completionDraft.task(), draft.task());
            completionDraft = draft;
        });
    }

    /** Cancel completion and return to editing without losing the active task. */
    public void cancelCompletion() {
        mutate(() -> {
            requireState(State.COMPLETION_DRAFT);
            completionDraft = null;
            transition(State.ACTIVE_EDITING);
        });
    }

    /** Wait for a correlated successful upload before submission. */
    public void waitForUpload(Runnable removeListener) {
        Objects.requireNonNull(removeListener);
        mutate(() -> {
            requireState(State.COMPLETION_DRAFT);
            replaceListenerCleanup(removeListener);
            transition(State.WAITING_FOR_UPLOAD);
        });
    }

    /** Cancel upload coordination while preserving the completion draft. */
    public void cancelUpload() {
        mutate(() -> {
            requireState(State.WAITING_FOR_UPLOAD);
            cleanupListener();
            transition(State.COMPLETION_DRAFT);
        });
    }

    /** Begin status submission, directly or after a successful correlated upload. */
    public void beginSubmission() {
        mutate(() -> {
            if (state != State.COMPLETION_DRAFT && state != State.WAITING_FOR_UPLOAD) {
                throw unexpectedState(State.COMPLETION_DRAFT);
            }
            cleanupListener();
            transition(State.SUBMITTING);
        });
    }

    /** Record the status commit, including the backend's successful lock release. */
    public void statusCommitted(@Nullable CompletionAuxiliaryRetry retry) {
        mutate(() -> {
            requireState(State.SUBMITTING);
            if (activeTask == null || retry != null && retry.taskId() != activeTask.id()) {
                throw new IllegalArgumentException("Auxiliary retry does not match the active task");
            }
            lockedTasks.remove(activeTask.id());
            auxiliaryRetry = retry;
            completionStatusCommitted = true;
        });
    }

    /** Retain the verified OSM changeset across status and auxiliary retries. */
    public void setCompletionChangesetId(@Nullable Integer changesetId) {
        mutate(() -> {
            if (state != State.COMPLETION_DRAFT && state != State.WAITING_FOR_UPLOAD) {
                throw unexpectedState(State.COMPLETION_DRAFT);
            }
            completionChangesetId = changesetId;
        });
    }

    /** Advance the immutable post-status retry after one auxiliary operation succeeds. */
    public void updateAuxiliaryRetry(@Nullable CompletionAuxiliaryRetry retry) {
        mutate(() -> {
            requireState(State.SUBMITTING);
            if (activeTask == null || retry != null && retry.taskId() != activeTask.id()) {
                throw new IllegalArgumentException("Auxiliary retry does not match the active task");
            }
            auxiliaryRetry = retry;
        });
    }

    /** Finish submission without reserving another candidate. */
    public void submissionSucceeded() {
        mutate(() -> {
            requireState(State.SUBMITTING);
            clearCompletedTask();
            transition(State.CHALLENGE_IDLE);
        });
    }

    /** Finish completion when no automatic candidate could be reserved. */
    public void submissionSucceeded(TaskReservationService.Status terminalStatus) {
        Objects.requireNonNull(terminalStatus);
        if (terminalStatus == TaskReservationService.Status.RESERVED) {
            throw new IllegalArgumentException("A reserved result requires its task");
        }
        mutate(() -> {
            requireState(State.SUBMITTING);
            clearCompletedTask();
            reservationStatus = terminalStatus;
            transition(State.CHALLENGE_IDLE);
        });
    }

    /** Finish submission and accept the next candidate reserved by the server. */
    public void submissionSucceeded(Task nextTask) {
        Objects.requireNonNull(nextTask);
        mutate(() -> {
            requireState(State.SUBMITTING);
            if (activeChallenge == null || nextTask.parentId() != activeChallenge.id()) {
                throw new IllegalArgumentException("Reserved task does not belong to the active challenge");
            }
            clearCompletedTask();
            reservedTask = nextTask;
            reservationStatus = TaskReservationService.Status.RESERVED;
            lockedTasks.put(nextTask.id(), nextTask);
            transition(State.RESERVED_PREVIEW);
        });
    }

    /** Enter a retryable error state while preserving reservation, task, and draft context. */
    public void failRecoverably() {
        mutate(() -> {
            if (state == State.DISCONNECTED || state == State.CHALLENGE_IDLE || state == State.RECOVERABLE_ERROR
                    || state == State.RELEASING) {
                throw new IllegalStateException("There is no in-progress operation to recover");
            }
            recoveryState = state;
            cleanupTransientHandles();
            transition(State.RECOVERABLE_ERROR);
        });
    }

    /** Return to the state whose operation failed. */
    public void retry() {
        mutate(() -> {
            requireState(State.RECOVERABLE_ERROR);
            final var target = Objects.requireNonNull(recoveryState, "Missing recovery state");
            recoveryState = null;
            transition(target);
        });
    }

    /** Retry a failed task download with a newly owned cancellation handle. */
    public void retryDownload(@Nullable Runnable cancelOperation) {
        mutate(() -> {
            requireState(State.RECOVERABLE_ERROR);
            if (recoveryState != State.STARTING_DOWNLOAD || reservedTask == null || activeTask != null) {
                throw new IllegalStateException("The recoverable operation is not a task download");
            }
            cleanupReservationRefresh();
            replaceOperationCleanup(cancelOperation);
            recoveryState = null;
            transition(State.STARTING_DOWNLOAD);
        });
    }

    /** Whether the retained recoverable operation is an initial task download. */
    public boolean canRetryInitialDownload() {
        return onEdt(() -> state == State.RECOVERABLE_ERROR && recoveryState == State.STARTING_DOWNLOAD
                && reservedTask != null && activeTask == null);
    }

    /** Whether the retained recoverable operation is an active-task re-download. */
    public boolean canRetryActiveRedownload() {
        return onEdt(() -> state == State.RECOVERABLE_ERROR && recoveryState == State.REDOWNLOADING
                && activeTask != null && editLayer != null);
    }

    /** Whether the current retained task can be safely released before status submission. */
    public boolean canReleaseTask() {
        return onEdt(this::canReleaseTaskOnEdt);
    }

    /** Begin an asynchronous server release while retaining all workflow context. */
    public void beginRelease(@Nullable Runnable cancelOperation) {
        mutate(() -> {
            if (!canReleaseTaskOnEdt()) {
                throw new IllegalStateException("The current MapRoulette task cannot be released");
            }
            releaseReturnState = state;
            releaseRecoveryState = recoveryState;
            cleanupAllHandles();
            replaceOperationCleanup(cancelOperation);
            transition(State.RELEASING);
        });
    }

    /** Finish a server release and discard workflow references, without changing the edit layer or its data. */
    public void releaseSucceeded() {
        mutate(() -> {
            requireState(State.RELEASING);
            cleanupAllHandles();
            if (reservedTask != null) {
                lockedTasks.remove(reservedTask.id());
            }
            if (activeTask != null) {
                lockedTasks.remove(activeTask.id());
            }
            reservedTask = null;
            activeTask = null;
            completionDraft = null;
            auxiliaryRetry = null;
            completionChangesetId = null;
            completionStatusCommitted = false;
            reservationStatus = null;
            editLayer = null;
            recoveryState = null;
            releaseReturnState = null;
            releaseRecoveryState = null;
            transition(State.CHALLENGE_IDLE);
        });
    }

    /** Restore the exact retained workflow state after a server release fails or is canceled. */
    public void releaseFailed() {
        mutate(() -> {
            requireState(State.RELEASING);
            cleanupAllHandles();
            final var returnState = Objects.requireNonNull(releaseReturnState, "Missing release return state");
            final var returnRecoveryState = releaseRecoveryState;
            releaseReturnState = null;
            releaseRecoveryState = null;
            recoveryState = returnRecoveryState;
            transition(returnState);
        });
    }

    /** Release a preview reservation, including one retained after a recoverable error. */
    public void releaseReservation() {
        beginRelease(null);
        releaseSucceeded();
    }

    /** Install the reservation timer cleanup, replacing and cleaning any previous timer. */
    public void setReservationRefreshCleanup(Runnable cleanup) {
        Objects.requireNonNull(cleanup);
        onEdt(() -> {
            if (state != State.RESERVED_PREVIEW
                    && !(state == State.RECOVERABLE_ERROR && reservedTask != null)) {
                throw unexpectedState(State.RESERVED_PREVIEW);
            }
            cleanupReservationRefresh();
            reservationRefreshCleanup = cleanup;
            return null;
        });
    }

    /**
     * Clear all local workflow state and owned resources. This is the map-frame/plugin shutdown path.
     */
    public void shutdown() {
        mutate(() -> {
            cleanupAllHandles();
            lockedTasks.clear();
            activeChallenge = null;
            reservedTask = null;
            activeTask = null;
            completionDraft = null;
            auxiliaryRetry = null;
            completionChangesetId = null;
            completionStatusCommitted = false;
            completedTaskId = null;
            reservationStatus = null;
            accountOwner = null;
            editLayer = null;
            recoveryState = null;
            releaseReturnState = null;
            releaseRecoveryState = null;
            suspended = false;
            state = State.DISCONNECTED;
        });
    }

    public void setNextMode(NextMode mode) {
        Objects.requireNonNull(mode);
        mutate(() -> nextMode = mode);
    }

    /** Restore a persisted draft after its task and challenge are fetched under the original account. */
    public void restoreDraft(WorkflowDraftStore.StoredDraft stored, Challenge challenge, Task task,
            @Nullable OsmDataLayer layer) {
        Objects.requireNonNull(stored);
        Objects.requireNonNull(challenge);
        Objects.requireNonNull(task);
        mutate(() -> {
            if (state != State.DISCONNECTED && state != State.CHALLENGE_IDLE || hasPendingWorkOnEdt()) {
                throw new IllegalStateException("Workflow recovery requires an idle controller");
            }
            if (challenge.id() != stored.challengeId() || task.id() != stored.taskId()
                    || task.parentId() != challenge.id()) {
                throw new IllegalArgumentException("Recovered challenge or task does not match the stored draft");
            }
            accountOwner = stored.owner();
            activeChallenge = challenge;
            activeTask = task;
            completionDraft = stored.toCompletionDraft(task);
            auxiliaryRetry = stored.auxiliaryRetry();
            completionChangesetId = stored.changesetId();
            completionStatusCommitted = stored.statusCommitted();
            nextMode = stored.nextMode();
            editLayer = layer;
            lockedTasks.clear();
            if (!stored.statusCommitted()) {
                lockedTasks.put(task.id(), task);
            }
            recoveryState = completionStatusCommitted ? State.SUBMITTING : null;
            suspended = false;
            state = completionStatusCommitted ? State.RECOVERABLE_ERROR : State.COMPLETION_DRAFT;
        });
    }

    /** Stop transient workers and listeners while retaining task ownership for a later safe release. */
    public void suspend() {
        mutate(() -> {
            cleanupAllHandles();
            suspended = true;
        });
    }

    /** Resume user operations after cleanup could not safely release retained work. */
    public void resume() {
        mutate(() -> suspended = false);
    }

    public boolean addLockedTask(Task task) {
        Objects.requireNonNull(task);
        return mutateWithResult(() -> lockedTasks.put(task.id(), task) == null);
    }

    public boolean removeLockedTask(Task task) {
        Objects.requireNonNull(task);
        return mutateWithResult(() -> lockedTasks.remove(task.id()) != null);
    }

    public List<Task> getLockedTasks() {
        return snapshot().lockedTasks();
    }

    @Nullable
    public Task getLockedTask(long id) {
        return onEdt(() -> lockedTasks.get(id));
    }

    private void clearCompletedTask() {
        cleanupAllHandles();
        if (activeTask != null) {
            completedTaskId = activeTask.id();
            lockedTasks.remove(activeTask.id());
        }
        activeTask = null;
        completionDraft = null;
        auxiliaryRetry = null;
        completionChangesetId = null;
        completionStatusCommitted = false;
        editLayer = null;
        recoveryState = null;
    }

    private void requireNoPendingWork() {
        if (hasPendingWorkOnEdt()) {
            throw new IllegalStateException("Pending MapRoulette work must be resolved first");
        }
    }

    public boolean hasPendingWork() {
        return onEdt(this::hasPendingWorkOnEdt);
    }

    private boolean hasPendingWorkOnEdt() {
        return reservedTask != null || activeTask != null || completionDraft != null || !lockedTasks.isEmpty();
    }

    private boolean canRequestCandidateOnEdt() {
        return state == State.CHALLENGE_IDLE && activeChallenge != null && !hasPendingWorkOnEdt();
    }

    private boolean canReleaseTaskOnEdt() {
        if (completionStatusCommitted) {
            return false;
        }
        if (state == State.RESERVED_PREVIEW) {
            return reservedTask != null && activeTask == null;
        }
        if (state == State.ACTIVE_EDITING || state == State.COMPLETION_DRAFT) {
            return activeTask != null;
        }
        if (state != State.RECOVERABLE_ERROR || recoveryState == null) {
            return false;
        }
        return switch (recoveryState) {
        case RESERVED_PREVIEW, STARTING_DOWNLOAD -> reservedTask != null && activeTask == null;
        case ACTIVE_EDITING, REDOWNLOADING, COMPLETION_DRAFT -> activeTask != null;
        default -> false;
        };
    }

    private void requireReservedTask() {
        if (reservedTask == null) {
            throw new IllegalStateException("No task is reserved");
        }
    }

    private void requireActiveTaskAndLayer() {
        if (activeTask == null || editLayer == null) {
            throw new IllegalStateException("No active task edit layer is retained");
        }
    }

    private static void requireSameTask(Task expected, Task actual) {
        if (expected == null || expected.id() != actual.id()) {
            throw new IllegalArgumentException("Task does not match the pending workflow task");
        }
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw unexpectedState(expected);
        }
    }

    private IllegalStateException unexpectedState(State expected) {
        return new IllegalStateException("Expected workflow state " + expected + " but was " + state);
    }

    private void transition(State target) {
        if (!isLegalTransition(state, target)) {
            throw new IllegalStateException("Illegal workflow transition from " + state + " to " + target);
        }
        state = target;
    }

    /**
     * Check the declared transition graph.
     *
     * @param from source state
     * @param to target state
     * @return {@code true} if the transition is legal
     */
    public static boolean isLegalTransition(State from, State to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        return switch (from) {
        case DISCONNECTED -> to == State.CHALLENGE_IDLE;
        case CHALLENGE_IDLE -> to == State.DISCONNECTED || to == State.RESERVED_PREVIEW;
        case RESERVED_PREVIEW -> to == State.STARTING_DOWNLOAD || to == State.RELEASING
                || to == State.RECOVERABLE_ERROR;
        case STARTING_DOWNLOAD -> to == State.RESERVED_PREVIEW || to == State.ACTIVE_EDITING
                || to == State.RECOVERABLE_ERROR;
        case ACTIVE_EDITING -> to == State.REDOWNLOADING || to == State.COMPLETION_DRAFT || to == State.RELEASING
                || to == State.RECOVERABLE_ERROR;
        case REDOWNLOADING -> to == State.ACTIVE_EDITING || to == State.RECOVERABLE_ERROR;
        case COMPLETION_DRAFT -> to == State.ACTIVE_EDITING || to == State.WAITING_FOR_UPLOAD
                || to == State.SUBMITTING || to == State.RELEASING || to == State.RECOVERABLE_ERROR;
        case WAITING_FOR_UPLOAD -> to == State.COMPLETION_DRAFT || to == State.SUBMITTING
                || to == State.RECOVERABLE_ERROR;
        case SUBMITTING -> to == State.CHALLENGE_IDLE || to == State.RESERVED_PREVIEW
                || to == State.RECOVERABLE_ERROR;
        case RELEASING -> to == State.CHALLENGE_IDLE || to == State.RESERVED_PREVIEW
                || to == State.ACTIVE_EDITING || to == State.COMPLETION_DRAFT || to == State.RECOVERABLE_ERROR;
        case RECOVERABLE_ERROR -> to == State.CHALLENGE_IDLE || to == State.RESERVED_PREVIEW
                || to == State.STARTING_DOWNLOAD || to == State.ACTIVE_EDITING || to == State.REDOWNLOADING
                || to == State.COMPLETION_DRAFT || to == State.WAITING_FOR_UPLOAD || to == State.SUBMITTING
                || to == State.RELEASING;
        };
    }

    private Snapshot snapshotOnEdt() {
        return new Snapshot(state, activeChallenge, reservedTask, activeTask, completionDraft, nextMode, editLayer,
                auxiliaryRetry, completionChangesetId, completedTaskId, reservationStatus,
                accountOwner, completionStatusCommitted, suspended, List.copyOf(lockedTasks.values()));
    }

    private void mutate(Runnable mutation) {
        mutateWithResult(() -> {
            mutation.run();
            return null;
        });
    }

    private <T> T mutateWithResult(Callable<T> mutation) {
        return onEdt(() -> {
            final var before = snapshotOnEdt();
            final var result = mutation.call();
            final var after = snapshotOnEdt();
            if (!before.equals(after)) {
                WorkflowDraftStore.update(before, after);
                listeners.firePropertyChange(SNAPSHOT_PROPERTY, before, after);
            }
            return result;
        });
    }

    private static <T> T onEdt(Callable<T> callable) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return callable.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        final var task = new FutureTask<>(callable);
        try {
            SwingUtilities.invokeAndWait(task);
            return task.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while updating MapRoulette workflow", exception);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            throw new IllegalStateException("Could not update MapRoulette workflow", exception.getCause());
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(exception.getCause());
        }
    }

    private void replaceOperationCleanup(@Nullable Runnable cleanup) {
        cleanupOperation();
        operationCleanup = cleanup;
    }

    private void replaceListenerCleanup(Runnable cleanup) {
        cleanupListener();
        listenerCleanup = cleanup;
    }

    private void cleanupTransientHandles() {
        cleanupReservationRefresh();
        cleanupOperation();
        cleanupListener();
    }

    private void cleanupAllHandles() {
        cleanupTransientHandles();
    }

    private void cleanupReservationRefresh() {
        reservationRefreshCleanup = runCleanup(reservationRefreshCleanup);
    }

    private void cleanupOperation() {
        operationCleanup = runCleanup(operationCleanup);
    }

    private void cleanupListener() {
        listenerCleanup = runCleanup(listenerCleanup);
    }

    private static Runnable runCleanup(@Nullable Runnable cleanup) {
        if (cleanup != null) {
            try {
                cleanup.run();
            } catch (RuntimeException exception) {
                Logging.warn(exception);
            }
        }
        return null;
    }
}
