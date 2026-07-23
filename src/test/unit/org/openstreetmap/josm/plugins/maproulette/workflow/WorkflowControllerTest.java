// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

/** Tests for {@link WorkflowController}. */
@BasicPreferences
class WorkflowControllerTest {
    private final WorkflowController workflow = WorkflowController.getInstance();

    @BeforeEach
    void setUp() {
        workflow.shutdown();
    }

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void declaredTransitionGraphContainsOnlyAgreedTransitions() {
        final var expected = new EnumMap<State, EnumSet<State>>(State.class);
        expected.put(State.DISCONNECTED, EnumSet.of(State.CHALLENGE_IDLE));
        expected.put(State.CHALLENGE_IDLE, EnumSet.of(State.DISCONNECTED, State.RESERVED_PREVIEW));
        expected.put(State.RESERVED_PREVIEW,
                EnumSet.of(State.STARTING_DOWNLOAD, State.RELEASING, State.RECOVERABLE_ERROR));
        expected.put(State.STARTING_DOWNLOAD,
                EnumSet.of(State.RESERVED_PREVIEW, State.ACTIVE_EDITING, State.RECOVERABLE_ERROR));
        expected.put(State.ACTIVE_EDITING,
                EnumSet.of(State.REDOWNLOADING, State.COMPLETION_DRAFT, State.RELEASING, State.RECOVERABLE_ERROR));
        expected.put(State.REDOWNLOADING, EnumSet.of(State.ACTIVE_EDITING, State.RECOVERABLE_ERROR));
        expected.put(State.COMPLETION_DRAFT, EnumSet.of(State.ACTIVE_EDITING, State.WAITING_FOR_UPLOAD,
                State.SUBMITTING, State.RELEASING, State.RECOVERABLE_ERROR));
        expected.put(State.WAITING_FOR_UPLOAD,
                EnumSet.of(State.COMPLETION_DRAFT, State.SUBMITTING, State.RECOVERABLE_ERROR));
        expected.put(State.SUBMITTING,
                EnumSet.of(State.CHALLENGE_IDLE, State.RESERVED_PREVIEW, State.RECOVERABLE_ERROR));
        expected.put(State.RELEASING, EnumSet.of(State.CHALLENGE_IDLE, State.RESERVED_PREVIEW,
                State.ACTIVE_EDITING, State.COMPLETION_DRAFT, State.RECOVERABLE_ERROR));
        expected.put(State.RECOVERABLE_ERROR, EnumSet.of(State.CHALLENGE_IDLE, State.RESERVED_PREVIEW,
                State.STARTING_DOWNLOAD, State.ACTIVE_EDITING, State.REDOWNLOADING, State.COMPLETION_DRAFT,
                State.WAITING_FOR_UPLOAD, State.SUBMITTING, State.RELEASING));

        for (var from : State.values()) {
            for (var to : State.values()) {
                assertEquals(expected.get(from).contains(to), WorkflowController.isLegalTransition(from, to),
                        () -> from + " -> " + to);
            }
        }
    }

    @Test
    void nominalWorkflowExercisesLegalTransitions() {
        final var challenge = challenge(10);
        final var task = task(100, challenge.id());
        final var nextTask = task(101, challenge.id());
        final var operationCleanup = new AtomicInteger();
        final var listenerCleanup = new AtomicInteger();

        workflow.connect();
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(task);
        assertEquals(State.RESERVED_PREVIEW, workflow.state());
        workflow.beginDownload(operationCleanup::incrementAndGet);
        assertEquals(State.STARTING_DOWNLOAD, workflow.state());
        workflow.activateTask(task, layer());
        assertEquals(State.ACTIVE_EDITING, workflow.state());
        assertEquals(1, operationCleanup.get());
        workflow.draftCompletion(draft(task));
        assertEquals(State.COMPLETION_DRAFT, workflow.state());
        workflow.waitForUpload(listenerCleanup::incrementAndGet);
        assertEquals(State.WAITING_FOR_UPLOAD, workflow.state());
        workflow.beginSubmission();
        assertEquals(State.SUBMITTING, workflow.state());
        assertEquals(1, listenerCleanup.get());
        workflow.submissionSucceeded(nextTask);
        assertEquals(State.RESERVED_PREVIEW, workflow.state());
        assertSame(nextTask, workflow.snapshot().reservedTask());
        workflow.releaseReservation();
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
        workflow.clearChallenge();
        workflow.disconnect();
        assertEquals(State.DISCONNECTED, workflow.state());
    }

    @Test
    void cancellationAndDirectSubmissionTransitionsPreserveContext() {
        final var task = enterReservedWorkflow();
        workflow.beginDownload(null);
        workflow.cancelDownload();
        assertEquals(State.RESERVED_PREVIEW, workflow.state());

        workflow.beginDownload(null);
        workflow.activateTask(task, layer());
        workflow.draftCompletion(draft(task));
        workflow.waitForUpload(() -> { });
        workflow.cancelUpload();
        assertEquals(State.COMPLETION_DRAFT, workflow.state());
        assertSame(task, workflow.snapshot().activeTask());
        assertSame(task, workflow.snapshot().completionDraft().task());

        workflow.cancelCompletion();
        assertEquals(State.ACTIVE_EDITING, workflow.state());
        assertNull(workflow.snapshot().completionDraft());
        workflow.draftCompletion(draft(task));
        workflow.beginSubmission();
        workflow.submissionSucceeded();
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
        assertTrue(workflow.getLockedTasks().isEmpty());
        assertTrue(workflow.getCompletionDrafts().isEmpty());
    }

    @Test
    void activeRedownloadTransitionsRetainExactTaskAndLayer() {
        final var task = enterReservedWorkflow();
        final var layer = layer();
        workflow.beginDownload(null);
        workflow.activateTask(task, layer);

        workflow.beginRedownload(null);
        assertEquals(State.REDOWNLOADING, workflow.state());
        assertSame(task, workflow.snapshot().activeTask());
        assertSame(layer, workflow.snapshot().editLayer());
        workflow.cancelRedownload();
        assertEquals(State.ACTIVE_EDITING, workflow.state());

        workflow.beginRedownload(null);
        workflow.failRecoverably();
        assertTrue(workflow.canRetryActiveRedownload());
        assertFalse(workflow.canRetryInitialDownload());
        workflow.retryRedownload(null);
        workflow.redownloadSucceeded(task, layer);
        assertEquals(State.ACTIVE_EDITING, workflow.state());
        assertSame(layer, workflow.snapshot().editLayer());
    }

    @Test
    void releaseFailureRestoresPreviewActiveDraftAndRecoverableContext() {
        enterReservedWorkflow();
        assertReleaseFailureRestoresSnapshot();

        final var task = workflow.snapshot().reservedTask();
        workflow.beginDownload(null);
        workflow.failRecoverably();
        assertTrue(workflow.canRetryInitialDownload());
        assertReleaseFailureRestoresSnapshot();

        workflow.retryDownload(null);
        final var layer = layer();
        workflow.activateTask(task, layer);
        assertReleaseFailureRestoresSnapshot();

        workflow.beginRedownload(null);
        workflow.failRecoverably();
        assertTrue(workflow.canRetryActiveRedownload());
        assertReleaseFailureRestoresSnapshot();
        workflow.retryRedownload(null);
        workflow.redownloadSucceeded(task, layer);

        workflow.draftCompletion(draft(task));
        assertReleaseFailureRestoresSnapshot();
        workflow.failRecoverably();
        assertReleaseFailureRestoresSnapshot();
    }

    @Test
    void releaseSuccessClearsWorkflowReferencesWithoutCompletingOrChangingLayerData() {
        final var task = enterReservedWorkflow();
        final var layer = layer();
        final var data = layer.getDataSet();
        workflow.beginDownload(null);
        workflow.activateTask(task, layer);
        workflow.draftCompletion(draft(task));

        workflow.beginRelease(null);
        assertEquals(State.RELEASING, workflow.state());
        assertThrows(IllegalStateException.class, workflow::beginSubmission);
        workflow.releaseSucceeded();

        final var snapshot = workflow.snapshot();
        assertEquals(State.CHALLENGE_IDLE, snapshot.state());
        assertNull(snapshot.reservedTask());
        assertNull(snapshot.activeTask());
        assertNull(snapshot.completionDraft());
        assertNull(snapshot.editLayer());
        assertNull(snapshot.completedTaskId());
        assertTrue(snapshot.lockedTasks().isEmpty());
        assertSame(data, layer.getDataSet());
    }

    @Test
    void releaseIsForbiddenDuringUploadAndSubmissionIncludingRecoverableSubmission() {
        final var task = enterReservedWorkflow();
        workflow.beginDownload(null);
        workflow.activateTask(task, layer());
        workflow.draftCompletion(draft(task));
        workflow.waitForUpload(() -> { });

        assertFalse(workflow.canReleaseTask());
        assertThrows(IllegalStateException.class, () -> workflow.beginRelease(null));
        workflow.beginSubmission();
        assertFalse(workflow.canReleaseTask());
        assertThrows(IllegalStateException.class, () -> workflow.beginRelease(null));
        workflow.statusCommitted(null);
        assertTrue(workflow.snapshot().completionStatusCommitted());
        assertFalse(workflow.canReleaseTask());
        workflow.failRecoverably();
        assertFalse(workflow.canReleaseTask());
        assertThrows(IllegalStateException.class, () -> workflow.beginRelease(null));
    }

    @Test
    void everyInProgressStateCanFailAndRetryWithoutLosingContext() {
        final var task = enterReservedWorkflow();
        failAndRetry(State.RESERVED_PREVIEW);
        workflow.beginDownload(null);
        failAndRetry(State.STARTING_DOWNLOAD);
        workflow.activateTask(task, layer());
        failAndRetry(State.ACTIVE_EDITING);
        workflow.draftCompletion(draft(task));
        failAndRetry(State.COMPLETION_DRAFT);
        workflow.waitForUpload(() -> { });
        failAndRetry(State.WAITING_FOR_UPLOAD);
        workflow.beginSubmission();
        failAndRetry(State.SUBMITTING);

        assertSame(task, workflow.snapshot().activeTask());
        assertSame(task, workflow.snapshot().completionDraft().task());
        workflow.submissionSucceeded();
    }

    @Test
    void challengeAndCandidateCannotReplacePendingWork() {
        final var originalChallenge = challenge(10);
        final var originalTask = task(100, originalChallenge.id());
        workflow.connect();
        workflow.selectChallenge(originalChallenge);
        workflow.reserveCandidate(originalTask);

        assertFalse(workflow.canRequestCandidate());
        assertThrows(IllegalStateException.class, () -> workflow.selectChallenge(challenge(11)));
        assertThrows(IllegalStateException.class,
                () -> workflow.reserveCandidate(task(101, originalChallenge.id())));
        assertSame(originalChallenge, workflow.snapshot().activeChallenge());
        assertSame(originalTask, workflow.snapshot().reservedTask());
        assertEquals(1, workflow.getLockedTasks().size());
    }

    @Test
    void legacyPendingWorkAlsoBlocksChallengeSwitchAndCandidateRequest() {
        final var challenge = challenge(10);
        final var task = task(100, challenge.id());
        workflow.connect();
        workflow.selectChallenge(challenge);
        workflow.addLockedTask(task);

        assertFalse(workflow.canRequestCandidate());
        assertThrows(IllegalStateException.class, () -> workflow.selectChallenge(challenge(11)));
        assertThrows(IllegalStateException.class, () -> workflow.reserveCandidate(task(101, challenge.id())));
    }

    @Test
    void cleanupHandlesRunOnceOnReleaseCancellationErrorsAndShutdown() {
        final var cleanup = new AtomicInteger();
        enterReservedWorkflow();
        workflow.setReservationRefreshCleanup(cleanup::incrementAndGet);
        workflow.releaseReservation();
        workflow.shutdown();
        assertEquals(1, cleanup.get());

        final var task = enterReservedWorkflow();
        workflow.beginDownload(cleanup::incrementAndGet);
        workflow.cancelDownload();
        assertEquals(2, cleanup.get());
        workflow.beginDownload(cleanup::incrementAndGet);
        workflow.failRecoverably();
        workflow.shutdown();
        assertEquals(3, cleanup.get());

        final var waitingTask = enterReservedWorkflow();
        workflow.beginDownload(null);
        workflow.activateTask(waitingTask, layer());
        workflow.draftCompletion(draft(waitingTask));
        workflow.waitForUpload(cleanup::incrementAndGet);
        workflow.cancelUpload();
        assertEquals(4, cleanup.get());
        workflow.waitForUpload(cleanup::incrementAndGet);
        workflow.failRecoverably();
        workflow.shutdown();
        assertEquals(5, cleanup.get());

        enterReservedWorkflow();
        workflow.setReservationRefreshCleanup(cleanup::incrementAndGet);
        workflow.shutdown();
        workflow.shutdown();
        assertEquals(6, cleanup.get());
    }

    @Test
    void releaseOperationCleanupRunsOnceOnFailureAndSuccess() {
        final var cleanup = new AtomicInteger();
        enterReservedWorkflow();
        workflow.beginRelease(cleanup::incrementAndGet);
        workflow.releaseFailed();
        assertEquals(1, cleanup.get());

        workflow.beginRelease(cleanup::incrementAndGet);
        workflow.releaseSucceeded();
        workflow.shutdown();
        assertEquals(2, cleanup.get());
    }

    @Test
    void notificationFromWorkerThreadRunsOnSwingEdt() throws InterruptedException {
        final var notified = new AtomicBoolean();
        final var listener = new AtomicBoolean();
        final var propertyListener = (java.beans.PropertyChangeListener) event -> {
            notified.set(true);
            listener.set(SwingUtilities.isEventDispatchThread());
        };
        workflow.addPropertyChangeListener(propertyListener);
        try {
            final var worker = new Thread(workflow::connect, "workflow-test-worker");
            worker.start();
            worker.join();
        } finally {
            workflow.removePropertyChangeListener(propertyListener);
        }
        assertTrue(notified.get());
        assertTrue(listener.get());
    }

    @Test
    void observableSnapshotContainsNoCredentialField() {
        assertTrue(java.util.Arrays.stream(WorkflowController.Snapshot.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(java.util.Locale.ROOT))
                .noneMatch(name -> name.contains("credential") || name.contains("key") || name.contains("token")));
    }

    @Test
    void activeWorkflowRemainsBoundToItsOriginalNonSecretAccount() {
        final var original = new org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser(1, 10,
                "original", 0);
        final var replacement = new org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser(2, 20,
                "replacement", 0);
        workflow.authenticatedAs("https://maproulette.example/api/v2", original);
        workflow.selectChallenge(challenge(10));
        workflow.reserveCandidate(task(100, 10));

        workflow.authenticatedAs("https://maproulette.example/api/v2", replacement);

        assertTrue(workflow.isOwnedBy("https://maproulette.example/api/v2", original));
        assertFalse(workflow.isOwnedBy("https://maproulette.example/api/v2", replacement));
        assertEquals(1, workflow.snapshot().accountOwner().mapRouletteUserId());
    }

    @Test
    void idleWorkflowCanSwitchAuthenticatedOwner() {
        final var first = new org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser(1, 10, "first",
                0);
        final var second = new org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser(2, 20, "second",
                0);
        workflow.authenticatedAs("https://maproulette.example/api/v2", first);

        workflow.authenticatedAs("https://maproulette.example/api/v2", second);

        assertTrue(workflow.isOwnedBy("https://maproulette.example/api/v2", second));
    }

    private void failAndRetry(State expected) {
        assertEquals(expected, workflow.state());
        final var before = workflow.snapshot();
        workflow.failRecoverably();
        assertEquals(State.RECOVERABLE_ERROR, workflow.state());
        workflow.retry();
        assertEquals(expected, workflow.state());
        assertEquals(before.activeChallenge(), workflow.snapshot().activeChallenge());
        assertEquals(before.reservedTask(), workflow.snapshot().reservedTask());
        assertEquals(before.activeTask(), workflow.snapshot().activeTask());
        assertEquals(before.completionDraft(), workflow.snapshot().completionDraft());
    }

    private void assertReleaseFailureRestoresSnapshot() {
        final var before = workflow.snapshot();
        assertTrue(workflow.canReleaseTask());
        workflow.beginRelease(null);
        assertEquals(State.RELEASING, workflow.state());
        assertFalse(workflow.canReleaseTask());
        workflow.releaseFailed();
        assertEquals(before, workflow.snapshot());
    }

    private Task enterReservedWorkflow() {
        workflow.shutdown();
        final var challenge = challenge(10);
        final var task = task(100, challenge.id());
        workflow.connect();
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(task);
        return task;
    }

    private static CompletionDraft draft(Task task) {
        return new CompletionDraft(task, CompletionResult.FIXED, "", "", null, Map.of(), NextMode.RANDOM);
    }

    private static OsmDataLayer layer() {
        return new OsmDataLayer(new DataSet(), "workflow-test", null);
    }

    private static Task task(long id, long challengeId) {
        return new Task(id, "task", null, null, challengeId, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
    }

    private static Challenge challenge(long id) {
        return new Challenge(id, "challenge", null, null, null, false, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
