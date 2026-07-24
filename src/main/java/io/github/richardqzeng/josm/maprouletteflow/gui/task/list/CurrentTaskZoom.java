// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task.list;

import java.util.Optional;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MainApplication;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference;
import io.github.richardqzeng.josm.maprouletteflow.workflow.TaskDownloadBounds;

/** Zooms from current-task bounds only, independent of accumulated layer data. */
final class CurrentTaskZoom {
    private CurrentTaskZoom() {
        // Utility class.
    }

    static Optional<Bounds> bounds(Task task) {
        return TaskDownloadBounds.forTask(task, MapRouletteTaskPreference.getGeometryPadding(),
                MapRouletteTaskPreference.getPointRadius());
    }

    static void zoom(Task task) {
        if (MapRouletteTaskPreference.isAutoCenter() && MainApplication.getMap() != null) {
            bounds(task).ifPresent(MainApplication.getMap().mapView::zoomTo);
        }
    }
}
