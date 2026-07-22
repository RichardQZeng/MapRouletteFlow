// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.io.IOException;

import org.openstreetmap.josm.plugins.maproulette.api.TaskCompletionAPI;

/** Production completion gateway backed by the MapRoulette v2 API. */
public final class ApiTaskCompletionGateway implements TaskCompletionGateway {
    @Override
    public void updateStatus(CompletionDraft draft) throws IOException {
        TaskCompletionAPI.updateStatus(draft);
    }

    @Override
    public void addComment(CompletionAuxiliaryRetry comment) throws IOException {
        TaskCompletionAPI.addComment(comment);
    }
}
