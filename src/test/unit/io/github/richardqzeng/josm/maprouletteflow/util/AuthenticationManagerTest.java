// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.UserIdentityManager;
import org.openstreetmap.josm.data.osm.UserInfo;
import io.github.richardqzeng.josm.maprouletteflow.api.UnauthorizedException;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
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
    void localPreferenceNameNeverReadsOrChangesOfficialPluginSettings() {
        final var officialKey = "maproulette.openstreetmap." + SERVER_A + ".api_key";
        final var flowKey = "maprouletteflow.openstreetmap." + SERVER_A + ".api_key";
        Config.getPref().put(officialKey, "official_key_name");

        assertEquals(AuthenticationManager.DEFAULT_OSM_PREFERENCE, AuthenticationManager.getOsmPreferenceName(SERVER_A));

        AuthenticationManager.setOsmPreferenceName(SERVER_A, "another_key");
        assertEquals("another_key", Config.getPref().get(flowKey));
        assertEquals("official_key_name", Config.getPref().get(officialKey));
    }

    @Test
    void automaticCacheIsScopedByServerAndOsmUser() throws UnauthorizedException {
        final var currentUserId = UserIdentityManager.getInstance().getUserInfo().getId();
        Config.getPref().put("maprouletteflow.openstreetmap." + SERVER_A + '.' + currentUserId, "a-user-key");
        Config.getPref().put("maprouletteflow.openstreetmap." + SERVER_B + '.' + currentUserId, "b-user-key");

        assertEquals("a-user-key", AuthenticationManager.getApiKey(SERVER_A));
        assertEquals("b-user-key", AuthenticationManager.getApiKey(SERVER_B));

        final var otherUser = new UserInfo();
        otherUser.setId(9999);
        UserIdentityManager.getInstance().setFullyIdentified("other", otherUser);
        Config.getPref().put("maprouletteflow.openstreetmap." + SERVER_A + ".9999", "other-user-key");
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

    @Test
    void staleUnauthorizedResponseDoesNotClearRotatedAccount() {
        final var account = new AuthenticatedUser(42, 1234, "Mapper", 135);
        AuthenticationManager.setMode(SERVER_A, AuthenticationMode.DIRECT);
        AuthenticationManager.setDirectApiKey(SERVER_A, "new-key", false);
        AuthenticationManager.setAuthenticated(SERVER_A, AuthenticationMode.DIRECT, "new-key", account);

        AuthenticationManager.handleUnauthorized(SERVER_A, "old-key");

        assertEquals(account, AuthenticationManager.getAuthenticatedUser(SERVER_A));
    }
}
