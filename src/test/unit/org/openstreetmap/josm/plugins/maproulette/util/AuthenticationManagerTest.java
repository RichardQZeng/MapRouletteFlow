// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.UserIdentityManager;
import org.openstreetmap.josm.data.osm.UserInfo;
import org.openstreetmap.josm.plugins.maproulette.api.UnauthorizedException;
import org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser;
import org.openstreetmap.josm.spi.preferences.Config;

@OsmUser
class AuthenticationManagerTest {
    private static final String SERVER_A = "https://a.example.test/api/v2";
    private static final String SERVER_B = "https://b.example.test/api/v2";

    @AfterEach
    void clearSession() {
        AuthenticationManager.clearSessionKeys();
    }

    @Test
    void directKeyIsSessionOnlyUnlessRememberedAndScopedByServer() throws UnauthorizedException {
        AuthenticationManager.setMode(SERVER_A, AuthenticationMode.DIRECT);
        AuthenticationManager.setDirectApiKey(SERVER_A, "server-a-key", false);
        AuthenticationManager.setMode(SERVER_B, AuthenticationMode.DIRECT);
        AuthenticationManager.setDirectApiKey(SERVER_B, "server-b-key", true);

        assertEquals("server-a-key", AuthenticationManager.getDirectApiKey(SERVER_A));
        assertFalse(AuthenticationManager.isDirectKeyRemembered(SERVER_A));
        assertEquals("server-b-key", AuthenticationManager.getApiKey(SERVER_B));
        assertTrue(AuthenticationManager.isDirectKeyRemembered(SERVER_B));

        AuthenticationManager.clearSessionKeys();
        assertNull(AuthenticationManager.getDirectApiKey(SERVER_A));
        assertEquals("server-b-key", AuthenticationManager.getDirectApiKey(SERVER_B));
    }

    @Test
    void readsLegacyCustomPreferenceNameAndMigratesToCorrectedKey() {
        final var legacyKey = "maproulette.openstreetmap" + SERVER_A + ".api_key";
        final var correctedKey = "maproulette.openstreetmap." + SERVER_A + ".api_key";
        Config.getPref().put(legacyKey, "custom_maproulette_key");

        assertEquals("custom_maproulette_key", AuthenticationManager.getOsmPreferenceName(SERVER_A));
        assertEquals("custom_maproulette_key", Config.getPref().get(correctedKey));

        AuthenticationManager.setOsmPreferenceName(SERVER_A, "another_key");
        assertEquals("another_key", Config.getPref().get(correctedKey));
    }

    @Test
    void automaticCacheIsScopedByServerAndOsmUser() throws UnauthorizedException {
        final var currentUserId = UserIdentityManager.getInstance().getUserInfo().getId();
        Config.getPref().put("maproulette.openstreetmap." + SERVER_A + '.' + currentUserId, "a-user-key");
        Config.getPref().put("maproulette.openstreetmap." + SERVER_B + '.' + currentUserId, "b-user-key");

        assertEquals("a-user-key", AuthenticationManager.getApiKey(SERVER_A));
        assertEquals("b-user-key", AuthenticationManager.getApiKey(SERVER_B));

        final var otherUser = new UserInfo();
        otherUser.setId(9999);
        UserIdentityManager.getInstance().setFullyIdentified("other", otherUser);
        Config.getPref().put("maproulette.openstreetmap." + SERVER_A + ".9999", "other-user-key");
        assertEquals("other-user-key", AuthenticationManager.getApiKey(SERVER_A));
    }

    @Test
    void accountServerAndKeyChangesClearValidatedAccount() {
        final var account = new AuthenticatedUser(42, 1234, "Mapper", 135);
        AuthenticationManager.setMode(SERVER_A, AuthenticationMode.DIRECT);
        AuthenticationManager.setDirectApiKey(SERVER_A, "first-key", false);
        AuthenticationManager.setAuthenticated(SERVER_A, AuthenticationMode.DIRECT, "first-key", account);
        assertTrue(AuthenticationManager.isAuthenticated(SERVER_A));

        AuthenticationManager.serverChanged(SERVER_A, SERVER_B);
        assertNull(AuthenticationManager.getAuthenticatedUser(SERVER_A));

        AuthenticationManager.setAuthenticated(SERVER_A, AuthenticationMode.DIRECT, "first-key", account);
        AuthenticationManager.setDirectApiKey(SERVER_A, "rotated-key", false);
        assertNull(AuthenticationManager.getAuthenticatedUser(SERVER_A));

        AuthenticationManager.setAuthenticated(SERVER_A, AuthenticationMode.DIRECT, "rotated-key", account);
        final var otherUser = new UserInfo();
        otherUser.setId(9999);
        UserIdentityManager.getInstance().setFullyIdentified("other", otherUser);
        assertNull(AuthenticationManager.getAuthenticatedUser(SERVER_A));
    }
}
