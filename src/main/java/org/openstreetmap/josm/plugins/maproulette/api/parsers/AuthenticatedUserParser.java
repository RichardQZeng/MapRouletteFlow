// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;

import org.openstreetmap.josm.plugins.maproulette.api.enums.Achievement;
import org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser;

import jakarta.json.Json;
import jakarta.json.JsonException;

/**
 * Parses only the non-sensitive account fields needed from {@code /user/whoami}.
 */
public final class AuthenticatedUserParser {
    private AuthenticatedUserParser() {
        // Hide constructor
    }

    /**
     * Parse an authenticated account response.
     *
     * @param inputStream response body
     * @return the minimal authenticated account
     * @throws IOException if required account fields are absent or malformed
     */
    public static AuthenticatedUser parse(InputStream inputStream) throws IOException {
        Long id = null;
        Long osmId = null;
        Long score = null;
        String displayName = null;
        final var achievements = new LinkedHashSet<Achievement>();
        var objectDepth = 0;
        var arrayDepth = 0;
        var osmProfileDepth = -1;
        var achievementsDepth = -1;
        String key = null;
        try (var parser = Json.createParser(inputStream)) {
            while (parser.hasNext()) {
                final var event = parser.next();
                switch (event) {
                case START_OBJECT -> {
                    objectDepth++;
                    if (objectDepth == 2 && "osmProfile".equals(key)) {
                        osmProfileDepth = objectDepth;
                    }
                    key = null;
                }
                case END_OBJECT -> {
                    if (objectDepth == osmProfileDepth) {
                        osmProfileDepth = -1;
                    }
                    objectDepth--;
                    key = null;
                }
                case START_ARRAY -> {
                    arrayDepth++;
                    if (objectDepth == 1 && arrayDepth == 1 && "achievements".equals(key)) {
                        achievementsDepth = arrayDepth;
                    }
                    key = null;
                }
                case END_ARRAY -> {
                    if (arrayDepth == achievementsDepth) {
                        achievementsDepth = -1;
                    }
                    arrayDepth--;
                    key = null;
                }
                case KEY_NAME -> key = parser.getString();
                case VALUE_NUMBER -> {
                    if (objectDepth == 1 && arrayDepth == achievementsDepth) {
                        try {
                            Achievement.fromApiId(parser.getBigDecimal().intValueExact()).ifPresent(achievements::add);
                        } catch (ArithmeticException exception) {
                            // Ignore unknown numeric IDs that cannot be represented by this client.
                        }
                    } else if (objectDepth == 1 && "id".equals(key)) {
                        id = parser.getLong();
                    } else if (objectDepth == 1 && "score".equals(key)) {
                        score = parser.getLong();
                    } else if (objectDepth == osmProfileDepth && "id".equals(key)) {
                        osmId = parser.getLong();
                    }
                    key = null;
                }
                case VALUE_STRING -> {
                    if (objectDepth == osmProfileDepth && "displayName".equals(key)) {
                        displayName = parser.getString().strip();
                    }
                    key = null;
                }
                default -> key = null;
                }
            }
        } catch (JsonException | ClassCastException exception) {
            throw invalidResponse();
        }
        if (id == null || osmId == null || score == null || displayName == null || displayName.isEmpty()) {
            throw invalidResponse();
        }
        return new AuthenticatedUser(id, osmId, displayName, score, List.copyOf(achievements));
    }

    private static IOException invalidResponse() {
        // Do not retain the parser exception: it can contain fragments of the private response.
        return new IOException("MapRoulette returned an invalid account response");
    }
}
