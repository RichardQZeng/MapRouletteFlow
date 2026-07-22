// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.io.upload;

import static org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl;

import java.util.Map;

import org.openstreetmap.josm.actions.upload.UploadHook;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.tools.Utils;

/** Adds metadata for an explicitly preserved Fixed draft without changing task status. */
public final class EarlyUploadHook implements UploadHook {
    @Override
    public void modifyChangesetTags(Map<String, String> tags) {
        final var snapshot = WorkflowController.getInstance().snapshot();
        final var draft = snapshot.completionDraft();
        if (draft == null || draft.result() != CompletionResult.FIXED) {
            return;
        }
        tags.put("maproulette:tasks", Long.toString(draft.task().id()));
        tags.put("maproulette:server", getBaseUrl());
        if (snapshot.activeChallenge() != null && snapshot.activeChallenge().general() != null) {
            merge(tags, "comment", snapshot.activeChallenge().general().checkinComment());
            merge(tags, "source", snapshot.activeChallenge().general().checkinSource());
        }
    }

    private static void merge(Map<String, String> tags, String key, String addition) {
        if (Utils.isStripEmpty(addition)) {
            return;
        }
        final var existing = tags.get(key);
        if (Utils.isStripEmpty(existing)) {
            tags.put(key, addition);
        } else if (!existing.contains(addition)) {
            tags.put(key, existing + "; " + addition);
        }
    }
}
