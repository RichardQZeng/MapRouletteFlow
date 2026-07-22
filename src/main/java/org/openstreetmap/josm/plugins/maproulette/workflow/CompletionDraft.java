// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;

import jakarta.annotation.Nullable;

/** Credentials-free task completion data shared by non-Fixed and Fixed workflows. */
public record CompletionDraft(Task task, CompletionResult result, String comment, String tags,
                              @Nullable Boolean requestReview, Map<String, Object> completionResponses,
                              NextMode nextMode) {
    public static final int MAX_COMMENT_LENGTH = 5_000;

    public CompletionDraft {
        Objects.requireNonNull(task);
        Objects.requireNonNull(result);
        Objects.requireNonNull(nextMode);
        comment = comment == null ? "" : comment;
        tags = tags == null ? "" : tags;
        completionResponses = completionResponses == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new TreeMap<>(completionResponses));
    }
}
