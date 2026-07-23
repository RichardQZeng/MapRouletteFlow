// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.openstreetmap.josm.plugins.maproulette.api.model.LeaderboardEntry;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonValue;

/** Parses the zero-or-one row response requested with leaderboard bracket zero. */
public final class LeaderboardEntryParser {
    private LeaderboardEntryParser() {
        // Hide constructor.
    }

    public static Optional<LeaderboardEntry> parse(InputStream inputStream) throws IOException {
        try (var reader = Json.createReader(inputStream)) {
            final var array = reader.readArray();
            if (array.isEmpty()) {
                return Optional.empty();
            }
            if (array.size() != 1) {
                throw new IOException("MapRoulette returned an invalid leaderboard response");
            }
            final var object = array.getJsonObject(0);
            final var completed = object.get("completedTasks") == null
                    || object.get("completedTasks").getValueType() == JsonValue.ValueType.NULL
                            ? 0
                            : object.getJsonNumber("completedTasks").longValueExact();
            return Optional.of(new LeaderboardEntry(object.getJsonNumber("userId").longValueExact(),
                    object.getJsonNumber("score").longValueExact(), completed,
                    object.getJsonNumber("rank").intValueExact()));
        } catch (JsonException | ArithmeticException | ClassCastException | NullPointerException exception) {
            throw new IOException("MapRoulette returned an invalid leaderboard response");
        }
    }
}
