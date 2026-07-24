// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;

import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.api.parsers.AuthenticatedUserParser;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import io.github.richardqzeng.josm.maprouletteflow.util.HttpClientUtils;
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

    /** Validate and activate the credential currently configured for this server. */
    public static AuthenticatedUser authenticateConfigured(String baseUrl) throws IOException {
        final var normalized = AuthenticationManager.normalizeBaseUrl(baseUrl);
        final var mode = AuthenticationManager.getMode(normalized);
        final var apiKey = AuthenticationManager.getApiKey(normalized);
        final var account = validate(normalized, apiKey);
        if (mode != AuthenticationManager.getMode(normalized)
                || !apiKey.equals(AuthenticationManager.getApiKey(normalized))) {
            throw new IOException("MapRoulette authentication settings changed during validation");
        }
        AuthenticationManager.setAuthenticated(normalized, mode, apiKey, account);
        return account;
    }
}
