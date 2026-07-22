// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URI;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.maproulette.util.MapRouletteConfig;

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

    private static WireMock wireMock() {
        final var server = URI.create(
                org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
