// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import io.github.richardqzeng.josm.maprouletteflow.api.model.PointReview;
import io.github.richardqzeng.josm.maprouletteflow.api.model.PublicUser;

import jakarta.annotation.Nullable;
import jakarta.json.JsonObject;

/**
 * A parser for user objects
 */
final class UserParser {
    private UserParser() {
        /* Hide constructor */ }

    /**
     * Parse a {@link PointReview} object
     *
     * @param object The object to parse
     * @return The parsed review object
     */
    @Nullable
    static PublicUser parse(@Nullable JsonObject object) {
        if (object != null && object.containsKey("username") && object.containsKey("id")) {
            return new PublicUser(object.getJsonNumber("id").longValue(), null, object.getString("username"), null,
                    null);
        }
        return null;
    }
}
