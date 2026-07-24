// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.io.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ChallengeGeneral;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.util.MapRouletteConfig;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController;

@MapRouletteConfig
class EarlyUploadHookTest {
    private final WorkflowController workflow = WorkflowController.getInstance();

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void fixedUploadMetadataMergesWithoutOverwritingUserValues() {
        enterActiveWorkflow();
        final var tags = new HashMap<>(
                Map.of("comment", "user comment", "source", "survey", "maproulette:tasks", "99"));

        EarlyUploadHook.applyMetadata(tags, 100, workflow.snapshot());

        assertEquals("user comment; challenge comment", tags.get("comment"));
        assertEquals("survey; challenge source", tags.get("source"));
        assertEquals("99;100", tags.get("maproulette:tasks"));
        assertTrue(tags.containsKey("maproulette:server"));
    }

    @Test
    void existingTaskMetadataIsNotDuplicated() {
        enterActiveWorkflow();
        final var tags = new HashMap<>(Map.of("maproulette:tasks", "99;100"));

        EarlyUploadHook.applyMetadata(tags, 100, workflow.snapshot());

        assertEquals("99;100", tags.get("maproulette:tasks"));
    }

    private void enterActiveWorkflow() {
        workflow.shutdown();
        final var general = new ChallengeGeneral(1, 1, "instructions", 1, "", true, false, 0, 0,
                "challenge comment", "challenge source", false, null, false);
        final var challenge = new Challenge(10, "challenge", null, null, null, false, null, general, null, null, null,
                null, null, null, null, null, null);
        final var task = new Task(100, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, 0, null, null, null, false, "");
        workflow.connect();
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(task);
        workflow.beginDownload(null);
        workflow.activateTask(task, new OsmDataLayer(new DataSet(), "test", null));
    }
}
