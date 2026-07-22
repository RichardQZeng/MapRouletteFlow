// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.util.Objects;

/** Operations that remain after the task status has already committed. */
public record CompletionAuxiliaryRetry(long taskId, int actionId, String comment, Integer changesetId,
                                       boolean changesetPending, boolean commentPending) {
    public CompletionAuxiliaryRetry {
        Objects.requireNonNull(comment);
    }

    public CompletionAuxiliaryRetry(long taskId, int actionId, String comment) {
        this(taskId, actionId, comment, null, false, !comment.isBlank());
    }

    public CompletionAuxiliaryRetry changesetCompleted() {
        return new CompletionAuxiliaryRetry(taskId, actionId, comment, changesetId, false, commentPending);
    }

    public CompletionAuxiliaryRetry commentCompleted() {
        return new CompletionAuxiliaryRetry(taskId, actionId, comment, changesetId, changesetPending, false);
    }

    public boolean isComplete() {
        return !changesetPending && !commentPending;
    }
}
