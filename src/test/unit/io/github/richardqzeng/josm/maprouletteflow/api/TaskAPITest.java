// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationMode;
import io.github.richardqzeng.josm.maprouletteflow.util.MapRouletteConfig;

@MapRouletteConfig
class TaskAPITest {
    private static final long TASK_ID = 135045992;
    private static final String TASK_FILE = "api/v2/task/135045992/start";

    @BeforeEach
    void useAutomaticAuthentication() {
        AuthenticationManager.setMode(
                io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl(),
                AuthenticationMode.AUTOMATIC);
    }

    @Test
    void getsExactTaskWithoutStartingIt() throws IOException {
        wireMock().register(get(urlPathEqualTo("/api/v2/task/" + TASK_ID))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBodyFile(TASK_FILE)));

        assertEquals(TASK_ID, TaskAPI.get(TASK_ID).id());

        wireMock().verifyThat(getRequestedFor(urlPathEqualTo("/api/v2/task/" + TASK_ID)));
    }

    @Test
    void startsExactTask() throws IOException {
        wireMock().register(get(urlPathEqualTo("/api/v2/task/" + TASK_ID + "/start"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBodyFile(TASK_FILE)));

        assertEquals(TASK_ID, TaskAPI.start(TASK_ID).id());

        wireMock().verifyThat(getRequestedFor(urlPathEqualTo("/api/v2/task/" + TASK_ID + "/start")));
    }

    @Test
    void unauthorizedTaskResponseClearsAuthenticationBeforeParsing() throws IOException {
        final var baseUrl = io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl();
        final var apiKey = "42|00000000-0000-0000-0000-000000000042";
        AuthenticationManager.setMode(baseUrl, AuthenticationMode.DIRECT);
        AuthenticationManager.setDirectApiKey(baseUrl, apiKey, false);
        AuthenticationManager.setAuthenticated(baseUrl, AuthenticationMode.DIRECT, apiKey,
                new AuthenticatedUser(42, 24, "user", 0));
        wireMock().register(get(urlPathEqualTo("/api/v2/task/42/start")).willReturn(aResponse().withStatus(401)));

        assertThrows(UnauthorizedException.class, () -> TaskAPI.start(42));
        assertFalse(AuthenticationManager.isAuthenticated(baseUrl));
    }

    private static WireMock wireMock() {
        final var server = URI.create(
                io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
