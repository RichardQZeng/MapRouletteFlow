// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.io.IOException;

import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;
import org.openstreetmap.josm.tools.Utils;

/** Coordinates workflow transitions around the status commit and auxiliary comment operation. */
public final class CompletionSubmissionController {
    private final WorkflowController workflow;
    private final TaskCompletionGateway gateway;

    public CompletionSubmissionController(WorkflowController workflow, TaskCompletionGateway gateway) {
        this.workflow = java.util.Objects.requireNonNull(workflow);
        this.gateway = java.util.Objects.requireNonNull(gateway);
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
        try {
            gateway.updateStatus(draft);
            final CompletionAuxiliaryRetry pendingComment = Utils.isStripEmpty(draft.comment()) ? null
                    : new CompletionAuxiliaryRetry(draft.task().id(), draft.result().actionId(), draft.comment());
            workflow.statusCommitted(pendingComment);
            if (pendingComment != null) {
                gateway.addComment(pendingComment);
            }
            workflow.submissionSucceeded();
        } catch (IOException | RuntimeException exception) {
            workflow.failRecoverably();
            throw exception;
        }
    }

    /** Retry the immutable post-status operation without reopening editable completion fields. */
    public void retryAuxiliary() throws IOException {
        final var retry = workflow.snapshot().auxiliaryRetry();
        if (workflow.state() != State.RECOVERABLE_ERROR || retry == null) {
            throw new IllegalStateException("No committed completion operation is waiting to be retried");
        }
        workflow.retry();
        try {
            gateway.addComment(retry);
            workflow.submissionSucceeded();
        } catch (IOException | RuntimeException exception) {
            workflow.failRecoverably();
            throw exception;
        }
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
