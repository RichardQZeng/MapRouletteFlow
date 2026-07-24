// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Point;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import org.openstreetmap.josm.testutils.annotations.Projection;

@Projection
class TaskPreviewBoundsTest {
    @Test
    void usesFullGeometryInsteadOfLocation() {
        final var geometries = new DataSet();
        geometries.addPrimitive(new Node(new LatLon(10, 20)));
        geometries.addPrimitive(new Node(new LatLon(12, 24)));

        final var bounds = TaskPreviewBounds.forTask(task(geometries, new Point(50, 60))).orElseThrow();

        assertEquals(10, bounds.getMinLat());
        assertEquals(20, bounds.getMinLon());
        assertEquals(12, bounds.getMaxLat());
        assertEquals(24, bounds.getMaxLon());
    }

    @Test
    void fallsBackToLocationAndHandlesUnlocatableTask() {
        final var pointBounds = TaskPreviewBounds.forTask(task(new DataSet(), new Point(50, 60))).orElseThrow();
        assertEquals(50, pointBounds.getMinLat());
        assertEquals(60, pointBounds.getMinLon());
        assertTrue(pointBounds.isCollapsed());
        assertTrue(TaskPreviewBounds.forTask(task(new DataSet(), null)).isEmpty());
    }

    private static Task task(DataSet geometries, Point location) {
        return new Task(100, "task", null, null, 10, null, location, geometries, null, TaskStatus.CREATED, null,
                null, null, null, 0, null, null, null, false, null, "");
    }
}
