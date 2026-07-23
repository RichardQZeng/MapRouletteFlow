// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.list;

import java.util.OptionalLong;

import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.spi.preferences.Config;

/** Stores the most recently loaded challenge without loading it automatically. */
final class RecentChallengePreference {
    private static final String KEY_PREFIX = "maproulette.workflow.last-challenge-id.";

    private RecentChallengePreference() {
    }

    static OptionalLong get(String baseUrl) {
        try {
            final var id = Long.parseLong(Config.getPref().get(key(baseUrl)));
            return id > 0 ? OptionalLong.of(id) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    static void remember(String baseUrl, long challengeId) {
        if (challengeId <= 0) {
            throw new IllegalArgumentException("Challenge ID must be positive");
        }
        Config.getPref().put(key(baseUrl), Long.toString(challengeId));
    }

    static void clear(String baseUrl) {
        Config.getPref().put(key(baseUrl), null);
    }

    private static String key(String baseUrl) {
        return KEY_PREFIX + AuthenticationManager.normalizeBaseUrl(baseUrl);
    }
}
