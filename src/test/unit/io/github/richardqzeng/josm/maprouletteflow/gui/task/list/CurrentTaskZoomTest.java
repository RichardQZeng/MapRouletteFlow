// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Point;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Projection;

@BasicPreferences
@Projection
class CurrentTaskZoomTest {
    @Test
    void pointTaskUsesConfiguredRadiusInsteadOfPriorLayerScale() {
        final var bounds = CurrentTaskZoom.bounds(task(new Point(35, -100))).orElseThrow();

        assertFalse(bounds.isCollapsed());
        assertEquals(35, bounds.getCenter().lat(), 1e-6);
        assertEquals(-100, bounds.getCenter().lon(), 1e-6);
    }

    private static Task task(Point location) {
        return new Task(100, "task", null, null, 10, null, location, new DataSet(), null, TaskStatus.CREATED, null,
                null, null, 0, null, null, null, false, "");
    }
}
