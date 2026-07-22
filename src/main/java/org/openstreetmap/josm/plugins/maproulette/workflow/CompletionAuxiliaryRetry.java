// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.util.Objects;

/** Comment operation that remains after the task status has already committed. */
public record CompletionAuxiliaryRetry(long taskId, int actionId, String comment) {
    public CompletionAuxiliaryRetry {
        Objects.requireNonNull(comment);
    }
}
