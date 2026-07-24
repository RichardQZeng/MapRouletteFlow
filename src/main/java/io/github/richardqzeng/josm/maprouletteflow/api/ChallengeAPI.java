// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalArray;
import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalInstant;
import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalInteger;
import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalObject;
import static io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl;
import static io.github.richardqzeng.josm.maprouletteflow.util.HttpClientUtils.get;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ChallengeExtra;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ChallengeGeneral;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.api.parsers.PointParser;
import io.github.richardqzeng.josm.maprouletteflow.api.parsers.TaskParser;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;

/**
 * A class for methods related to the challenge apis
 */
public final class ChallengeAPI {
    /**
     * The base path for challenge requests
     */
    private static final String PATH = "/challenge";

    /**
     * Don't allow the API object to be instantiated
     */
    private ChallengeAPI() {
        // Hide constructor
    }

    /**
     * Reserve at most one prioritized task. Supplying no proximity gives the normal priority-based random behavior.
     *
     * @param challengeId challenge to reserve from
     * @param proximityTaskId optional completed task for nearby selection
     * @return the reserved task, or {@code null} when no task is available
     * @throws IOException if communication fails
     */
    @Nullable
    public static Task prioritizedTask(long challengeId, @Nullable Long proximityTaskId) throws IOException {
        final var tasks = taskCollectionEndpoints("/tasks/prioritizedTasks", challengeId, null, null, 1,
                proximityTaskId == null ? 0 : proximityTaskId);
        return tasks.length == 0 ? null : tasks[0];
    }

    /**
     * Common method for task collection endpoints
     *
     * @param challengeId  The challenge to get tasks for
     * @param searchString The string to search for (case insensitive)
     * @param tags         The task status to limit the response by
     * @param limit        The number of prioritized tasks to get. If less than zero, one is used.
     * @param proximity    The current task
     * @return The next task
     * @throws IOException if there was a problem communicating with the server
     */
    private static Task[] taskCollectionEndpoints(@Nonnull String path, long challengeId, @Nullable String searchString,
            @Nullable String[] tags, int limit, long proximity) throws IOException {
        Map<String, String> query = new TreeMap<>();
        if (searchString != null && !searchString.isBlank()) {
            query.put("s", searchString);
        }
        if (tags != null && tags.length > 0) {
            query.put("tags", String.join(",", tags));
        }
        if (limit > 0) {
            query.put("limit", String.valueOf(limit));
        }
        if (proximity > 0) {
            query.put("proximity", String.valueOf(proximity));
        }
        final var client = get(getBaseUrl() + PATH + "/" + challengeId + path, query);
        try {
            try (var inputStream = client.connect().getContent()) {
                return (Task[]) TaskParser.parseTask(inputStream);
            }
        } finally {
            client.disconnect();
        }
    }

    /**
     * Get a specified challenge
     *
     * @param challengeId The challenge to get
     * @return The challenge
     * @throws IOException if there was a problem communicating with the server
     */
    public static Challenge challenge(long challengeId) throws IOException {
        final var client = get(getBaseUrl() + PATH + "/" + challengeId, null);
        try {
            try (var inputstream = client.connect().getContent()) {
                return parseChallenge(inputstream);
            }
        } catch (RuntimeException exception) {
            throw new IOException("MapRoulette returned an invalid response for challenge " + challengeId, exception);
        } finally {
            client.disconnect();
        }
    }

    /**
     * Parse a challenge
     *
     * @param inputStream The incoming stream
     * @return The challenge
     */
    @Nonnull
    private static Challenge parseChallenge(InputStream inputStream) {
        try (var parser = Json.createReader(inputStream)) {
            JsonStructure structure = parser.read();
            if (structure.getValueType() == JsonValue.ValueType.OBJECT) {
                var obj = structure.asJsonObject();
                // These may be flattened into the top-level challenge object after creation.
                final var challengeGeneral = optionalObject(obj, "general", ChallengeAPI::parseChallengeGeneral);
                final var challengeExtra = optionalObject(obj, "extra", ChallengeAPI::parseChallengeExtra);
                return new Challenge(obj.getJsonNumber("id").longValue(), obj.getString("name"),
                        Instant.parse(obj.getString("created")), Instant.parse(obj.getString("modified")),
                        obj.getString("description", null), obj.getBoolean("deleted"), obj.getString("infoLink", null),
                        challengeGeneral == null ? ChallengeAPI.parseChallengeGeneral(obj) : challengeGeneral,
                        challengeExtra == null ? ChallengeAPI.parseChallengeExtra(obj) : challengeExtra,
                        optionalInteger(obj, "status"), obj.getString("statusMessage", null),
                        optionalInstant(obj, "lastTaskRefresh"), optionalInstant(obj, "dataOriginDate"),
                        optionalObject(obj, "location", PointParser::parse),
                        optionalObject(obj, "bounding", Object::toString), optionalInteger(obj, "completionPercentage"),
                        optionalInteger(obj, "tasksRemaining"));
            } else {
                throw new IllegalArgumentException("Bad challenge json");
            }
        }
    }

    /**
     * Parse a general info object for a challenge
     *
     * @param object The json value
     * @return The object
     */
    @Nonnull
    private static ChallengeGeneral parseChallengeGeneral(JsonObject object) {
        return new ChallengeGeneral(object.getJsonNumber("owner").longValue(),
                object.getJsonNumber("parent").longValue(), object.getString("instruction"),
                object.getInt("difficulty"), object.getString("blurb", null), object.getBoolean("enabled"),
                object.getBoolean("featured"), object.getInt("cooperativeType"), object.getInt("popularity"),
                object.getString("checkinComment"), object.getString("checkinSource"),
                object.getBoolean("changesetUrl", false), optionalArray(object, "virtualParents", array -> array
                        .getValuesAs(JsonNumber.class).stream().mapToLong(JsonNumber::longValue).toArray()),
                object.getBoolean("requiresLocal"));
    }

    /**
     * Parse extra information
     *
     * @param object The object to parse
     * @return The parsed information
     */
    @Nonnull
    private static ChallengeExtra parseChallengeExtra(JsonObject object) {
        return new ChallengeExtra(object.getInt("defaultZoom"), object.getInt("minZoom"), object.getInt("maxZoom"),
                object.getInt("defaultBasemap", -1), object.getString("defaultBasemapId", null),
                object.getString("customBasemap", null), object.getBoolean("updateTasks", false),
                object.getString("exportableProperties", null), object.getString("osmIdProperty", null),
                object.getString("preferredTags", null), object.getString("preferredReviewTags", null),
                object.getBoolean("limitTags"), object.getBoolean("limitReviewTags"),
                optionalArray(object, "taskStyles", JsonValue::toString),
                object.getString("taskBundleIdProperty", null), object.getBoolean("isArchived"),
                optionalInstant(object, "systemArchivedAt"), optionalArray(object, "presets", array -> array
                        .getValuesAs(JsonString.class).stream().map(JsonString::getString).toArray(String[]::new)));
    }
}
