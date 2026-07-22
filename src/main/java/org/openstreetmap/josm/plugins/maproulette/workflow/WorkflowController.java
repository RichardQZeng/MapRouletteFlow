// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

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
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.ModifiedTask;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
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
        COMPLETION_DRAFT,
        WAITING_FOR_UPLOAD,
        SUBMITTING,
        RECOVERABLE_ERROR
    }

    /**
     * Immutable, credentials-free view of UI-observable workflow state.
     *
     * @param state current workflow state
     * @param activeChallenge selected challenge
     * @param reservedTask reserved candidate that has not entered editing
     * @param activeTask task being edited
     * @param completionDraft pending completion details
     * @param nextMode next-task selection mode
     * @param editLayer layer containing task edits
     * @param lockedTasks compatibility view of all tasks locked by the existing UI
     * @param completionDrafts compatibility view of all drafts created by the existing UI
     */
    public record Snapshot(State state, @Nullable Challenge activeChallenge, @Nullable Task reservedTask,
                           @Nullable Task activeTask, @Nullable ModifiedTask completionDraft, NextMode nextMode,
                           @Nullable OsmDataLayer editLayer, List<Task> lockedTasks,
                           List<ModifiedTask> completionDrafts) {
    }

    private static final WorkflowController INSTANCE = new WorkflowController();

    private final PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    private final Map<Long, Task> lockedTasks = new TreeMap<>();
    private final Map<Long, ModifiedTask> completionDrafts = new TreeMap<>();
    private State state = State.DISCONNECTED;
    private State recoveryState;
    private Challenge activeChallenge;
    private Task reservedTask;
    private Task activeTask;
    private ModifiedTask completionDraft;
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

    /** Leave an idle authenticated workflow. Pending work must be explicitly cleaned up instead. */
    public void disconnect() {
        mutate(() -> {
            requireState(State.CHALLENGE_IDLE);
            requireNoPendingWork();
            activeChallenge = null;
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
            activeChallenge = challenge;
        });
    }

    /** Clear the selected challenge when no reservation, edit, or completion is pending. */
    public void clearChallenge() {
        mutate(() -> {
            requireState(State.CHALLENGE_IDLE);
            requireNoPendingWork();
            activeChallenge = null;
        });
    }

    /**
     * Whether calling a server endpoint that replaces the caller's reservation is safe.
     *
     * @return {@code true} only when no local lock or draft can be discarded
     */
    public boolean canRequestCandidate() {
        return onEdt(() -> state == State.CHALLENGE_IDLE && activeChallenge != null && !hasPendingWork());
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

    /** Create the single completion draft for the active task. */
    public void draftCompletion(ModifiedTask draft) {
        Objects.requireNonNull(draft);
        mutate(() -> {
            requireState(State.ACTIVE_EDITING);
            requireSameTask(activeTask, draft.task());
            if (completionDraft != null) {
                throw new IllegalStateException("A completion draft is already pending");
            }
            completionDraft = draft;
            completionDrafts.put(draft.task().id(), draft);
            transition(State.COMPLETION_DRAFT);
        });
    }

    /** Replace the details of the existing draft without replacing its task. */
    public void updateCompletionDraft(ModifiedTask draft) {
        Objects.requireNonNull(draft);
        mutate(() -> {
            if (state != State.COMPLETION_DRAFT && state != State.WAITING_FOR_UPLOAD
                    && !(state == State.RECOVERABLE_ERROR && completionDraft != null)) {
                throw unexpectedState(State.COMPLETION_DRAFT);
            }
            requireSameTask(completionDraft.task(), draft.task());
            completionDraft = draft;
            completionDrafts.put(draft.task().id(), draft);
        });
    }

    /** Cancel completion and return to editing without losing the active task. */
    public void cancelCompletion() {
        mutate(() -> {
            requireState(State.COMPLETION_DRAFT);
            completionDrafts.remove(completionDraft.task().id());
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

    /** Finish submission without reserving another candidate. */
    public void submissionSucceeded() {
        mutate(() -> {
            requireState(State.SUBMITTING);
            clearCompletedTask();
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
            lockedTasks.put(nextTask.id(), nextTask);
            transition(State.RESERVED_PREVIEW);
        });
    }

    /** Enter a retryable error state while preserving reservation, task, and draft context. */
    public void failRecoverably() {
        mutate(() -> {
            if (state == State.DISCONNECTED || state == State.CHALLENGE_IDLE || state == State.RECOVERABLE_ERROR) {
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

    /** Release a preview reservation, including one retained after a recoverable error. */
    public void releaseReservation() {
        mutate(() -> {
            if (state != State.RESERVED_PREVIEW
                    && !(state == State.RECOVERABLE_ERROR && reservedTask != null && activeTask == null)) {
                throw unexpectedState(State.RESERVED_PREVIEW);
            }
            cleanupAllHandles();
            lockedTasks.remove(reservedTask.id());
            reservedTask = null;
            recoveryState = null;
            transition(State.CHALLENGE_IDLE);
        });
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
            completionDrafts.clear();
            activeChallenge = null;
            reservedTask = null;
            activeTask = null;
            completionDraft = null;
            editLayer = null;
            recoveryState = null;
            state = State.DISCONNECTED;
        });
    }

    public void setNextMode(NextMode mode) {
        Objects.requireNonNull(mode);
        mutate(() -> nextMode = mode);
    }

    // Compatibility collection operations used while the existing multi-task UI is migrated.

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

    public boolean addCompletionDraft(ModifiedTask draft) {
        Objects.requireNonNull(draft);
        return mutateWithResult(() -> completionDrafts.put(draft.task().id(), draft) == null);
    }

    public boolean removeCompletionDraft(ModifiedTask draft) {
        Objects.requireNonNull(draft);
        return mutateWithResult(() -> completionDrafts.remove(draft.task().id()) != null);
    }

    public List<ModifiedTask> getCompletionDrafts() {
        return snapshot().completionDrafts();
    }

    @Nullable
    public ModifiedTask getCompletionDraft(long id) {
        return onEdt(() -> completionDrafts.get(id));
    }

    private void clearCompletedTask() {
        cleanupAllHandles();
        if (activeTask != null) {
            lockedTasks.remove(activeTask.id());
            completionDrafts.remove(activeTask.id());
        }
        activeTask = null;
        completionDraft = null;
        editLayer = null;
        recoveryState = null;
    }

    private void requireNoPendingWork() {
        if (hasPendingWork()) {
            throw new IllegalStateException("Pending MapRoulette work must be resolved first");
        }
    }

    private boolean hasPendingWork() {
        return reservedTask != null || activeTask != null || completionDraft != null || !lockedTasks.isEmpty()
                || !completionDrafts.isEmpty();
    }

    private boolean canRequestCandidateOnEdt() {
        return state == State.CHALLENGE_IDLE && activeChallenge != null && !hasPendingWork();
    }

    private void requireReservedTask() {
        if (reservedTask == null) {
            throw new IllegalStateException("No task is reserved");
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
        case RESERVED_PREVIEW -> to == State.CHALLENGE_IDLE || to == State.STARTING_DOWNLOAD
                || to == State.RECOVERABLE_ERROR;
        case STARTING_DOWNLOAD -> to == State.RESERVED_PREVIEW || to == State.ACTIVE_EDITING
                || to == State.RECOVERABLE_ERROR;
        case ACTIVE_EDITING -> to == State.COMPLETION_DRAFT || to == State.RECOVERABLE_ERROR;
        case COMPLETION_DRAFT -> to == State.ACTIVE_EDITING || to == State.WAITING_FOR_UPLOAD
                || to == State.SUBMITTING || to == State.RECOVERABLE_ERROR;
        case WAITING_FOR_UPLOAD -> to == State.COMPLETION_DRAFT || to == State.SUBMITTING
                || to == State.RECOVERABLE_ERROR;
        case SUBMITTING -> to == State.CHALLENGE_IDLE || to == State.RESERVED_PREVIEW
                || to == State.RECOVERABLE_ERROR;
        case RECOVERABLE_ERROR -> to == State.CHALLENGE_IDLE || to == State.RESERVED_PREVIEW
                || to == State.STARTING_DOWNLOAD || to == State.ACTIVE_EDITING || to == State.COMPLETION_DRAFT
                || to == State.WAITING_FOR_UPLOAD || to == State.SUBMITTING;
        };
    }

    private Snapshot snapshotOnEdt() {
        return new Snapshot(state, activeChallenge, reservedTask, activeTask, completionDraft, nextMode, editLayer,
                List.copyOf(lockedTasks.values()), List.copyOf(completionDrafts.values()));
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
