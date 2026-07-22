// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
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
}
