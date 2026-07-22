// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.io.IOException;

import org.openstreetmap.josm.plugins.maproulette.data.IgnoreList;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/** Coordinates workflow transitions around the status commit and auxiliary comment operation. */
public final class CompletionSubmissionController {
    private final WorkflowController workflow;
    private final TaskCompletionGateway gateway;
    private final NextTaskReservation nextTaskReservation;

    @FunctionalInterface
    public interface NextTaskReservation {
        TaskReservationService.Result reserve(CompletionDraft draft) throws IOException;
    }

    public CompletionSubmissionController(WorkflowController workflow, TaskCompletionGateway gateway) {
        this(workflow, gateway, draft -> new TaskReservationService(workflow).reserveAfterCompletion(
                draft.task().parentId(), draft.nextMode(), draft.task().id(), IgnoreList::isTaskIgnored));
    }

    public CompletionSubmissionController(WorkflowController workflow, TaskCompletionGateway gateway,
            NextTaskReservation nextTaskReservation) {
        this.workflow = java.util.Objects.requireNonNull(workflow);
        this.gateway = java.util.Objects.requireNonNull(gateway);
        this.nextTaskReservation = java.util.Objects.requireNonNull(nextTaskReservation);
    }

    /** Closing confirmation before submission intentionally has no side effects. */
    public void cancel() {
        // The dialog owns only local widget state until Submit.
    }

    /** Preserve a valid Fixed draft for Phase 6 without submitting it. */
    public void preserveFixedDraft(CompletionDraft draft) {
        if (draft.result() != CompletionResult.FIXED) {
            throw new IllegalArgumentException("Only Fixed completion is upload-gated");
        }
        storeDraft(draft);
    }

    /** Submit a non-Fixed draft or retry only its post-commit auxiliary operation. */
    public void submit(CompletionDraft draft) throws IOException {
        if (draft.result() == CompletionResult.FIXED) {
            throw new IllegalArgumentException("Fixed completion requires Phase 6 upload integration");
        }
        if (workflow.snapshot().auxiliaryRetry() != null) {
            throw new IllegalStateException("Committed completion details cannot be changed during auxiliary retry");
        }
        prepareSubmission(draft);
        submitPrepared(draft, null, false);
    }

    /** Submit a stored Fixed draft after a correlated successful upload. */
    public void submitFixedAfterUpload(int changesetId) throws IOException {
        final var draft = requireFixedDraft(State.WAITING_FOR_UPLOAD);
        workflow.setCompletionChangesetId(changesetId);
        workflow.beginSubmission();
        submitPrepared(draft, changesetId, false);
    }

    /** Explicitly submit Fixed when the captured layer has no edits to upload. */
    public void submitFixedWithoutUpload() throws IOException {
        final var snapshot = workflow.snapshot();
        if (snapshot.state() == State.RECOVERABLE_ERROR && snapshot.completionDraft() != null
                && snapshot.completionDraft().result() == CompletionResult.FIXED
                && snapshot.auxiliaryRetry() == null) {
            workflow.retry();
            submitPrepared(snapshot.completionDraft(), snapshot.completionChangesetId(), true);
        } else {
            final var draft = requireFixedDraft(State.COMPLETION_DRAFT);
            workflow.setCompletionChangesetId(null);
            workflow.beginSubmission();
            submitPrepared(draft, null, false);
        }
    }

    private void submitPrepared(CompletionDraft draft, Integer changesetId, boolean reconcileBeforeUpdate)
            throws IOException {
        try {
            var statusCommitted = false;
            if (reconcileBeforeUpdate) {
                statusCommitted = gateway.hasTaskStatus(draft.task().id(), draft.result().actionId());
            }
            if (!statusCommitted) {
                try {
                    gateway.updateStatus(draft);
                } catch (IOException exception) {
                    if (draft.result() != CompletionResult.FIXED
                            || !gateway.hasTaskStatus(draft.task().id(), draft.result().actionId())) {
                        throw exception;
                    }
                }
            }
            final var pending = new CompletionAuxiliaryRetry(draft.task().id(), draft.result().actionId(),
                    draft.comment(), changesetId, changesetId != null, !Utils.isStripEmpty(draft.comment()));
            workflow.statusCommitted(pending.isComplete() ? null : pending);
            if (!pending.isComplete()) {
                completeAuxiliary(pending);
            }
        } catch (IOException | RuntimeException exception) {
            workflow.failRecoverably();
            throw exception;
        }
        reserveNext(draft);
    }

    /** Retry the immutable post-status operation without reopening editable completion fields. */
    public void retryAuxiliary() throws IOException {
        final var snapshot = workflow.snapshot();
        final var retry = snapshot.auxiliaryRetry();
        final var draft = snapshot.completionDraft();
        if (workflow.state() != State.RECOVERABLE_ERROR || retry == null) {
            throw new IllegalStateException("No committed completion operation is waiting to be retried");
        }
        workflow.retry();
        try {
            completeAuxiliary(retry);
        } catch (IOException | RuntimeException exception) {
            workflow.failRecoverably();
            throw exception;
        }
        reserveNext(draft);
    }

    private void reserveNext(CompletionDraft draft) {
        try {
            final var result = nextTaskReservation.reserve(draft);
            if (result.status() != TaskReservationService.Status.RESERVED) {
                workflow.submissionSucceeded(result.status());
            }
        } catch (IOException | RuntimeException exception) {
            Logging.warn("Could not reserve the next MapRoulette task: {0}", exception.getMessage());
            workflow.submissionSucceeded(TaskReservationService.Status.REQUEST_FAILED);
        }
    }

    private void completeAuxiliary(CompletionAuxiliaryRetry initial) throws IOException {
        var pending = initial;
        if (pending.changesetPending()) {
            gateway.associateChangeset(pending.taskId(), pending.changesetId());
            pending = pending.changesetCompleted();
            workflow.updateAuxiliaryRetry(pending.isComplete() ? null : pending);
        }
        if (pending.commentPending()) {
            gateway.addComment(pending);
            pending = pending.commentCompleted();
            workflow.updateAuxiliaryRetry(pending.isComplete() ? null : pending);
        }
    }

    private CompletionDraft requireFixedDraft(State expectedState) {
        final var snapshot = workflow.snapshot();
        if (snapshot.state() != expectedState || snapshot.completionDraft() == null
                || snapshot.completionDraft().result() != CompletionResult.FIXED) {
            throw new IllegalStateException("A Fixed completion draft is not ready for submission");
        }
        return snapshot.completionDraft();
    }

    private void prepareSubmission(CompletionDraft draft) {
        final var state = workflow.state();
        if (state == State.ACTIVE_EDITING) {
            workflow.draftCompletion(draft);
            workflow.beginSubmission();
        } else if (state == State.COMPLETION_DRAFT) {
            workflow.updateCompletionDraft(draft);
            workflow.beginSubmission();
        } else if (state == State.RECOVERABLE_ERROR) {
            workflow.updateCompletionDraft(draft);
            workflow.retry();
        } else {
            throw new IllegalStateException("Completion cannot be submitted from " + state);
        }
    }

    private void storeDraft(CompletionDraft draft) {
        if (workflow.state() == State.ACTIVE_EDITING) {
            workflow.draftCompletion(draft);
        } else {
            workflow.updateCompletionDraft(draft);
        }
    }
}
