// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Point;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import org.openstreetmap.josm.testutils.annotations.Projection;

@Projection
class TaskDownloadBoundsTest {
    @Test
    void padsFullGeometryByConfiguredPercent() {
        final var geometries = wayGeometry(10, 20, 12, 24);

        final var bounds = TaskDownloadBounds.forTask(task(geometries, new Point(50, 60)), 10, 100)
                .orElseThrow();

        assertEquals(9.8, bounds.getMinLat(), 1e-9);
        assertEquals(19.6, bounds.getMinLon(), 1e-9);
        assertEquals(12.2, bounds.getMaxLat(), 1e-9);
        assertEquals(24.4, bounds.getMaxLon(), 1e-9);
    }

    @Test
    void usesGeometryWithoutLocation() {
        final var bounds = TaskDownloadBounds.forTask(task(wayGeometry(10, 20, 12, 24), null), 0, 100)
                .orElseThrow();

        assertEquals(10, bounds.getMinLat());
        assertEquals(20, bounds.getMinLon());
        assertEquals(12, bounds.getMaxLat());
        assertEquals(24, bounds.getMaxLon());
    }

    @Test
    void pointOnlyGeometryUsesMeterRadiusAroundTaskLocation() {
        final var geometries = new DataSet();
        geometries.addPrimitive(new Node(new LatLon(1, 2)));

        final var bounds = TaskDownloadBounds.forTask(task(geometries, new Point(50, 60)), 10, 100)
                .orElseThrow();
        final var expectedLatitudeDelta = Math.toDegrees(100 / TaskDownloadBounds.EARTH_RADIUS_METERS);

        assertEquals(50, bounds.getCenter().lat(), 1e-9);
        assertEquals(60, bounds.getCenter().lon(), 1e-9);
        assertEquals(expectedLatitudeDelta, 50 - bounds.getMinLat(), 5e-8);
        assertTrue(bounds.getWidth() > bounds.getHeight());
    }

    @Test
    void emptyOrNullGeometryRequiresLocation() {
        assertTrue(TaskDownloadBounds.forTask(task(new DataSet(), null), 10, 100).isEmpty());
        assertTrue(TaskDownloadBounds.forTask(task(null, null), 10, 100).isEmpty());
        assertTrue(TaskDownloadBounds.forTask(null, 10, 100).isEmpty());

        final var bounds = TaskDownloadBounds.forTask(task(null, new Point(10, 20)), 10, 100).orElseThrow();
        assertEquals(10, bounds.getCenter().lat(), 1e-9);
        assertEquals(20, bounds.getCenter().lon(), 1e-9);
    }

    private static DataSet wayGeometry(double minLat, double minLon, double maxLat, double maxLon) {
        final var way = new Way();
        way.setNodes(List.of(new Node(new LatLon(minLat, minLon)), new Node(new LatLon(maxLat, maxLon))));
        final var dataSet = new DataSet();
        dataSet.addPrimitiveRecursive(way);
        return dataSet;
    }

    private static Task task(DataSet geometries, Point location) {
        return new Task(100, "task", null, null, 10, null, location, geometries, null, TaskStatus.CREATED, null,
                null, null, 0, null, null, null, false, "");
    }
}
