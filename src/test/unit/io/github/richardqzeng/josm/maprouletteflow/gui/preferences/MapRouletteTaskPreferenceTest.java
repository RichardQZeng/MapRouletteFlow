// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class MapRouletteTaskPreferenceTest {
    @AfterEach
    void tearDown() {
        MapRouletteTaskPreference.setNextMode(NextMode.RANDOM);
        WorkflowController.getInstance().shutdown();
    }

    @Test
    void completionSelectionIsPersistedAndAppliedToWorkflow() {
        MapRouletteTaskPreference.setNextMode(NextMode.NEARBY);
        assertEquals(NextMode.NEARBY, MapRouletteTaskPreference.getNextMode());
        assertEquals(NextMode.NEARBY, WorkflowController.getInstance().snapshot().nextMode());
    }

    @Test
    void challengeModeOverridesGlobalWithFallbackForOtherChallenges() {
        MapRouletteTaskPreference.setNextMode(NextMode.RANDOM);
        MapRouletteTaskPreference.setNextMode(10, NextMode.NEARBY);

        assertEquals(NextMode.NEARBY, MapRouletteTaskPreference.getNextMode(10));
        assertEquals(NextMode.RANDOM, MapRouletteTaskPreference.getNextMode(11));
        assertEquals(NextMode.NEARBY, WorkflowController.getInstance().snapshot().nextMode());
    }

    @Test
    void changingGlobalDefaultKeepsActiveChallengeOverride() {
        final var workflow = WorkflowController.getInstance();
        workflow.connect();
        workflow.selectChallenge(new io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge(20, "challenge",
                null, null, null, false, null, null, null, null, null, null, null, null, null, null, null));
        MapRouletteTaskPreference.setNextMode(20, NextMode.NEARBY);

        MapRouletteTaskPreference.setNextMode(NextMode.RANDOM);

        assertEquals(NextMode.NEARBY, workflow.snapshot().nextMode());
        assertEquals(NextMode.NEARBY, MapRouletteTaskPreference.getNextMode(20));
    }
}
