// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.current;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import javax.swing.Action;

import org.junit.jupiter.api.Test;
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
}
