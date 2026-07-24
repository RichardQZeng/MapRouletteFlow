// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task.current;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.List;

import javax.swing.Action;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;

@BasicPreferences
@Main
class CurrentTaskPanelTest {
    @Test
    void exposesAllFiveWebLabelsAsVisibleActions() {
        final var panel = new CurrentTaskPanel();
        try {
            assertEquals(List.of("I fixed it!", "Already fixed", "Not an Issue", "Can't Complete", "Skip"),
                    Arrays.stream(panel.actions()).limit(5).map(action -> action.getValue(Action.NAME)).toList());
        } finally {
            panel.destroy();
        }
    }

    @Test
    void sameTaskRefreshKeepsInstructionDocumentState() {
        final var panel = new CurrentTaskPanel();
        final var task = new Task(100, "task", null, null, 10, "instructions", null, new DataSet(), null,
                TaskStatus.CREATED, null, null, null, 0, null, null, null, false, "");
        try {
            panel.refreshModel(task);
            final var document = panel.instructionDocument();

            panel.refreshModel(task);

            assertSame(document, panel.instructionDocument());
        } finally {
            panel.destroy();
        }
    }
}
