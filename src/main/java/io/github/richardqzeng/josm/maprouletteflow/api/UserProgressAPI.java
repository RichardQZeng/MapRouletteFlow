// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.api.model.LeaderboardEntry;
import io.github.richardqzeng.josm.maprouletteflow.api.model.UserProgress;
import io.github.richardqzeng.josm.maprouletteflow.api.parsers.LeaderboardEntryParser;
import io.github.richardqzeng.josm.maprouletteflow.api.parsers.UserMetricsParser;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import io.github.richardqzeng.josm.maprouletteflow.util.HttpClientUtils;
import org.openstreetmap.josm.tools.HttpClient;

/** Retrieves the compact progress data displayed by the task panel. */
public final class UserProgressAPI {
    private UserProgressAPI() {
        // Hide constructor.
    }

    public static UserProgress fetch(String baseUrl, AuthenticatedUser account) throws IOException {
        final var normalized = AuthenticationManager.normalizeBaseUrl(baseUrl);
        final var apiKey = AuthenticationManager.getApiKey(normalized);
        final var completedTasks = fetchCompletedTasks(normalized, account.id(), apiKey);
        final var allTime = fetchLeaderboard(normalized, account.id(), -1, apiKey);
        final var pastMonth = fetchLeaderboard(normalized, account.id(), 1, apiKey);
        validateUser(account.id(), allTime);
        validateUser(account.id(), pastMonth);
        return new UserProgress(account.achievements(), account.score(), completedTasks,
                allTime.map(LeaderboardEntry::rank).orElse(null),
                pastMonth.map(LeaderboardEntry::rank).orElse(null));
    }

    private static long fetchCompletedTasks(String baseUrl, long userId, String apiKey) throws IOException {
        final var client = HttpClientUtils.getWithApiKey(baseUrl + "/data/user/" + userId + "/metrics",
                Map.of("monthDuration", "-1", "reviewDuration", "-1", "reviewerDuration", "-1", "start", "",
                        "end", "", "reviewStart", "", "reviewEnd", "", "reviewerStart", "", "reviewerEnd", ""),
                apiKey);
        try {
            final var response = connect(client, baseUrl, apiKey, "user metrics");
            try (var input = response.getContent()) {
                return UserMetricsParser.parseCompletedTasks(input);
            }
        } finally {
            client.disconnect();
        }
    }

    private static Optional<LeaderboardEntry> fetchLeaderboard(String baseUrl, long userId, int monthDuration,
            String apiKey) throws IOException {
        final var client = HttpClientUtils.getWithApiKey(baseUrl + "/data/user/" + userId + "/leaderboard",
                Map.of("bracket", "0", "monthDuration", Integer.toString(monthDuration), "onlyEnabled", "true"),
                apiKey);
        try {
            final var response = connect(client, baseUrl, apiKey, "leaderboard");
            try (var input = response.getContent()) {
                return LeaderboardEntryParser.parse(input);
            }
        } finally {
            client.disconnect();
        }
    }

    private static HttpClient.Response connect(HttpClient client, String baseUrl, String apiKey, String operation)
            throws IOException {
        client.setAccept("application/json");
        final var response = client.connect();
        if (response.getResponseCode() == HTTP_UNAUTHORIZED) {
            AuthenticationManager.handleUnauthorized(baseUrl, apiKey);
            throw new UnauthorizedException("MapRoulette rejected the API key");
        }
        if (response.getResponseCode() != HTTP_OK) {
            throw new IOException("MapRoulette " + operation + " request failed with HTTP "
                    + response.getResponseCode());
        }
        return response;
    }

    private static void validateUser(long userId, Optional<LeaderboardEntry> entry) throws IOException {
        if (entry.isPresent() && entry.get().userId() != userId) {
            throw new IOException("MapRoulette returned leaderboard data for another user");
        }
    }
}
