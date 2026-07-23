// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class TaskEditTrackerTest {
    private final WorkflowController workflow = WorkflowController.getInstance();
    private final TaskEditTracker tracker = new TaskEditTracker(workflow);

    @AfterEach
    void tearDown() {
        tracker.shutdown();
        UndoRedoHandler.getInstance().clean();
        workflow.shutdown();
    }

    @Test
    void composesMergeAndTagChangesFromTaskPeriodCommands() {
        final var dataSet = new DataSet();
        final var reservoir = node(100, 35, -100, Map.of("name", "Tankersley Lake", "landuse", "reservoir"));
        final var area = area(dataSet, 200, Map.of("name", "Tankersley Lake", "landuse", "reservoir"));
        dataSet.addPrimitive(reservoir);
        final var context = activate(dataSet);
        final var changes = new HashMap<String, String>();
        changes.put("landuse", null);
        changes.put("natural", "water");
        changes.put("water", "reservoir");
        final var command = new SequenceCommand("Merge reservoir",
                new ChangePropertyCommand(dataSet, List.of(area), changes),
                new DeleteCommand(dataSet, reservoir));

        UndoRedoHandler.getInstance().add(command);
        final var comment = tracker.compose(context.task(), context.layer());

        assertTrue(comment.contains("Merged"));
        assertTrue(comment.contains("node 100"));
        assertTrue(comment.contains("Tankersley Lake"));
        assertTrue(comment.contains("replaced `landuse=reservoir` with `water=reservoir`"));
        assertTrue(comment.contains("set `natural=water`"));
    }

    @Test
    void composesAbsorbedReservoirNodeAsOneSemanticMerge() {
        final var dataSet = new DataSet();
        final var reservoir = node(357601420, 35, -100,
                Map.of("ele", "2790", "gnis:feature_id", "913069", "landuse", "reservoir", "name",
                        "Indian Joe Tank"));
        final var area = area(dataSet, 46185044, Map.of("name", "Indian Joe Tank", "natural", "water"));
        dataSet.addPrimitive(reservoir);
        final var context = activate(dataSet);
        final var removedNodeTags = new HashMap<String, String>();
        reservoir.getKeys().keySet().forEach(key -> removedNodeTags.put(key, null));
        final var areaTags = Map.of("ele", "2790", "water", "reservoir");
        final var areaNodes = new ArrayList<>(area.getNodes());
        areaNodes.add(areaNodes.size() - 1, reservoir);
        UndoRedoHandler.getInstance().add(new SequenceCommand("Merge reservoir node into area",
                new ChangePropertyCommand(dataSet, List.of(reservoir), removedNodeTags),
                new ChangePropertyCommand(dataSet, List.of(area), areaTags), new ChangeNodesCommand(area, areaNodes)));

        assertEquals(
                "Merged Indian Joe Tank (node 357,601,420) into Indian Joe Tank (area 46,185,044); "
                        + "replaced `landuse=reservoir` with `water=reservoir`; transferred `ele=2790`.",
                tracker.compose(context.task(), context.layer()));
    }

    @Test
    void undoneActionsAreNotIncludedAndRedoRestoresThem() {
        final var dataSet = new DataSet();
        final var reservoir = node(100, 35, -100, Map.of("name", "Tankersley Lake"));
        dataSet.addPrimitive(reservoir);
        final var context = activate(dataSet);
        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(reservoir, "natural", "water"));
        assertTrue(tracker.compose(context.task(), context.layer()).contains("natural=water"));

        UndoRedoHandler.getInstance().undo();
        assertEquals("", tracker.compose(context.task(), context.layer()));

        UndoRedoHandler.getInstance().redo();
        assertTrue(tracker.compose(context.task(), context.layer()).contains("natural=water"));
    }

    @Test
    void commandsThatPredateTaskActivationAreIgnored() {
        final var dataSet = new DataSet();
        final var node = node(100, 35, -100, Map.of("name", "Existing edit"));
        dataSet.addPrimitive(node);
        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(node, "source", "survey"));

        final var context = activate(dataSet);

        assertEquals("", tracker.compose(context.task(), context.layer()));
    }

    @Test
    void commandsInRedoStackAtActivationRemainOutsideTaskSummary() {
        final var dataSet = new DataSet();
        final var node = node(100, 35, -100, Map.of("name", "Existing edit"));
        dataSet.addPrimitive(node);
        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(node, "source", "survey"));
        UndoRedoHandler.getInstance().undo();
        final var context = activate(dataSet);

        UndoRedoHandler.getInstance().redo();

        assertEquals("", tracker.compose(context.task(), context.layer()));
    }

    @Test
    void unrelatedLayerCommandsAreExcludedWhenTaskHasPrimitiveSeeds() {
        final var dataSet = new DataSet();
        final var taskNode = node(100, 35, -100, Map.of("name", "Task feature"));
        final var unrelated = node(200, 36, -101, Map.of("name", "Unrelated feature"));
        dataSet.addPrimitive(taskNode);
        dataSet.addPrimitive(unrelated);
        final var taskGeometries = new DataSet();
        taskGeometries.addPrimitive(node(100, 35, -100, Map.of()));
        final var context = activate(dataSet, taskGeometries);

        UndoRedoHandler.getInstance().add(new ChangePropertyCommand(unrelated, "source", "survey"));

        assertEquals("", tracker.compose(context.task(), context.layer()));
    }

    @Test
    void unrelatedDeleteAndUpdateInOneSequenceAreNotCalledAMerge() {
        final var dataSet = new DataSet();
        final var removed = node(100, 35, -100, Map.of("name", "First feature"));
        final var survivor = node(200, 36, -101, Map.of("name", "Different feature"));
        dataSet.addPrimitive(removed);
        dataSet.addPrimitive(survivor);
        final var context = activate(dataSet);
        UndoRedoHandler.getInstance().add(new SequenceCommand("Independent edits",
                new ChangePropertyCommand(survivor, "source", "survey"), new DeleteCommand(dataSet, removed)));

        final var comment = tracker.compose(context.task(), context.layer());

        assertTrue(comment.contains("Deleted First feature"));
        assertTrue(!comment.contains("Merged"));
    }

    private Context activate(DataSet dataSet) {
        return activate(dataSet, new DataSet());
    }

    private Context activate(DataSet dataSet, DataSet taskGeometries) {
        tracker.start();
        final var challenge = new Challenge(10, "challenge", null, null, null, false, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        final var task = new Task(1000, "task", null, null, challenge.id(), null, null, taskGeometries, null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
        final var layer = new OsmDataLayer(dataSet, "task edits", null);
        workflow.connect();
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(task);
        workflow.beginDownload(null);
        workflow.activateTask(task, layer);
        return new Context(task, layer);
    }

    private static Node node(long id, double lat, double lon, Map<String, String> tags) {
        final var node = new Node(id, 1);
        node.setCoor(new LatLon(lat, lon));
        node.setKeys(tags);
        return node;
    }

    private static Way area(DataSet dataSet, long id, Map<String, String> tags) {
        final var first = node(id * 10, 35, -100, Map.of());
        final var second = node(id * 10 + 1, 35, -99.99, Map.of());
        final var third = node(id * 10 + 2, 35.01, -99.99, Map.of());
        dataSet.addPrimitive(first);
        dataSet.addPrimitive(second);
        dataSet.addPrimitive(third);
        final var way = new Way(id, 1);
        way.setNodes(List.of(first, second, third, first));
        way.setKeys(tags);
        dataSet.addPrimitive(way);
        return way;
    }

    private record Context(Task task, OsmDataLayer layer) {
    }
}
