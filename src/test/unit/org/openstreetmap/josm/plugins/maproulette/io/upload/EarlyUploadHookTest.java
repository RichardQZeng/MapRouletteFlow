// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.io.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.ChallengeGeneral;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.plugins.maproulette.util.MapRouletteConfig;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionDraft;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;

@MapRouletteConfig
class EarlyUploadHookTest {
    private final WorkflowController workflow = WorkflowController.getInstance();

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void fixedDraftAddsMetadataWithoutOverwritingUserCommentOrSource() {
        enterActiveWorkflow();
        workflow.draftCompletion(new CompletionDraft(workflow.snapshot().activeTask(), CompletionResult.FIXED, "", "",
                null, Map.of(), NextMode.RANDOM));
        final var tags = new HashMap<>(Map.of("comment", "user comment", "source", "survey"));

        new EarlyUploadHook().modifyChangesetTags(tags);

        assertEquals("user comment; challenge comment", tags.get("comment"));
        assertEquals("survey; challenge source", tags.get("source"));
        assertEquals("100", tags.get("maproulette:tasks"));
        assertTrue(tags.containsKey("maproulette:server"));
    }

    @Test
    void nonFixedDraftCannotQueueUploadMetadataOrStatus() {
        enterActiveWorkflow();
        workflow.draftCompletion(new CompletionDraft(workflow.snapshot().activeTask(), CompletionResult.SKIP, "", "",
                null, Map.of(), NextMode.RANDOM));
        final var tags = new HashMap<String, String>();

        new EarlyUploadHook().modifyChangesetTags(tags);

        assertTrue(tags.isEmpty());
        assertFalse(workflow.getLockedTasks().isEmpty());
    }

    private void enterActiveWorkflow() {
        workflow.shutdown();
        final var general = new ChallengeGeneral(1, 1, "instructions", 1, "", true, false, 0, 0,
                "challenge comment", "challenge source", false, null, false);
        final var challenge = new Challenge(10, "challenge", null, null, null, false, null, general, null, null, null,
                null, null, null, null, null, null, null, null);
        final var task = new Task(100, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
        workflow.connect();
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(task);
        workflow.beginDownload(null);
        workflow.activateTask(task, new OsmDataLayer(new DataSet(), "test", null));
    }
}
