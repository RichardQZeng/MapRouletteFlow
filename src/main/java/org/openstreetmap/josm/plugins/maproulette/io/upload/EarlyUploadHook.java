// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.io.upload;

import static org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl;

import java.util.Map;

import org.openstreetmap.josm.actions.upload.UploadHook;
import org.openstreetmap.josm.data.APIDataSet;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.tools.Utils;

/** Merges metadata into the exact upload dialog opened for a Fixed draft. */
public final class EarlyUploadHook implements UploadHook {
    private final FixedUploadCoordinator uploads;

    public EarlyUploadHook() {
        this(FixedUploadCoordinator.getInstance());
    }

    EarlyUploadHook(FixedUploadCoordinator uploads) {
        this.uploads = uploads;
    }

    @Override
    public boolean checkUpload(APIDataSet dataSet) {
        return uploads.checkUpload(dataSet);
    }

    @Override
    public void modifyChangesetTags(Map<String, String> tags) {
        final var taskId = uploads.consumeMetadataTaskId();
        if (taskId != null) {
            applyMetadata(tags, taskId, WorkflowController.getInstance().snapshot());
        }
    }

    static void applyMetadata(Map<String, String> tags, long taskId, WorkflowController.Snapshot snapshot) {
        appendTask(tags, taskId);
        tags.putIfAbsent("maproulette:server", getBaseUrl());
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

    private static void appendTask(Map<String, String> tags, long taskId) {
        final var value = Long.toString(taskId);
        for (var index = 1; ; index++) {
            final var key = index == 1 ? "maproulette:tasks" : "maproulette:tasks:" + index;
            final var existing = tags.get(key);
            if (Utils.isStripEmpty(existing)) {
                tags.put(key, value);
                return;
            }
            if (java.util.Arrays.stream(existing.split(";")).map(String::strip).anyMatch(value::equals)) {
                return;
            }
            if (existing.length() + value.length() + 1 <= 255) {
                tags.put(key, existing + ";" + value);
                return;
            }
        }
    }
}
