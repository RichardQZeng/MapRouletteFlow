// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import java.io.StringReader;
import java.util.Map;
import java.util.TreeMap;

import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.spi.preferences.Config;

import jakarta.annotation.Nullable;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/** Versioned, credential-free persistence for one recoverable completion draft. */
public final class WorkflowDraftStore {
    private static final String KEY = "maprouletteflow.workflow.recovery";
    private static final int VERSION = 1;

    private WorkflowDraftStore() {
    }

    public record StoredDraft(String server, long mapRouletteUserId, long osmUserId, long challengeId, long taskId,
                              CompletionResult result, String comment, String tags, @Nullable Boolean requestReview,
                              Map<String, Object> completionResponses, NextMode nextMode, boolean statusCommitted,
                              @Nullable CompletionAuxiliaryRetry auxiliaryRetry, @Nullable Integer changesetId,
                              @Nullable String editLayerName) {
        public CompletionDraft toCompletionDraft(Task task) {
            if (task.id() != taskId) {
                throw new IllegalArgumentException("Recovered task does not match the stored draft");
            }
            return new CompletionDraft(task, result, comment, tags, requestReview, completionResponses, nextMode);
        }

        public WorkflowController.AccountOwner owner() {
            return new WorkflowController.AccountOwner(server, mapRouletteUserId, osmUserId);
        }
    }

    static void update(WorkflowController.Snapshot previous, WorkflowController.Snapshot snapshot) {
        final var draft = snapshot.completionDraft();
        final var owner = snapshot.accountOwner();
        final var challenge = snapshot.activeChallenge();
        if (draft == null || owner == null || challenge == null) {
            if (previous.completionDraft() != null && snapshot.state() != WorkflowController.State.DISCONNECTED) {
                clear();
            }
            return;
        }
        final var json = Json.createObjectBuilder().add("version", VERSION).add("server", owner.baseUrl())
                .add("mapRouletteUserId", owner.mapRouletteUserId()).add("osmUserId", owner.osmUserId())
                .add("challengeId", challenge.id()).add("taskId", draft.task().id())
                .add("result", draft.result().name()).add("comment", draft.comment()).add("tags", draft.tags())
                .add("nextMode", draft.nextMode().name()).add("statusCommitted", snapshot.completionStatusCommitted())
                .add("responses", responsesToJson(draft.completionResponses()));
        if (snapshot.editLayer() != null) {
            json.add("editLayerName", snapshot.editLayer().getName());
        }
        if (draft.requestReview() != null) {
            json.add("requestReview", draft.requestReview());
        }
        if (snapshot.completionChangesetId() != null) {
            json.add("changesetId", snapshot.completionChangesetId());
        }
        final var retry = snapshot.auxiliaryRetry();
        if (retry != null) {
            json.add("auxiliary", Json.createObjectBuilder().add("actionId", retry.actionId())
                    .add("comment", retry.comment()).add("changesetPending", retry.changesetPending())
                    .add("commentPending", retry.commentPending())
                    .add("changesetId", retry.changesetId() == null ? JsonValue.NULL : Json.createValue(retry.changesetId())));
        }
        Config.getPref().put(KEY, json.build().toString());
    }

    public static StoredDraft load() {
        final var value = Config.getPref().get(KEY, null);
        if (value == null) {
            return null;
        }
        try (var reader = Json.createReader(new StringReader(value))) {
            final var json = reader.readObject();
            if (json.getInt("version", -1) != VERSION) {
                return null;
            }
            final var responses = new TreeMap<String, Object>();
            json.getJsonObject("responses").forEach((key, response) -> responses.put(key, fromJson(response)));
            final var auxiliary = json.getJsonObject("auxiliary");
            final CompletionAuxiliaryRetry retry = auxiliary == null ? null
                    : new CompletionAuxiliaryRetry(json.getJsonNumber("taskId").longValue(),
                            auxiliary.getInt("actionId"), auxiliary.getString("comment"),
                            auxiliary.isNull("changesetId") ? null : auxiliary.getInt("changesetId"),
                            auxiliary.getBoolean("changesetPending"), auxiliary.getBoolean("commentPending"));
            return new StoredDraft(json.getString("server"), json.getJsonNumber("mapRouletteUserId").longValue(),
                    json.getJsonNumber("osmUserId").longValue(), json.getJsonNumber("challengeId").longValue(),
                    json.getJsonNumber("taskId").longValue(), CompletionResult.valueOf(json.getString("result")),
                    json.getString("comment"), json.getString("tags"),
                    json.containsKey("requestReview") ? json.getBoolean("requestReview") : null, responses,
                    NextMode.valueOf(json.getString("nextMode")), json.getBoolean("statusCommitted"), retry,
                    json.containsKey("changesetId") ? json.getInt("changesetId") : null,
                    json.getString("editLayerName", null));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static void clear() {
        Config.getPref().put(KEY, null);
    }

    static String serializedValue() {
        return Config.getPref().get(KEY, "");
    }

    private static JsonObject responsesToJson(Map<String, Object> responses) {
        final var json = Json.createObjectBuilder();
        responses.forEach((key, value) -> {
            if (value instanceof Boolean bool) {
                json.add(key, bool);
            } else if (value instanceof Number number) {
                json.add(key, number.longValue());
            } else {
                json.add(key, value.toString());
            }
        });
        return json.build();
    }

    private static Object fromJson(JsonValue value) {
        return switch (value.getValueType()) {
        case TRUE -> true;
        case FALSE -> false;
        case NUMBER -> ((jakarta.json.JsonNumber) value).longValue();
        case STRING -> ((jakarta.json.JsonString) value).getString();
        default -> value.toString();
        };
    }
}
