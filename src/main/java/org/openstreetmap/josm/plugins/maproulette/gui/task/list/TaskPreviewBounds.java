// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.list;

import java.util.Collection;
import java.util.Optional;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.api.model.TaskClusteredPoint;

/** Computes preview bounds from full task geometry with a location fallback. */
public final class TaskPreviewBounds {
    private TaskPreviewBounds() {
        // Utility class.
    }

    public static Optional<Bounds> forTask(Task task) {
        Bounds bounds = null;
        for (var primitive : task.geometries().allNonDeletedPrimitives()) {
            bounds = extend(bounds, primitive.getBBox());
        }
        if (bounds == null && task.location() != null) {
            bounds = new Bounds(task.location().lat(), task.location().lon(), task.location().lat(),
                    task.location().lon());
        }
        return Optional.ofNullable(bounds);
    }

    public static Optional<Bounds> forTasks(Collection<? extends TaskClusteredPoint> tasks) {
        Bounds bounds = null;
        for (var point : tasks) {
            if (point instanceof Task task) {
                final var taskBounds = forTask(task);
                if (taskBounds.isPresent()) {
                    bounds = extend(bounds, taskBounds.get().toBBox());
                }
            } else if (point.location() != null) {
                bounds = extend(bounds, point.location().getBBox());
            }
        }
        return Optional.ofNullable(bounds);
    }

    private static Bounds extend(Bounds bounds, BBox box) {
        if (box == null || !box.isValid()) {
            return bounds;
        }
        if (bounds == null) {
            bounds = new Bounds(box.getBottomRight());
        }
        bounds.extend(box.getTopLeft());
        bounds.extend(box.getBottomRight());
        return bounds;
    }
}
