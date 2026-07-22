// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openstreetmap.josm.data.UserIdentityManager;
import org.openstreetmap.josm.plugins.maproulette.api.UnauthorizedException;
import org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Utils;

/**
 * Owns MapRoulette credential selection and the validated account state.
 */
public final class AuthenticationManager {
    public static final String DEFAULT_OSM_PREFERENCE = "maproulette_apikey_v2";
    private static final String MODE_PREFIX = "maproulette.authentication.";
    private static final String DIRECT_PREFIX = "maproulette.direct.";
    private static final String AUTOMATIC_PREFIX = "maproulette.openstreetmap.";
    private static final String LEGACY_AUTOMATIC_PREFIX = "maproulette.openstreetmap";
    private static final Map<String, String> SESSION_DIRECT_KEYS = new ConcurrentHashMap<>();
    private static volatile AuthenticationContext authenticatedContext;

    static {
        UserIdentityManager.getInstance().addListener(AuthenticationManager::clearActiveAuthentication);
    }

    private AuthenticationManager() {
        // Hide constructor
    }

    /**
     * Normalize a base URL for preference and in-memory scoping.
     *
     * @param baseUrl URL to normalize
     * @return URL without trailing slashes
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (Utils.isStripEmpty(baseUrl)) {
            throw new IllegalArgumentException("MapRoulette API URL is missing");
        }
        var normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public static AuthenticationMode getMode(String baseUrl) {
        final var value = Config.getPref().get(MODE_PREFIX + normalizeBaseUrl(baseUrl), AuthenticationMode.AUTOMATIC.name());
        try {
            return AuthenticationMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AuthenticationMode.AUTOMATIC;
        }
    }

    public static void setMode(String baseUrl, AuthenticationMode mode) {
        final var normalized = normalizeBaseUrl(baseUrl);
        if (getMode(normalized) == mode) {
            return;
        }
        if (mode == AuthenticationMode.AUTOMATIC) {
            Config.getPref().put(MODE_PREFIX + normalized, null);
        } else {
            Config.getPref().put(MODE_PREFIX + normalized, mode.name());
        }
        clearActiveAuthentication();
    }

    public static String getOsmPreferenceName(String baseUrl) {
        final var normalized = normalizeBaseUrl(baseUrl);
        final var correctedKey = AUTOMATIC_PREFIX + normalized + ".api_key";
        var preferenceName = Config.getPref().get(correctedKey);
        if (Utils.isStripEmpty(preferenceName)) {
            preferenceName = Config.getPref().get(LEGACY_AUTOMATIC_PREFIX + normalized + ".api_key");
            if (!Utils.isStripEmpty(preferenceName)) {
                Config.getPref().put(correctedKey, preferenceName);
            }
        }
        return Utils.isStripEmpty(preferenceName) ? DEFAULT_OSM_PREFERENCE : preferenceName;
    }

    public static void setOsmPreferenceName(String baseUrl, String preferenceName) {
        final var normalized = normalizeBaseUrl(baseUrl);
        final var normalizedName = Utils.isStripEmpty(preferenceName) ? DEFAULT_OSM_PREFERENCE : preferenceName.strip();
        if (getOsmPreferenceName(normalized).equals(normalizedName)) {
            return;
        }
        final var key = AUTOMATIC_PREFIX + normalized + ".api_key";
        if (DEFAULT_OSM_PREFERENCE.equals(normalizedName)) {
            Config.getPref().put(key, null);
        } else {
            Config.getPref().put(key, normalizedName);
        }
        OsmPreferenceUtils.clearCachedKey(normalized);
        clearActiveAuthentication();
    }

    public static String getDirectApiKey(String baseUrl) {
        final var normalized = normalizeBaseUrl(baseUrl);
        final var key = SESSION_DIRECT_KEYS.computeIfAbsent(normalized,
                scopedUrl -> Config.getPref().get(DIRECT_PREFIX + scopedUrl + ".api_key"));
        return Utils.isStripEmpty(key) ? null : key;
    }

    public static boolean isDirectKeyRemembered(String baseUrl) {
        return !Utils.isStripEmpty(Config.getPref().get(DIRECT_PREFIX + normalizeBaseUrl(baseUrl) + ".api_key"));
    }

    public static void setDirectApiKey(String baseUrl, String apiKey, boolean remember) {
        final var normalized = normalizeBaseUrl(baseUrl);
        final var preferenceKey = DIRECT_PREFIX + normalized + ".api_key";
        final var normalizedKey = Utils.isStripEmpty(apiKey) ? null : apiKey.strip();
        if (java.util.Objects.equals(normalizedKey, getDirectApiKey(normalized))
                && remember == isDirectKeyRemembered(normalized)) {
            return;
        }
        if (Utils.isStripEmpty(apiKey)) {
            SESSION_DIRECT_KEYS.remove(normalized);
            Config.getPref().put(preferenceKey, null);
        } else {
            SESSION_DIRECT_KEYS.put(normalized, normalizedKey);
            Config.getPref().put(preferenceKey, remember ? normalizedKey : null);
        }
        clearActiveAuthentication();
    }

    public static String getApiKey(String baseUrl) throws UnauthorizedException {
        final var normalized = normalizeBaseUrl(baseUrl);
        if (getMode(normalized) == AuthenticationMode.DIRECT) {
            final var key = getDirectApiKey(normalized);
            if (Utils.isStripEmpty(key)) {
                throw new UnauthorizedException("MapRoulette API key is missing");
            }
            return key;
        }
        return OsmPreferenceUtils.getMapRouletteApiKey(normalized, getOsmPreferenceName(normalized));
    }

    public static void setAuthenticated(String baseUrl, AuthenticationMode mode, String apiKey,
            AuthenticatedUser account) {
        authenticatedContext = new AuthenticationContext(normalizeBaseUrl(baseUrl), mode, currentOsmUserId(mode),
                fingerprint(apiKey), account);
    }

    public static AuthenticatedUser getAuthenticatedUser(String baseUrl) {
        final var context = authenticatedContext;
        if (context == null || !context.matches(normalizeBaseUrl(baseUrl), getMode(baseUrl),
                currentOsmUserId(getMode(baseUrl)))) {
            clearActiveAuthentication();
            return null;
        }
        return context.account();
    }

    public static boolean isAuthenticated(String baseUrl) {
        return getAuthenticatedUser(baseUrl) != null;
    }

    public static void clearActiveAuthentication() {
        authenticatedContext = null;
    }

    public static void serverChanged(String oldBaseUrl, String newBaseUrl) {
        if (oldBaseUrl != null && !normalizeBaseUrl(oldBaseUrl).equals(normalizeBaseUrl(newBaseUrl))) {
            clearActiveAuthentication();
        }
    }

    public static void handleUnauthorized(String baseUrl, String rejectedKey) {
        final var normalized = normalizeBaseUrl(baseUrl);
        if (rejectedKey.equals(SESSION_DIRECT_KEYS.get(normalized))
                || rejectedKey.equals(Config.getPref().get(DIRECT_PREFIX + normalized + ".api_key"))) {
            SESSION_DIRECT_KEYS.remove(normalized);
            Config.getPref().put(DIRECT_PREFIX + normalized + ".api_key", null);
        }
        OsmPreferenceUtils.clearCachedKey(normalized, rejectedKey);
        clearActiveAuthentication();
    }

    public static void clearCurrentCredential(String baseUrl) {
        final var normalized = normalizeBaseUrl(baseUrl);
        if (getMode(normalized) == AuthenticationMode.DIRECT) {
            SESSION_DIRECT_KEYS.remove(normalized);
            Config.getPref().put(DIRECT_PREFIX + normalized + ".api_key", null);
        } else {
            OsmPreferenceUtils.clearCachedKey(normalized);
        }
        clearActiveAuthentication();
    }

    static void clearSessionKeys() {
        SESSION_DIRECT_KEYS.clear();
        clearActiveAuthentication();
    }

    private static long currentOsmUserId(AuthenticationMode mode) {
        if (mode == AuthenticationMode.DIRECT) {
            return -1;
        }
        final var user = UserIdentityManager.getInstance().getUserInfo();
        return user == null ? -1 : user.getId();
    }

    private static String fingerprint(String apiKey) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record AuthenticationContext(String baseUrl, AuthenticationMode mode, long osmUserId, String keyFingerprint,
            AuthenticatedUser account) {
        boolean matches(String candidateBaseUrl, AuthenticationMode candidateMode, long candidateOsmUserId) {
            if (!baseUrl.equals(candidateBaseUrl) || mode != candidateMode || osmUserId != candidateOsmUserId) {
                return false;
            }
            try {
                return keyFingerprint.equals(fingerprint(AuthenticationManager.getApiKey(candidateBaseUrl)));
            } catch (UnauthorizedException exception) {
                return false;
            }
        }
    }
}
