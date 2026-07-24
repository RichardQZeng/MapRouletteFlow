// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import java.io.IOException;

import io.github.richardqzeng.josm.maprouletteflow.api.TaskCompletionAPI;

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

    @Override
    public void associateChangeset(long taskId, int changesetId) throws IOException {
        TaskCompletionAPI.associateChangeset(taskId, changesetId);
    }

    @Override
    public boolean hasTaskStatus(long taskId, int status) throws IOException {
        return TaskCompletionAPI.hasTaskStatus(taskId, status);
    }
}
