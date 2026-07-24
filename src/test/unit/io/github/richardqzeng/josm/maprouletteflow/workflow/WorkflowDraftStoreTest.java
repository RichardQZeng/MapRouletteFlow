// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class WorkflowDraftStoreTest {
    private final WorkflowController workflow = WorkflowController.getInstance();
    private Challenge challenge;
    private Task task;
    private OsmDataLayer layer;

    @BeforeEach
    void setUp() {
        workflow.shutdown();
        WorkflowDraftStore.clear();
        challenge = new Challenge(10, "challenge", null, null, null, false, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        task = new Task(100, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED, null, null,
                null, null, 0, null, null, null, false, null, "");
        layer = new OsmDataLayer(new DataSet(), "test", null);
    }

    @AfterEach
    void tearDown() {
        workflow.shutdown();
        WorkflowDraftStore.clear();
    }

    @Test
    void roundTripPersistsDraftWithoutCredentialMaterialAndRestoresIt() {
        enterDraft();
        final var stored = WorkflowDraftStore.load();

        assertNotNull(stored);
        assertEquals("comment", stored.comment());
        assertEquals(Map.of("confirmed", true), stored.completionResponses());
        assertFalse(WorkflowDraftStore.serializedValue().contains("42|secret-api-key"));

        workflow.shutdown();
        workflow.restoreDraft(stored, challenge, task, layer);
        assertEquals(WorkflowController.State.COMPLETION_DRAFT, workflow.state());
        assertEquals(CompletionResult.FIXED, workflow.snapshot().completionDraft().result());
    }

    @Test
    void statusCommittedAuxiliaryRetrySurvivesShutdownWithoutAnotherStatusMarker() {
        enterDraft();
        workflow.beginSubmission();
        workflow.statusCommitted(new CompletionAuxiliaryRetry(100, CompletionResult.FIXED.actionId(), "comment", 77,
                true, true));
        workflow.failRecoverably();

        workflow.shutdown();
        final var stored = WorkflowDraftStore.load();

        assertNotNull(stored);
        assertEquals(true, stored.statusCommitted());
        assertEquals(77, stored.auxiliaryRetry().changesetId());
    }

    @Test
    void authenticationDuringStartupDoesNotEraseStoredRecovery() {
        enterDraft();
        workflow.shutdown();

        workflow.authenticatedAs("https://maproulette.example/api/v2/", new AuthenticatedUser(42, 24, "mapper", 0));

        assertNotNull(WorkflowDraftStore.load());
    }

    private void enterDraft() {
        workflow.authenticatedAs("https://maproulette.example/api/v2", new AuthenticatedUser(42, 24, "mapper", 0));
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(task);
        workflow.beginDownload(null);
        workflow.activateTask(task, layer);
        workflow.draftCompletion(new CompletionDraft(task, CompletionResult.FIXED, "comment", "tag", true,
                Map.of("confirmed", true), NextMode.NEARBY));
    }
}
