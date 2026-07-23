// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.maproulette.api.TaskAPI;
import org.openstreetmap.josm.plugins.maproulette.util.MapRouletteConfig;

/**
 * Test class for {@link TaskPrimitives}
 */
@MapRouletteConfig
class TaskPrimitivesTest {
    @Test
    void testGetPrimitiveIdsNode() {
        final var task = assertDoesNotThrow(() -> TaskAPI.start(132279499));
        assertFalse(TaskPrimitives.getPrimitiveIds(task).isEmpty());
        assertEquals(1, TaskPrimitives.getPrimitiveIds(task).size());
        final var nodeId = TaskPrimitives.getPrimitiveIds(task).iterator().next();
        final var expectedId = new SimplePrimitiveId(9494185766L, OsmPrimitiveType.NODE);
        assertEquals(expectedId, nodeId);
        assertEquals(java.util.Set.of(expectedId),
                java.util.Set.copyOf(TaskPrimitives.getPrimitiveIds(task, "osmIdentifier")));
    }

    @Test
    void testGetPrimitiveIdsNull() {
        assertTrue(TaskPrimitives.getPrimitiveIds(null).isEmpty());
    }

    @Test
    void testGetPrimitiveIdsNoIdentifier() {
        final var task = assertDoesNotThrow(() -> TaskAPI.start(132279499));
        task.geometries().allPrimitives().forEach(p -> p.remove("osmIdentifier"));
        assertTrue(TaskPrimitives.getPrimitiveIds(task).isEmpty());
    }

    @Test
    void formattedPrimitiveIdsReturnParsedNodeWayAndRelation() {
        assertEquals(new SimplePrimitiveId(11, OsmPrimitiveType.NODE),
                TaskPrimitives.getPrimitiveId(new Node(), "node/11"));
        assertEquals(new SimplePrimitiveId(22, OsmPrimitiveType.WAY),
                TaskPrimitives.getPrimitiveId(new Way(), "way/22"));
        assertEquals(new SimplePrimitiveId(33, OsmPrimitiveType.RELATION),
                TaskPrimitives.getPrimitiveId(new Relation(), "relation/33"));
    }

    @Test
    void findsPresentReferencedPrimitivesAndIgnoresMissingOnes() {
        final var dataSet = new DataSet();
        final var node = new Node(11);
        dataSet.addPrimitive(node);

        final var found = TaskPrimitives.findPrimitives(dataSet, java.util.List.of(
                new SimplePrimitiveId(11, OsmPrimitiveType.NODE),
                new SimplePrimitiveId(99, OsmPrimitiveType.WAY)));

        assertEquals(java.util.List.of(node), found);
    }
}
