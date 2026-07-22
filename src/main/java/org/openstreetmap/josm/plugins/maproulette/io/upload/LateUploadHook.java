// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.io.upload;

import java.util.Map;
import java.util.function.Consumer;

import org.openstreetmap.josm.actions.upload.UploadHook;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.tools.ListenerList;

/**
 * A late upload hook for uploading task status'
 */
public class LateUploadHook implements UploadHook {
    /**
     * A list of listeners for uploaded tasks
     */
    private static final ListenerList<Consumer<Map<Long, Task>>> MODIFIED_TASKS_UPLOADED = ListenerList.create();

    /**
     * Add an upload listener
     *
     * @param consumer The consumer will receive a sorted array of ids
     */
    public static void addUploadListener(Consumer<Map<Long, Task>> consumer) {
        MODIFIED_TASKS_UPLOADED.addListener(consumer);
    }

    /**
     * Remove an upload listener
     *
     * @param consumer The listener to remove
     */
    public static void removeUploadListener(Consumer<Map<Long, Task>> consumer) {
        MODIFIED_TASKS_UPLOADED.removeListener(consumer);
    }

    @Override
    public void modifyChangesetTags(Map<String, String> tags) {
        // Phase 6 will notify listeners only after a correlated successful upload.
    }
}
