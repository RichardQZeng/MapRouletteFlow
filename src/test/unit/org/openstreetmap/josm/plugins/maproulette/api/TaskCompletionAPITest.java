// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.plugins.maproulette.util.MapRouletteConfig;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionAuxiliaryRetry;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionDraft;
import org.openstreetmap.josm.plugins.maproulette.workflow.CompletionResult;

@MapRouletteConfig
class TaskCompletionAPITest {
    @Test
    void statusUsesExactPathEncodedQueryJsonBodyAnd204() throws IOException {
        wireMock().register(put(urlPathEqualTo("/api/v2/task/42/2")).willReturn(aResponse().withStatus(204)));
        final var draft = draft(Boolean.TRUE, "one & two,slash/value");

        TaskCompletionAPI.updateStatus(draft);

        wireMock().verifyThat(putRequestedFor(urlPathEqualTo("/api/v2/task/42/2"))
                .withQueryParam("requestReview", equalTo("true"))
                .withQueryParam("tags", equalTo("one & two,slash/value"))
                .withHeader("apiKey", equalTo(AuthenticationManager.getApiKey(
                        org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl())))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"choice\":\"yes\",\"checked\":true}")));
    }

    @Test
    void reviewFalseIsSentAndOmittedRemainsDistinguishable() throws IOException {
        wireMock().register(put(urlPathEqualTo("/api/v2/task/42/2")).willReturn(aResponse().withStatus(204)));

        TaskCompletionAPI.updateStatus(draft(Boolean.FALSE, ""));
        TaskCompletionAPI.updateStatus(draft(null, ""));

        wireMock().verifyThat(1, putRequestedFor(urlPathEqualTo("/api/v2/task/42/2"))
                .withQueryParam("requestReview", equalTo("false")));
        wireMock().verifyThat(1,
                putRequestedFor(urlPathEqualTo("/api/v2/task/42/2")).withoutQueryParam("requestReview"));
    }

    @Test
    void commentUsesSeparateActionEndpointJsonBodyAnd201() throws IOException {
        wireMock().register(post(urlPathEqualTo("/api/v2/task/42/comment")).willReturn(aResponse().withStatus(201)));

        TaskCompletionAPI.addComment(new CompletionAuxiliaryRetry(42, 6, "Text with \"quotes\" & unicode"));

        wireMock().verifyThat(postRequestedFor(urlPathEqualTo("/api/v2/task/42/comment"))
                .withQueryParam("actionId", equalTo("6")).withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("{\"comment\":\"Text with \\\"quotes\\\" & unicode\"}")));
    }

    @Test
    void changesetAssociationUsesCheckedEndpoint() throws IOException {
        wireMock().register(put(urlPathEqualTo("/api/v2/task/42/changeset")).willReturn(aResponse().withStatus(200)));

        TaskCompletionAPI.associateChangeset(42, 77);

        wireMock().verifyThat(putRequestedFor(urlPathEqualTo("/api/v2/task/42/changeset")));
    }

    @Test
    void unexpectedSuccessCodesAreRejected() {
        wireMock().register(put(urlPathEqualTo("/api/v2/task/42/2")).willReturn(aResponse().withStatus(200)));
        wireMock().register(post(urlPathEqualTo("/api/v2/task/42/comment")).willReturn(aResponse().withStatus(200)));

        assertThrows(IOException.class, () -> TaskCompletionAPI.updateStatus(draft(null, "")));
        assertThrows(IOException.class,
                () -> TaskCompletionAPI.addComment(new CompletionAuxiliaryRetry(42, 2, "comment")));
    }

    @Test
    void unauthorizedCompletionClearsAuthentication() throws IOException {
        final var baseUrl = org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl();
        AuthenticationManager.setDirectApiKey(baseUrl, "42|00000000-0000-0000-0000-000000000042", false);
        AuthenticationManager.setAuthenticated(baseUrl,
                org.openstreetmap.josm.plugins.maproulette.util.AuthenticationMode.DIRECT,
                "42|00000000-0000-0000-0000-000000000042",
                new org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser(42, 24, "user", 0));
        wireMock().register(put(urlPathEqualTo("/api/v2/task/42/2")).willReturn(aResponse().withStatus(401)));

        assertThrows(UnauthorizedException.class, () -> TaskCompletionAPI.updateStatus(draft(null, "")));
        assertFalse(AuthenticationManager.isAuthenticated(baseUrl));
    }

    private static CompletionDraft draft(Boolean requestReview, String tags) {
        final var task = new Task(42, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
        return new CompletionDraft(task, CompletionResult.NOT_AN_ISSUE, "", tags, requestReview,
                Map.of("choice", "yes", "checked", true), NextMode.RANDOM);
    }

    private static WireMock wireMock() {
        final var server = URI.create(
                org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig.getBaseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
