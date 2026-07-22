// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.stream.Stream;

import javax.swing.AbstractButton;
import javax.swing.JTable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.task.list.TaskListPanel;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;

@BasicPreferences
@Main
class TaskListPanelTest {
    private final WorkflowController workflow = WorkflowController.getInstance();

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void mainFlowHasSinglePreviewControlsAndNoBatchTable() {
        workflow.connect();
        final var panel = new TaskListPanel();
        try {
            final var buttonTexts = descendants(panel).filter(AbstractButton.class::isInstance)
                    .map(AbstractButton.class::cast).map(AbstractButton::getText).toList();
            assertTrue(buttonTexts.contains("Load Challenge"));
            assertTrue(buttonTexts.contains("Random"));
            assertTrue(buttonTexts.contains("Nearby"));
            assertTrue(buttonTexts.contains("Start & Download"));
            assertTrue(buttonTexts.contains("Retry"));
            assertTrue(buttonTexts.contains("Release"));
            assertFalse(buttonTexts.stream().anyMatch(text -> text != null && text.contains("10")));
            assertTrue(descendants(panel).noneMatch(JTable.class::isInstance));
        } finally {
            panel.destroy();
        }
    }

    @Test
    void controllerReservationIsTheOnlyPanelSelection() {
        workflow.connect();
        workflow.selectChallenge(new Challenge(10, "challenge", null, null, null, false, null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        final var task = new Task(100, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
        workflow.reserveCandidate(task);
        final var panel = new TaskListPanel();
        try {
            assertEquals(1, panel.getSelected().size());
            assertEquals(task, panel.getSelected().iterator().next());
        } finally {
            panel.destroy();
        }
    }

    private static Stream<Component> descendants(Container container) {
        return Arrays.stream(container.getComponents())
                .flatMap(component -> component instanceof Container child
                        ? Stream.concat(Stream.of(component), descendants(child))
                        : Stream.of(component));
    }
}
