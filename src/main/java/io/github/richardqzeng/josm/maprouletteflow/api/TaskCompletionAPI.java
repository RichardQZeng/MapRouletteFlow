// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import io.github.richardqzeng.josm.maprouletteflow.util.HttpClientUtils;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionAuxiliaryRetry;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionDraft;

import jakarta.json.Json;
import jakarta.json.JsonObject;

/** Checked HTTP operations for the MapRoulette web completion contract. */
public final class TaskCompletionAPI {
    private static final String TASK = "/task";

    private TaskCompletionAPI() {
    }

    public static void updateStatus(CompletionDraft draft) throws IOException {
        final var baseUrl = getBaseUrl();
        final Map<String, String> query = new TreeMap<>();
        if (draft.requestReview() != null) {
            query.put("requestReview", draft.requestReview().toString());
        }
        if (!draft.tags().isBlank()) {
            query.put("tags", draft.tags());
        }
        final var json = Json.createObjectBuilder();
        draft.completionResponses().forEach((name, value) -> addJsonValue(json, name, value));
        final var client = HttpClientUtils.put(baseUrl + TASK + "/" + draft.task().id() + "/"
                + draft.result().actionId(), query, json.build().toString().getBytes(StandardCharsets.UTF_8));
        client.setHeader("Content-Type", "application/json");
        expect(client, baseUrl, HTTP_NO_CONTENT, "task status");
    }

    public static void addComment(CompletionAuxiliaryRetry comment) throws IOException {
        final var baseUrl = getBaseUrl();
        final var body = Json.createObjectBuilder().add("comment", comment.comment()).build().toString()
                .getBytes(StandardCharsets.UTF_8);
        final var client = HttpClientUtils.post(baseUrl + TASK + "/" + comment.taskId() + "/comment",
                Map.of("actionId", Integer.toString(comment.actionId())), body);
        client.setHeader("Content-Type", "application/json");
        expect(client, baseUrl, HTTP_CREATED, "task comment");
    }

    public static void associateChangeset(long taskId, int changesetId) throws IOException {
        final var baseUrl = getBaseUrl();
        final var client = HttpClientUtils.put(baseUrl + TASK + "/" + taskId + "/changeset", Map.of(), new byte[0]);
        expect(client, baseUrl, HTTP_OK, "task changeset association");
        // The current API infers the association and does not accept an ID. The caller retains changesetId so a
        // failed matcher request can be retried without repeating Fixed status.
    }

    public static boolean hasTaskStatus(long taskId, int status) throws IOException {
        return taskSummary(taskId).getInt("status") == status;
    }

    private static JsonObject taskSummary(long taskId) throws IOException {
        final var baseUrl = getBaseUrl();
        final var client = HttpClientUtils.get(baseUrl + TASK + "/" + taskId, Map.of("summary", "true"));
        try {
            final var response = HttpClientUtils.connectExpecting(client, baseUrl, HTTP_OK, "task lookup");
            try (var reader = Json.createReader(response.getContent())) {
                return reader.readObject();
            }
        } finally {
            client.disconnect();
        }
    }

    private static void expect(org.openstreetmap.josm.tools.HttpClient client, String baseUrl, int expected,
            String operation) throws IOException {
        try {
            HttpClientUtils.connectExpecting(client, baseUrl, expected, operation);
        } finally {
            client.disconnect();
        }
    }

    private static void addJsonValue(jakarta.json.JsonObjectBuilder json, String name, Object value) {
        if (value instanceof Boolean booleanValue) {
            json.add(name, booleanValue);
        } else if (value instanceof Number number) {
            json.add(name, number.longValue());
        } else if (value != null) {
            json.add(name, value.toString());
        }
    }
}
