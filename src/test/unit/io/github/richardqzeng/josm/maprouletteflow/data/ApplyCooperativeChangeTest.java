// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ElementCreate;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ElementTagChange;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ElementUpdate;
import io.github.richardqzeng.josm.maprouletteflow.api.model.OSMChange;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class ApplyCooperativeChangeTest {
    @Test
    void keepSelectionControlsApplyUndoAndRedo() {
        final var dataSet = new DataSet();
        final var node = new Node(new LatLon(0, 0));
        node.setOsmId(10, 1);
        node.put("remove", "old");
        dataSet.addPrimitive(node);
        node.setModified(false);
        final var update = new ElementUpdate(10, OsmPrimitiveType.NODE, -1,
                new ElementTagChange(Map.of("keep", "new", "omit", "ignored"), new String[] { "remove" }));
        final var command = new ApplyCooperativeChange(new OSMChange(new ElementCreate[0],
                new ElementUpdate[] { update })).generateCommand(dataSet, (candidate, key) -> !"omit".equals(key));

        command.executeCommand();
        assertEquals("new", node.get("keep"));
        assertNull(node.get("omit"));
        assertNull(node.get("remove"));

        command.undoCommand();
        assertNull(node.get("keep"));
        assertEquals("old", node.get("remove"));
        command.executeCommand();
        assertEquals("new", node.get("keep"));
    }

    @Test
    void missingPrimitiveAndUnsupportedCreateReturnNoCommand() {
        final var missing = new ElementUpdate(10, OsmPrimitiveType.NODE, -1,
                new ElementTagChange(Map.of("key", "value"), new String[0]));
        assertNull(new ApplyCooperativeChange(new OSMChange(new ElementCreate[0], new ElementUpdate[] { missing }))
                .generateCommand(new DataSet()));

        final var create = new ElementCreate(1, OsmPrimitiveType.NODE, Map.of(), Map.of());
        assertNull(new ApplyCooperativeChange(new OSMChange(new ElementCreate[] { create }, new ElementUpdate[0]))
                .generateCommand(new DataSet()));
    }

    @Test
    void omittingEveryTagReturnsNoCommand() {
        final var dataSet = new DataSet();
        final var node = new Node(new LatLon(0, 0));
        node.setOsmId(10, 1);
        dataSet.addPrimitive(node);
        final var update = new ElementUpdate(10, OsmPrimitiveType.NODE, -1,
                new ElementTagChange(Map.of("key", "value"), new String[0]));

        assertNull(new ApplyCooperativeChange(new OSMChange(new ElementCreate[0], new ElementUpdate[] { update }))
                .generateCommand(dataSet, (candidate, key) -> false));
    }
}
