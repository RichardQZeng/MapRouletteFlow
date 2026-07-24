// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController.State;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class CompletionSubmissionControllerTest {
    private final WorkflowController workflow = WorkflowController.getInstance();
    private FakeGateway gateway;
    private CompletionSubmissionController controller;
    private Task task;
    private TaskReservationService.Result nextResult;
    private IOException nextFailure;
    private int nextCalls;
    private CompletionDraft nextDraft;

    @BeforeEach
    void setUp() {
        workflow.shutdown();
        gateway = new FakeGateway();
        nextResult = new TaskReservationService.Result(TaskReservationService.Status.EMPTY, null);
        controller = new CompletionSubmissionController(workflow, gateway, draft -> {
            nextCalls++;
            nextDraft = draft;
            if (nextFailure != null) {
                throw nextFailure;
            }
            if (nextResult.status() == TaskReservationService.Status.RESERVED) {
                workflow.submissionSucceeded(nextResult.task());
            }
            return nextResult;
        });
        task = enterActiveWorkflow();
    }

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void cancelHasZeroWorkflowOrApiMutations() {
        final var before = workflow.snapshot();
        controller.cancel();
        assertEquals(before, workflow.snapshot());
        assertEquals(0, gateway.statusCalls);
        assertEquals(0, gateway.commentCalls);
    }

    @Test
    void nonFixedTransitionsThroughDraftSubmittingAndClearsOnlyAfterRequiredOperations() throws IOException {
        final var observed = new ArrayList<State>();
        final var listener = (java.beans.PropertyChangeListener) event -> observed.add(workflow.state());
        workflow.addPropertyChangeListener(listener);
        try {
            controller.submit(draft("comment"));
        } finally {
            workflow.removePropertyChangeListener(listener);
        }

        assertEquals(1, gateway.statusCalls);
        assertEquals(1, gateway.commentCalls);
        assertEquals(1, nextCalls);
        assertTrue(observed.contains(State.COMPLETION_DRAFT));
        assertTrue(observed.contains(State.SUBMITTING));
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
        assertEquals(TaskReservationService.Status.EMPTY, workflow.snapshot().reservationStatus());
        assertNull(workflow.snapshot().activeTask());
        assertNull(workflow.snapshot().completionDraft());
        assertFalse(workflow.getLockedTasks().stream().anyMatch(locked -> locked.id() == task.id()));
    }

    @Test
    void statusFailureKeepsLockAndDraftAndCanRetryStatus() throws IOException {
        gateway.failStatus = true;
        assertThrows(IOException.class, () -> controller.submit(draft("")));

        assertEquals(State.RECOVERABLE_ERROR, workflow.state());
        assertSame(task, workflow.snapshot().activeTask());
        assertNotNull(workflow.snapshot().completionDraft());
        assertNull(workflow.snapshot().auxiliaryRetry());
        assertEquals(1, workflow.getLockedTasks().size());

        gateway.failStatus = false;
        controller.submit(draft(""));
        assertEquals(2, gateway.statusCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
    }

    @Test
    void auxiliaryFailureDropsReleasedLockAndRetryNeverResubmitsStatus() throws IOException {
        gateway.failComment = true;
        assertThrows(IOException.class, () -> controller.submit(draft("recover me")));

        assertEquals(State.RECOVERABLE_ERROR, workflow.state());
        assertEquals(1, gateway.statusCalls);
        assertEquals(1, gateway.commentCalls);
        assertEquals(0, nextCalls);
        assertNotNull(workflow.snapshot().auxiliaryRetry());
        assertTrueNoTaskLock();
        assertNotNull(workflow.snapshot().completionDraft());

        gateway.failComment = false;
        controller.retryAuxiliary();

        assertEquals(1, gateway.statusCalls);
        assertEquals(2, gateway.commentCalls);
        assertEquals(1, nextCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
    }

    @Test
    void auxiliaryRetryRejectsEditableResubmission() {
        gateway.failComment = true;
        assertThrows(IOException.class, () -> controller.submit(draft("committed comment")));

        assertThrows(IllegalStateException.class,
                () -> controller.submit(new CompletionDraft(task, CompletionResult.NOT_AN_ISSUE, "different", "",
                        null, Map.of(), NextMode.NEARBY)));
        assertEquals(1, gateway.statusCalls);
        assertEquals(1, gateway.commentCalls);
    }

    @Test
    void fixedIsPreservedAsDraftAndNeverSubmitted() {
        final var fixed = new CompletionDraft(task, CompletionResult.FIXED, "note", "tag", null, Map.of(),
                NextMode.NEARBY);
        controller.preserveFixedDraft(fixed);

        assertEquals(State.COMPLETION_DRAFT, workflow.state());
        assertEquals(fixed, workflow.snapshot().completionDraft());
        assertEquals(0, gateway.statusCalls);
        assertEquals(0, gateway.commentCalls);
    }

    @Test
    void fixedWithoutEditsSubmitsWithoutChangesetAssociation() throws IOException {
        controller.preserveFixedDraft(fixedDraft(""));

        controller.submitFixedWithoutUpload();

        assertEquals(1, gateway.statusCalls);
        assertEquals(0, gateway.changesetCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
    }

    @Test
    void correlatedFixedUploadAssociatesChangesetBeforeComment() throws IOException {
        controller.preserveFixedDraft(fixedDraft("uploaded"));
        workflow.waitForUpload(() -> { });

        controller.submitFixedAfterUpload(77);

        assertEquals(java.util.List.of("status", "changeset", "comment"), gateway.operations);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
    }

    @Test
    void changesetFailureRetriesOnlyRemainingAuxiliaryOperations() throws IOException {
        controller.preserveFixedDraft(fixedDraft("uploaded"));
        workflow.waitForUpload(() -> { });
        gateway.failChangeset = true;

        assertThrows(IOException.class, () -> controller.submitFixedAfterUpload(77));
        assertEquals(1, gateway.statusCalls);
        assertEquals(1, gateway.changesetCalls);
        assertEquals(0, gateway.commentCalls);
        assertTrueNoTaskLock();

        gateway.failChangeset = false;
        controller.retryAuxiliary();

        assertEquals(1, gateway.statusCalls);
        assertEquals(2, gateway.changesetCalls);
        assertEquals(1, gateway.commentCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
    }

    @Test
    void ambiguousFixedStatusResponseIsReconciledWithoutResubmission() throws IOException {
        controller.preserveFixedDraft(fixedDraft(""));
        workflow.waitForUpload(() -> { });
        gateway.failStatus = true;
        gateway.statusWasCommitted = true;

        controller.submitFixedAfterUpload(77);

        assertEquals(1, gateway.statusCalls);
        assertEquals(1, gateway.statusLookupCalls);
        assertEquals(1, gateway.changesetCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
    }

    @Test
    void fixedStatusFailureRetainsChangesetAndCanRetry() throws IOException {
        controller.preserveFixedDraft(fixedDraft(""));
        workflow.waitForUpload(() -> { });
        gateway.failStatus = true;

        assertThrows(IOException.class, () -> controller.submitFixedAfterUpload(77));
        assertEquals(77, workflow.snapshot().completionChangesetId());

        gateway.failStatus = false;
        controller.submitFixedWithoutUpload();

        assertEquals(2, gateway.statusCalls);
        assertEquals(1, gateway.changesetCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
    }

    @Test
    void retryDoesNotResubmitFixedUntilServerStatusCanBeReconciled() {
        controller.preserveFixedDraft(fixedDraft(""));
        workflow.waitForUpload(() -> { });
        gateway.failStatus = true;
        gateway.failStatusLookup = true;

        assertThrows(IOException.class, () -> controller.submitFixedAfterUpload(77));
        assertEquals(1, gateway.statusCalls);

        assertThrows(IOException.class, controller::submitFixedWithoutUpload);
        assertEquals(1, gateway.statusCalls);
        assertEquals(State.RECOVERABLE_ERROR, workflow.state());
    }

    @Test
    void successfulCompletionAutomaticallyReservesSelectedNearbyTaskWithoutDownloading() throws IOException {
        final var candidate = new Task(200, "next", null, null, 10, null, null, new DataSet(), null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
        nextResult = new TaskReservationService.Result(TaskReservationService.Status.RESERVED, candidate);
        final var nearbyDraft = new CompletionDraft(task, CompletionResult.CANT_COMPLETE, "", "", null, Map.of(),
                NextMode.NEARBY);

        final var states = new ArrayList<State>();
        final var listener = (java.beans.PropertyChangeListener) event -> states.add(workflow.state());
        workflow.addPropertyChangeListener(listener);
        try {
            controller.submit(nearbyDraft);
        } finally {
            workflow.removePropertyChangeListener(listener);
        }

        assertEquals(1, nextCalls);
        assertEquals(NextMode.NEARBY, nextDraft.nextMode());
        assertEquals(State.RESERVED_PREVIEW, workflow.state());
        assertSame(candidate, workflow.snapshot().reservedTask());
        assertEquals(100L, workflow.snapshot().completedTaskId());
        assertFalse(states.contains(State.STARTING_DOWNLOAD));
    }

    @Test
    void nextReservationFailureDoesNotMakeCommittedCompletionRetryable() throws IOException {
        nextFailure = new IOException("candidate failed");

        controller.submit(draft(""));

        assertEquals(1, gateway.statusCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
        assertEquals(TaskReservationService.Status.REQUEST_FAILED, workflow.snapshot().reservationStatus());
    }

    @Test
    void excludedNextCandidatesEndCompletionWithoutStatusRetry() throws IOException {
        nextResult = new TaskReservationService.Result(TaskReservationService.Status.EXCLUDED_RETRIES_EXHAUSTED,
                null);

        controller.submit(draft(""));

        assertEquals(1, gateway.statusCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
        assertEquals(TaskReservationService.Status.EXCLUDED_RETRIES_EXHAUSTED,
                workflow.snapshot().reservationStatus());
    }

    private void assertTrueNoTaskLock() {
        assertFalse(workflow.getLockedTasks().stream().anyMatch(locked -> locked.id() == task.id()));
    }

    private CompletionDraft draft(String comment) {
        return new CompletionDraft(task, CompletionResult.CANT_COMPLETE, comment, "tag", null, Map.of(),
                NextMode.RANDOM);
    }

    private CompletionDraft fixedDraft(String comment) {
        return new CompletionDraft(task, CompletionResult.FIXED, comment, "tag", null, Map.of(), NextMode.RANDOM);
    }

    private Task enterActiveWorkflow() {
        final var challenge = new Challenge(10, "challenge", null, null, null, false, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        final var activeTask = new Task(100, "task", null, null, 10, null, null, new DataSet(), null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
        workflow.connect();
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(activeTask);
        workflow.beginDownload(null);
        workflow.activateTask(activeTask, new OsmDataLayer(new DataSet(), "test", null));
        return activeTask;
    }

    private static final class FakeGateway implements TaskCompletionGateway {
        private int statusCalls;
        private int commentCalls;
        private int changesetCalls;
        private int statusLookupCalls;
        private boolean failStatus;
        private boolean failComment;
        private boolean failChangeset;
        private boolean statusWasCommitted;
        private boolean failStatusLookup;
        private final java.util.List<String> operations = new ArrayList<>();

        @Override
        public void updateStatus(CompletionDraft draft) throws IOException {
            statusCalls++;
            operations.add("status");
            assertEquals(State.SUBMITTING, WorkflowController.getInstance().state());
            assertNotNull(WorkflowController.getInstance().snapshot().completionDraft());
            if (failStatus) {
                throw new IOException("status failed");
            }
        }

        @Override
        public void addComment(CompletionAuxiliaryRetry comment) throws IOException {
            commentCalls++;
            operations.add("comment");
            assertEquals(State.SUBMITTING, WorkflowController.getInstance().state());
            assertNotNull(WorkflowController.getInstance().snapshot().auxiliaryRetry());
            if (failComment) {
                throw new IOException("comment failed");
            }
        }

        @Override
        public void associateChangeset(long taskId, int changesetId) throws IOException {
            changesetCalls++;
            operations.add("changeset");
            assertEquals(State.SUBMITTING, WorkflowController.getInstance().state());
            assertTrue(WorkflowController.getInstance().snapshot().auxiliaryRetry().changesetPending());
            if (failChangeset) {
                throw new IOException("changeset failed");
            }
        }

        @Override
        public boolean hasTaskStatus(long taskId, int status) throws IOException {
            statusLookupCalls++;
            if (failStatusLookup) {
                throw new IOException("status lookup failed");
            }
            return statusWasCommitted;
        }
    }
}
