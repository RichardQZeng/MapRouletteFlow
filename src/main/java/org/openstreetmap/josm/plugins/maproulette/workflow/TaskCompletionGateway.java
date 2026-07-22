// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.io.IOException;

/** Network boundary for completion submission and deterministic controller tests. */
public interface TaskCompletionGateway {
    void updateStatus(CompletionDraft draft) throws IOException;

    void addComment(CompletionAuxiliaryRetry comment) throws IOException;

    void associateChangeset(long taskId, int changesetId) throws IOException;

    boolean hasTaskStatus(long taskId, int status) throws IOException;
}
