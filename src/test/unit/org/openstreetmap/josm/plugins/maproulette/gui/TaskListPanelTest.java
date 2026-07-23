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
import javax.swing.JLabel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.task.list.TaskListPanel;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionDraft;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;
import org.openstreetmap.josm.plugins.maproulette.workflow.TaskReservationService;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
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
            assertTrue(buttonTexts.contains("Instructions..."));
            assertTrue(buttonTexts.contains("I fixed it!"));
            assertTrue(buttonTexts.contains("Already fixed"));
            assertTrue(buttonTexts.contains("Not an Issue"));
            assertTrue(buttonTexts.contains("Can't Complete"));
            assertTrue(buttonTexts.contains("Skip"));
            assertTrue(buttonTexts.contains("Select Primitives"));
            assertFalse(buttonTexts.stream().anyMatch(text -> text != null && text.contains("10")));
            assertTrue(descendants(panel).noneMatch(JTable.class::isInstance));
        } finally {
            panel.destroy();
        }
    }

    @Test
    void currentTaskActionsAppearInMainPanelAfterActivation() {
        workflow.connect();
        workflow.selectChallenge(new Challenge(10, "challenge", null, null, null, false, null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        final var task = task(100);
        workflow.reserveCandidate(task);
        workflow.beginDownload(null);
        workflow.activateTask(task, new OsmDataLayer(new DataSet(), "test", null));

        final var panel = new TaskListPanel();
        try {
            final var instructions = descendants(panel).filter(AbstractButton.class::isInstance)
                    .map(AbstractButton.class::cast).filter(button -> "Instructions...".equals(button.getText()))
                    .findFirst().orElseThrow();
            assertTrue(isVisibleWithin(instructions, panel));
        } finally {
            panel.destroy();
        }
    }

    @Test
    void controllerReservationIsTheOnlyPanelSelection() {
        workflow.connect();
        workflow.selectChallenge(new Challenge(10, "challenge", null, null, null, false, null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        final var task = new Task(100, "task", null, null, 10, "instructions", null, new DataSet(), null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
        workflow.reserveCandidate(task);
        final var panel = new TaskListPanel();
        try {
            assertEquals(1, panel.getSelected().size());
            assertEquals(task, panel.getSelected().iterator().next());
        } finally {
            panel.destroy();
        }
    }

    @Test
    void automaticReservationUpdatesPanelWithoutStartingDownload() {
        final var oldTask = enterSubmittingTask();
        final var nextTask = task(200);
        final var panel = new TaskListPanel();
        try {
            workflow.submissionSucceeded(nextTask);

            assertEquals(nextTask, panel.getSelected().iterator().next());
            assertEquals(WorkflowController.State.RESERVED_PREVIEW, workflow.state());
            assertEquals(oldTask.id(), workflow.snapshot().completedTaskId());
            assertTrue(descendants(panel).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .map(JLabel::getText).anyMatch(text -> text != null && text.contains("No OSM data")));
            final var instructions = descendants(panel).filter(AbstractButton.class::isInstance)
                    .map(AbstractButton.class::cast).filter(button -> "Instructions...".equals(button.getText()))
                    .findFirst().orElseThrow();
            assertFalse(isVisibleWithin(instructions, panel));
        } finally {
            panel.destroy();
        }
    }

    @Test
    void emptyAutomaticReservationLeavesChallengeVisibleWithTerminalMessage() {
        enterSubmittingTask();
        final var panel = new TaskListPanel();
        try {
            workflow.submissionSucceeded(TaskReservationService.Status.EMPTY);

            assertEquals(WorkflowController.State.CHALLENGE_IDLE, workflow.state());
            assertEquals(10, workflow.snapshot().activeChallenge().id());
            assertTrue(descendants(panel).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .map(JLabel::getText).anyMatch(text -> text != null && text.contains("No more tasks")));
        } finally {
            panel.destroy();
        }
    }

    private Task enterSubmittingTask() {
        workflow.connect();
        workflow.selectChallenge(new Challenge(10, "challenge", null, null, null, false, null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        final var task = task(100);
        workflow.reserveCandidate(task);
        workflow.beginDownload(null);
        workflow.activateTask(task, new OsmDataLayer(new DataSet(), "test", null));
        workflow.draftCompletion(new CompletionDraft(task, CompletionResult.CANT_COMPLETE, "", "", null,
                java.util.Map.of(), NextMode.RANDOM));
        workflow.beginSubmission();
        workflow.statusCommitted(null);
        return task;
    }

    private static Task task(long id) {
        return new Task(id, "task", null, null, 10, "instructions", null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
    }

    private static Stream<Component> descendants(Container container) {
        return Arrays.stream(container.getComponents())
                .flatMap(component -> component instanceof Container child
                        ? Stream.concat(Stream.of(component), descendants(child))
                        : Stream.of(component));
    }

    private static boolean isVisibleWithin(Component component, Container root) {
        for (var current = component; current != root; current = current.getParent()) {
            if (current == null || !current.isVisible()) {
                return false;
            }
        }
        return true;
    }
}
