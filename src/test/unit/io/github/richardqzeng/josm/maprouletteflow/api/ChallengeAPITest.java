// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationMode;
import io.github.richardqzeng.josm.maprouletteflow.util.MapRouletteConfig;

@MapRouletteConfig
class ChallengeAPITest {
    @Test
    void randomCandidateUsesLimitOneWithoutProximity() throws IOException {
        wireMock().register(get(urlPathEqualTo("/api/v2/challenge/42/tasks/prioritizedTasks"))
                .willReturn(okJson("[]")));

        assertNull(ChallengeAPI.prioritizedTask(42, null));

        wireMock().verifyThat(getRequestedFor(urlPathEqualTo("/api/v2/challenge/42/tasks/prioritizedTasks"))
                .withQueryParam("limit", equalTo("1")).withoutQueryParam("proximity"));
    }

    @Test
    void nearbyCandidateUsesCompletedTaskAsProximity() throws IOException {
        wireMock().register(get(urlPathEqualTo("/api/v2/challenge/42/tasks/prioritizedTasks"))
                .willReturn(okJson("[]")));

        assertNull(ChallengeAPI.prioritizedTask(42, 99L));

        wireMock().verifyThat(getRequestedFor(urlPathEqualTo("/api/v2/challenge/42/tasks/prioritizedTasks"))
                .withQueryParam("limit", equalTo("1")).withQueryParam("proximity", equalTo("99")));
    }

    @Test
    void candidateAcceptsExplicitNullOptionalFields() throws IOException {
        wireMock().register(get(urlPathEqualTo("/api/v2/challenge/50561/tasks/prioritizedTasks"))
                .willReturn(okJson("""
                        [{
                          "id": 266668897,
                          "name": "node/356749972",
                          "created": "2024-12-20T01:07:46.458Z",
                          "modified": "2024-12-20T01:07:46.458Z",
                          "parent": 50561,
                          "instruction": "",
                          "location": {"type": "Point", "coordinates": [-95.8227354, 31.4773988]},
                          "geometries": {"features": [{
                            "id": "node/356749972",
                            "type": "Feature",
                            "geometry": {"type": "Point", "coordinates": [-95.8227354, 31.4773988]},
                            "properties": {"@id": "node/356749972", "natural": "water"}
                          }]},
                          "cooperativeWork": null,
                          "status": 0,
                          "mappedOn": null,
                          "completedTimeSpent": null,
                          "completedBy": null,
                          "review": {},
                          "priority": 0,
                          "changesetId": -1,
                          "completionResponses": null,
                          "bundleId": null,
                          "isBundlePrimary": null,
                          "mapillaryImages": null,
                          "errorTags": ""
                        }]
                        """)));

        final var task = ChallengeAPI.prioritizedTask(50561, null);

        assertEquals(266668897, task.id());
        assertNull(task.cooperativeWork());
        assertNull(task.bundleId());
    }

    @Test
    void unauthorizedChallengeResponseClearsAuthenticationBeforeParsing() throws IOException {
        final var baseUrl = io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl();
        final var apiKey = "42|00000000-0000-0000-0000-000000000042";
        AuthenticationManager.setMode(baseUrl, AuthenticationMode.DIRECT);
        AuthenticationManager.setDirectApiKey(baseUrl, apiKey, false);
        AuthenticationManager.setAuthenticated(baseUrl, AuthenticationMode.DIRECT, apiKey,
                new AuthenticatedUser(42, 24, "user", 0));
        wireMock().register(get(urlPathEqualTo("/api/v2/challenge/42")).willReturn(aResponse().withStatus(401)));

        assertThrows(UnauthorizedException.class, () -> ChallengeAPI.challenge(42));
        assertFalse(AuthenticationManager.isAuthenticated(baseUrl));
    }

    private static WireMock wireMock() {
        final var server = URI.create(
                io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
