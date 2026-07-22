// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;

import org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser;
import org.openstreetmap.josm.plugins.maproulette.api.parsers.AuthenticatedUserParser;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.plugins.maproulette.util.HttpClientUtils;
import org.openstreetmap.josm.tools.Utils;

/**
 * API methods for the authenticated MapRoulette account.
 */
public final class CurrentUserAPI {
    private CurrentUserAPI() {
        // Hide constructor
    }

    /**
     * Validate a candidate key without using any configured MapRoulette credential.
     *
     * @param baseUrl MapRoulette API base URL
     * @param apiKey candidate API key
     * @return the authenticated account
     * @throws IOException if validation fails
     */
    public static AuthenticatedUser validate(String baseUrl, String apiKey) throws IOException {
        if (Utils.isStripEmpty(apiKey)) {
            throw new UnauthorizedException("MapRoulette API key is missing");
        }
        final var client = HttpClientUtils.getWithApiKey(AuthenticationManager.normalizeBaseUrl(baseUrl)
                + "/user/whoami", apiKey.strip());
        client.setAccept("application/json");
        try {
            final var response = client.connect();
            if (response.getResponseCode() == HTTP_UNAUTHORIZED) {
                AuthenticationManager.handleUnauthorized(baseUrl, apiKey.strip());
                throw new UnauthorizedException("MapRoulette rejected the API key");
            }
            if (response.getResponseCode() != HTTP_OK) {
                throw new IOException("MapRoulette account validation failed with HTTP " + response.getResponseCode());
            }
            try (var inputStream = response.getContent()) {
                return AuthenticatedUserParser.parse(inputStream);
            }
        } finally {
            client.disconnect();
        }
    }
}
