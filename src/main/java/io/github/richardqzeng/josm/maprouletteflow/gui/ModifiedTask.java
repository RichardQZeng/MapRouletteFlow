// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui;

import java.util.Map;
import java.util.Objects;

import javax.swing.text.html.Option;

import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Information for a modified task
 *
 * @param task            The task to modify
 * @param status          The status to set
 * @param comment         The comment to use
 * @param tags            The tags to use
 * @param reviewRequested Force review or not
 * @param completionResponses Completion responses to send
 */
public record ModifiedTask(@Nonnull Task task, @Nonnull TaskStatus status, @Nullable String comment,
                           @Nullable String tags, @Nullable Boolean reviewRequested, @Nullable Map<String, Option> completionResponses) {
    /**
     * Validate the non-null fields
     *
     * @param task            The task to modify
     * @param status          The status to set
     * @param comment         The comment to use
     * @param tags            The tags to use
     * @param reviewRequested Force review or not
     */
    public ModifiedTask {
        Objects.requireNonNull(task);
        Objects.requireNonNull(status);
    }
}
