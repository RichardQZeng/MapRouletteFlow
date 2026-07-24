// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class RecentChallengePreferenceTest {
    private static final String SERVER = "https://maproulette.example/api/v2";

    @AfterEach
    void clearPreference() {
        RecentChallengePreference.clear(SERVER);
    }

    @Test
    void remembersTheLastChallengeForItsServer() {
        RecentChallengePreference.remember(SERVER + '/', 50561);

        assertEquals(50561, RecentChallengePreference.get(SERVER).orElseThrow());
        assertTrue(RecentChallengePreference.get("https://other.example/api/v2").isEmpty());
    }

    @Test
    void clearsTheRememberedChallenge() {
        RecentChallengePreference.remember(SERVER, 50561);

        RecentChallengePreference.clear(SERVER);

        assertTrue(RecentChallengePreference.get(SERVER).isEmpty());
    }
}
