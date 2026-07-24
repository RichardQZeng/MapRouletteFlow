// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.Achievement;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationMode;
import io.github.richardqzeng.josm.maprouletteflow.util.MapRouletteConfig;

@MapRouletteConfig
class UserProgressAPITest {
    private static final String API_KEY = "progress-secret";
    private final AuthenticatedUser account = new AuthenticatedUser(42, 1234, "Mapper", 16_065,
            List.of(Achievement.MAPPED_WATER, Achievement.POINTS_10000));

    @BeforeEach
    void authenticate() {
        AuthenticationManager.setDirectApiKey(baseUrl(), API_KEY, false);
        AuthenticationManager.setMode(baseUrl(), AuthenticationMode.DIRECT);
        AuthenticationManager.setAuthenticated(baseUrl(), AuthenticationMode.DIRECT, API_KEY, account);
    }

    @AfterEach
    void clearAuthentication() {
        AuthenticationManager.clearCurrentCredential(baseUrl());
    }

    @Test
    void fetchesCompletedTasksAndBothRanksWithExactCredential() throws Exception {
        stubMetrics();
        stubLeaderboard(-1, "[{\"userId\":42,\"score\":16065,\"rank\":440,\"completedTasks\":3377}]");
        stubLeaderboard(1, "[{\"userId\":42,\"score\":600,\"rank\":72,\"completedTasks\":120}]");

        final var progress = UserProgressAPI.fetch(baseUrl(), account);

        assertEquals(16_065, progress.score());
        assertEquals(3_377, progress.completedTasks());
        assertEquals(440, progress.allTimeRank());
        assertEquals(72, progress.pastMonthRank());
        assertEquals(account.achievements(), progress.achievements());
        wireMock().verifyThat(1, getRequestedFor(urlPathEqualTo("/api/v2/data/user/42/metrics"))
                .withHeader("apiKey", equalTo(API_KEY)).withoutHeader("Authorization"));
        wireMock().verifyThat(2, getRequestedFor(urlPathEqualTo("/api/v2/data/user/42/leaderboard"))
                .withHeader("apiKey", equalTo(API_KEY)).withoutHeader("Authorization"));
    }

    @Test
    void treatsEmptyLeaderboardsAsUnranked() throws Exception {
        stubMetrics();
        stubLeaderboard(-1, "[]");
        stubLeaderboard(1, "[]");

        final var progress = UserProgressAPI.fetch(baseUrl(), account);

        assertNull(progress.allTimeRank());
        assertNull(progress.pastMonthRank());
    }

    @Test
    void rejectsAnotherUsersLeaderboardRow() {
        stubMetrics();
        stubLeaderboard(-1, "[{\"userId\":99,\"score\":1,\"rank\":1}]");
        stubLeaderboard(1, "[]");

        assertThrows(java.io.IOException.class, () -> UserProgressAPI.fetch(baseUrl(), account));
    }

    @Test
    void unauthorizedResponseClearsAuthentication() {
        wireMock().register(get(urlPathEqualTo("/api/v2/data/user/42/metrics")).willReturn(unauthorized()));

        assertThrows(UnauthorizedException.class, () -> UserProgressAPI.fetch(baseUrl(), account));
        assertNull(AuthenticationManager.getAuthenticatedUser(baseUrl()));
    }

    private void stubMetrics() {
        wireMock().register(get(urlPathEqualTo("/api/v2/data/user/42/metrics"))
                .withQueryParam("monthDuration", equalTo("-1"))
                .withQueryParam("reviewDuration", equalTo("-1"))
                .withQueryParam("reviewerDuration", equalTo("-1"))
                .withQueryParam("start", equalTo(""))
                .withQueryParam("end", equalTo(""))
                .withQueryParam("reviewStart", equalTo(""))
                .withQueryParam("reviewEnd", equalTo(""))
                .withQueryParam("reviewerStart", equalTo(""))
                .withQueryParam("reviewerEnd", equalTo(""))
                .willReturn(okJson("{\"tasks\":{\"total\":3377}}")));
    }

    private void stubLeaderboard(int duration, String body) {
        wireMock().register(get(urlPathEqualTo("/api/v2/data/user/42/leaderboard"))
                .withQueryParam("bracket", equalTo("0"))
                .withQueryParam("monthDuration", equalTo(Integer.toString(duration)))
                .withQueryParam("onlyEnabled", equalTo("true")).willReturn(okJson(body)));
    }

    private static String baseUrl() {
        return io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl();
    }

    private static WireMock wireMock() {
        final var server = URI.create(baseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
