// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.util;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.UserIdentityManager;
import org.openstreetmap.josm.data.preferences.ListProperty;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.io.OsmServerUserPreferencesReader;
import org.openstreetmap.josm.io.OsmTransferException;
import io.github.richardqzeng.josm.maprouletteflow.api.UnauthorizedException;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Utils;

/**
 * Get preference information
 */
public final class OsmPreferenceUtils {
    /**
     * Prevent instantiation of the utils
     */
    private OsmPreferenceUtils() {
        // Hide constructor
    }

    /**
     * Get the MapRoulette key for the current user
     *
     * @return The key
     * @throws UnauthorizedException If the user is not logged in to either OSM through JOSM <i>or</i> has not logged in to MapRoulette.
     */
    public static String getMapRouletteApiKey(String baseUrl, String osmPreferenceName) throws UnauthorizedException {
        final var user = UserIdentityManager.getInstance().getUserInfo();
        if (user == null) {
            clearCachedKey(baseUrl);
            throw new UnauthorizedException("User is not logged in");
        }
        final var normalizedBaseUrl = AuthenticationManager.normalizeBaseUrl(baseUrl);
        final var preferenceKey = cachePreferenceKey(normalizedBaseUrl, user.getId());
        final var possibleApiKey = Config.getPref().get(preferenceKey);
        if (!Utils.isStripEmpty(possibleApiKey) && !"Couldn't authenticate you".equals(possibleApiKey)) {
            return possibleApiKey;
        }
        final var reader = new OsmServerUserPreferencesReader();
        final var monitor = new PleaseWaitProgressMonitor(tr("Fetching OpenStreetMap User Preferences"));
        final var userList = new ListProperty("maprouletteflow.openstreetmap.users", Collections.emptyList());
        try {
            final var key = reader.fetchUserPreferences(monitor, tr("Getting MapRoulette API Key"))
                    .getOrDefault(osmPreferenceName, null);
            final String userId = String.valueOf(user.getId());
            List<String> userIds = new ArrayList<>(userList.get());
            if (!userIds.contains(userId)) {
                userIds.add(userId);
                userList.put(userIds);
            }
            Config.getPref().put(preferenceKey, key);
            return key;
        } catch (OsmTransferException e) {
            throw new JosmRuntimeException(e);
        } finally {
            monitor.close();
        }
    }

    /**
     * Remove all cached keys (this can happen due to auth failure)
     */
    static void clearCachedKey(String baseUrl) {
        final var userList = new ListProperty("maprouletteflow.openstreetmap.users", Collections.emptyList());
        for (var userId : userList.get()) {
            Config.getPref().put(cachePreferenceKey(AuthenticationManager.normalizeBaseUrl(baseUrl), userId), null);
        }
    }

    static void clearCachedKey(String baseUrl, String rejectedKey) {
        final var userList = new ListProperty("maprouletteflow.openstreetmap.users", Collections.emptyList());
        for (var userId : userList.get()) {
            final var preferenceKey = cachePreferenceKey(AuthenticationManager.normalizeBaseUrl(baseUrl), userId);
            if (rejectedKey.equals(Config.getPref().get(preferenceKey))) {
                Config.getPref().put(preferenceKey, null);
            }
        }
    }

    private static String cachePreferenceKey(String baseUrl, Object userId) {
        return "maprouletteflow.openstreetmap." + baseUrl + '.' + userId;
    }
}
