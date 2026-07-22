// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api;

import static org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import org.openstreetmap.josm.plugins.maproulette.util.HttpClientUtils;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionAuxiliaryRetry;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionDraft;

import jakarta.json.Json;

/** Checked HTTP operations for the MapRoulette web completion contract. */
public final class TaskCompletionAPI {
    private static final String TASK = "/task";

    private TaskCompletionAPI() {
    }

    public static void updateStatus(CompletionDraft draft) throws IOException {
        final Map<String, String> query = new TreeMap<>();
        if (draft.requestReview() != null) {
            query.put("requestReview", draft.requestReview().toString());
        }
        if (!draft.tags().isBlank()) {
            query.put("tags", draft.tags());
        }
        final var json = Json.createObjectBuilder();
        draft.completionResponses().forEach((name, value) -> addJsonValue(json, name, value));
        final var client = HttpClientUtils.put(getBaseUrl() + TASK + "/" + draft.task().id() + "/"
                + draft.result().actionId(), query, json.build().toString().getBytes(StandardCharsets.UTF_8));
        client.setHeader("Content-Type", "application/json");
        expect(client, 204, "task status");
    }

    public static void addComment(CompletionAuxiliaryRetry comment) throws IOException {
        final var body = Json.createObjectBuilder().add("comment", comment.comment()).build().toString()
                .getBytes(StandardCharsets.UTF_8);
        final var client = HttpClientUtils.post(getBaseUrl() + TASK + "/" + comment.taskId() + "/comment",
                Map.of("actionId", Integer.toString(comment.actionId())), body);
        client.setHeader("Content-Type", "application/json");
        expect(client, 201, "task comment");
    }

    private static void expect(org.openstreetmap.josm.tools.HttpClient client, int expected, String operation)
            throws IOException {
        try {
            final var response = client.connect();
            if (response.getResponseCode() == HTTP_UNAUTHORIZED) {
                AuthenticationManager.clearCurrentCredential(getBaseUrl());
                throw new UnauthorizedException("MapRoulette rejected the API key");
            }
            if (response.getResponseCode() != expected) {
                throw new IOException("MapRoulette " + operation + " returned HTTP " + response.getResponseCode()
                        + "; expected " + expected);
            }
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
