// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.util;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.UnauthorizedException;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;

@MapRouletteConfig
class HttpClientUtilsTest {
    @Test
    void centrallyEncodesEveryQueryKeyAndValue() {
        final var query = new TreeMap<>(Map.of("request review", "true", "tags", "one & two,slash/value"));
        assertEquals("?request%20review=true&tags=one%20%26%20two%2Cslash%2Fvalue", HttpClientUtils.query(query));
    }

    @Test
    void unauthorizedResponseClearsTheMatchingRequestCredential() throws Exception {
        final var baseUrl = io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl();
        final var apiKey = "42|00000000-0000-0000-0000-000000000042";
        authenticate(baseUrl, apiKey, 42);
        wireMock().register(get("/api/v2/probe").willReturn(unauthorized()));
        final var client = HttpClientUtils.getWithApiKey(baseUrl + "/probe", apiKey);

        try {
            assertThrows(UnauthorizedException.class,
                    () -> HttpClientUtils.connectExpecting(client, baseUrl, 200, "probe"));
        } finally {
            client.disconnect();
        }

        assertFalse(AuthenticationManager.isAuthenticated(baseUrl));
    }

    @Test
    void delayedUnauthorizedResponseDoesNotClearARotatedCredential() throws Exception {
        final var baseUrl = io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl();
        final var oldKey = "42|00000000-0000-0000-0000-000000000042";
        final var newKey = "43|00000000-0000-0000-0000-000000000043";
        authenticate(baseUrl, oldKey, 42);
        final var client = HttpClientUtils.getWithApiKey(baseUrl + "/probe", oldKey);
        authenticate(baseUrl, newKey, 43);
        wireMock().register(get("/api/v2/probe").willReturn(unauthorized()));

        try {
            assertThrows(UnauthorizedException.class,
                    () -> HttpClientUtils.connectExpecting(client, baseUrl, 200, "probe"));
        } finally {
            client.disconnect();
        }

        assertEquals(newKey, AuthenticationManager.getApiKey(baseUrl));
        assertEquals(43, AuthenticationManager.getAuthenticatedUser(baseUrl).id());
    }

    private static void authenticate(String baseUrl, String apiKey, long userId) {
        AuthenticationManager.setMode(baseUrl, AuthenticationMode.DIRECT);
        AuthenticationManager.setDirectApiKey(baseUrl, apiKey, false);
        AuthenticationManager.setAuthenticated(baseUrl, AuthenticationMode.DIRECT, apiKey,
                new AuthenticatedUser(userId, userId, "user", 0));
    }

    private static WireMock wireMock() {
        final var server = URI.create(
                io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
