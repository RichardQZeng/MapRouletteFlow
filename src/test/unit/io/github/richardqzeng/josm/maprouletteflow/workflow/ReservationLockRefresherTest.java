// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class ReservationLockRefresherTest {
    private final WorkflowController workflow = WorkflowController.getInstance();

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void releaseAndShutdownStopRefreshTimer() {
        reserveTask();
        final var released = ReservationLockRefresher.start(workflow, event -> { }, 60_000);
        assertTrue(released.isRunning());
        workflow.releaseReservation();
        assertFalse(released.isRunning());

        reserveTask();
        final var shutdown = ReservationLockRefresher.start(workflow, event -> { }, 60_000);
        workflow.shutdown();
        assertFalse(shutdown.isRunning());
    }

    private void reserveTask() {
        workflow.shutdown();
        workflow.connect();
        workflow.selectChallenge(new Challenge(10, "challenge", null, null, null, false, null, null, null, null,
                null, null, null, null, null, null, null));
        workflow.reserveCandidate(new Task(100, "task", null, null, 10, null, null, new DataSet(), null,
                TaskStatus.CREATED, null, null, null, 0, null, null, null, false, ""));
    }
}
