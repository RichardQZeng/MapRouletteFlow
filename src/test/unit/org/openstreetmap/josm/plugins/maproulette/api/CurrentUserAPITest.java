// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationMode;
import org.openstreetmap.josm.plugins.maproulette.util.LoggingHandler;
import org.openstreetmap.josm.plugins.maproulette.util.MapRouletteConfig;

@LoggingHandler
@MapRouletteConfig
class CurrentUserAPITest {
    private static final String CANDIDATE_KEY = "candidate-secret";

    @AfterEach
    void clearAuthentication() {
        AuthenticationManager.clearCurrentCredential(
                org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl());
    }

    @Test
    void validatesWithExactApiKeyHeaderAndNoOsmAuthorization(LoggingHandler.TestHandler logs) throws IOException {
        wireMock().register(get("/api/v2/user/whoami").willReturn(okJson(validResponse())));

        final var account = CurrentUserAPI.validate(baseUrl(), CANDIDATE_KEY);

        assertEquals(new AuthenticatedUser(42, 1234, "Mapper", 135), account);
        wireMock().verifyThat(getRequestedFor(urlEqualTo("/api/v2/user/whoami"))
                .withHeader("apiKey", equalTo(CANDIDATE_KEY)).withoutHeader("Authorization"));
        final var logged = logs.getRecords().toString();
        assertFalse(logged.contains(CANDIDATE_KEY));
        assertFalse(logged.contains("private@example.test"));
    }

    @Test
    void validatesAutomaticOsmPreferenceKey() throws IOException {
        final var baseUrl = baseUrl();
        final var automaticKey = AuthenticationManager.getApiKey(baseUrl);
        wireMock().register(get("/api/v2/user/whoami")
                .withHeader("apiKey", equalTo(automaticKey)).willReturn(okJson(validResponse())));

        assertEquals(42, CurrentUserAPI.validate(baseUrl, automaticKey).id());
    }

    @Test
    void unauthorizedKeyClearsRememberedCredentialAndAccount() {
        final var baseUrl = baseUrl();
        final var account = new AuthenticatedUser(42, 1234, "Mapper", 135);
        AuthenticationManager.setDirectApiKey(baseUrl, CANDIDATE_KEY, true);
        AuthenticationManager.setMode(baseUrl, AuthenticationMode.DIRECT);
        AuthenticationManager.setAuthenticated(baseUrl, AuthenticationMode.DIRECT, CANDIDATE_KEY, account);
        wireMock().register(get("/api/v2/user/whoami").willReturn(unauthorized()));

        assertThrows(UnauthorizedException.class, () -> CurrentUserAPI.validate(baseUrl, CANDIDATE_KEY));
        assertNull(AuthenticationManager.getDirectApiKey(baseUrl));
        assertNull(AuthenticationManager.getAuthenticatedUser(baseUrl));
    }

    @Test
    void rejectsMalformedAndIncompleteResponses() {
        final var baseUrl = baseUrl();
        assertThrows(UnauthorizedException.class, () -> CurrentUserAPI.validate(baseUrl, " "));
        wireMock().register(get("/api/v2/user/whoami")
                .willReturn(okJson("{\"id\":42,\"score\":1}")));
        assertThrows(IOException.class, () -> CurrentUserAPI.validate(baseUrl, CANDIDATE_KEY));

        wireMock().resetMappings();
        wireMock().register(get("/api/v2/user/whoami")
                .willReturn(okJson("{not-json")));
        assertThrows(IOException.class, () -> CurrentUserAPI.validate(baseUrl, CANDIDATE_KEY));
    }

    @Test
    void replacesARejectedRotatedKey() throws IOException {
        final var baseUrl = baseUrl();
        final var oldKey = "old-candidate";
        final var rotatedKey = "rotated-candidate";
        AuthenticationManager.setDirectApiKey(baseUrl, oldKey, true);
        AuthenticationManager.setMode(baseUrl, AuthenticationMode.DIRECT);
        wireMock().register(get("/api/v2/user/whoami").withHeader("apiKey", equalTo(oldKey))
                .willReturn(unauthorized()));
        wireMock().register(get("/api/v2/user/whoami").withHeader("apiKey", equalTo(rotatedKey))
                .willReturn(okJson(validResponse())));

        assertThrows(UnauthorizedException.class, () -> CurrentUserAPI.validate(baseUrl, oldKey));
        AuthenticationManager.setDirectApiKey(baseUrl, rotatedKey, false);
        assertEquals(42, CurrentUserAPI.validate(baseUrl, rotatedKey).id());
    }

    @Test
    void authenticatesWithTheConfiguredCredential() throws IOException {
        final var baseUrl = baseUrl();
        AuthenticationManager.setDirectApiKey(baseUrl, CANDIDATE_KEY, true);
        AuthenticationManager.setMode(baseUrl, AuthenticationMode.DIRECT);
        wireMock().register(get("/api/v2/user/whoami").willReturn(okJson(validResponse())));

        final var account = CurrentUserAPI.authenticateConfigured(baseUrl);

        assertEquals(42, account.id());
        assertNotNull(AuthenticationManager.getAuthenticatedUser(baseUrl));
    }

    @Test
    void temporaryStartupFailureKeepsTheConfiguredCredential() {
        final var baseUrl = baseUrl();
        AuthenticationManager.setDirectApiKey(baseUrl, CANDIDATE_KEY, true);
        AuthenticationManager.setMode(baseUrl, AuthenticationMode.DIRECT);
        wireMock().register(get("/api/v2/user/whoami").willReturn(serverError()));

        assertThrows(IOException.class, () -> CurrentUserAPI.authenticateConfigured(baseUrl));

        assertEquals(CANDIDATE_KEY, AuthenticationManager.getDirectApiKey(baseUrl));
        assertNull(AuthenticationManager.getAuthenticatedUser(baseUrl));
    }

    private static String validResponse() {
        return """
                {"id":42,"score":135,"apiKey":"echoed-private-value","email":"private@example.test",
                 "osmProfile":{"id":1234,"displayName":"Mapper","description":"private"}}
                """;
    }

    private static String baseUrl() {
        return org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl();
    }

    private static WireMock wireMock() {
        final var server = URI.create(baseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
