// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.layer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Point;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class MapRouletteClusteredPointLayerTest {
    @Test
    void replacingPreviewRecalculatesBoundsAndKeepsExactlyOneTask() {
        final var layer = new MapRouletteClusteredPointLayer(null, List.of(task(1, 10, 20)));
        try {
            assertEquals(10, layer.getDataBounds().getMinLat());
            assertEquals(1, layer.getTasks().size());

            layer.replaceTasks(List.of(task(2, 30, 40)));

            assertEquals(1, layer.getTasks().size());
            assertEquals(2, layer.getTasks().iterator().next().id());
            assertEquals(30, layer.getDataBounds().getMinLat());
            assertEquals(40, layer.getDataBounds().getMinLon());

            layer.replaceTasks(List.of());
            assertEquals(0, layer.getTasks().size());
            assertNull(layer.getDataBounds());
        } finally {
            layer.destroy();
        }
    }

    @Test
    void acceptsGeometryOnlyPreview() {
        final var geometries = new DataSet();
        geometries.addPrimitive(new Node(new LatLon(5, 6)));
        final var task = new Task(3, "geometry", null, null, 10, null, null, geometries, null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
        final var layer = new MapRouletteClusteredPointLayer(null, List.of(task));
        try {
            assertEquals(5, layer.getDataBounds().getMinLat());
            assertEquals(6, layer.getDataBounds().getMinLon());
        } finally {
            layer.destroy();
        }
    }

    private static Task task(long id, double lat, double lon) {
        return new Task(id, "task", null, null, 10, null, new Point(lat, lon), new DataSet(), null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
    }
}
