// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.parsers;

import java.io.IOException;
import java.io.InputStream;

import jakarta.json.Json;
import jakarta.json.JsonException;

/** Parses the all-time completed-task count from the user metrics response. */
public final class UserMetricsParser {
    private UserMetricsParser() {
        // Hide constructor.
    }

    public static long parseCompletedTasks(InputStream inputStream) throws IOException {
        try (var reader = Json.createReader(inputStream)) {
            return reader.readObject().getJsonObject("tasks").getJsonNumber("total").longValueExact();
        } catch (JsonException | ArithmeticException | ClassCastException | NullPointerException exception) {
            throw new IOException("MapRoulette returned an invalid user metrics response");
        }
    }
}
