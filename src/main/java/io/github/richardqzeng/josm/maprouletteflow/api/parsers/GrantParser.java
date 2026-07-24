// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalObject;

import io.github.richardqzeng.josm.maprouletteflow.api.model.Grant;
import io.github.richardqzeng.josm.maprouletteflow.api.model.GrantTarget;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Grantee;

import jakarta.annotation.Nonnull;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

/**
 * A parser for grants
 */
final class GrantParser {
    private GrantParser() {
        // Hide constructor
    }

    /**
     * Get the grants from an array
     *
     * @param multiple The array with multiple grants
     * @return The parsed grants
     */
    @Nonnull
    static Grant[] parse(JsonArray multiple) {
        final var grants = new Grant[multiple.size()];
        for (var i = 0; i < grants.length; i++) {
            final var tObj = multiple.get(i);
            if (tObj.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new IllegalArgumentException("Bad Grant array: " + multiple);
            }
            grants[i] = parse(tObj.asJsonObject());
        }
        return grants;
    }

    /**
     * Parse a single grant
     *
     * @param singleton The grant to parse
     * @return The parsed grant
     */
    @Nonnull
    static Grant parse(JsonObject singleton) {
        return new Grant(singleton.getJsonNumber("id").longValue(), singleton.getString("name", null),
                optionalObject(singleton, "grantee", GrantParser::parseGrantee), singleton.getInt("role"),
                optionalObject(singleton, "target", GrantParser::parseTarget));
    }

    /**
     * Parse a grantee object
     *
     * @param object The object to parse
     * @return The grantee
     */
    @Nonnull
    private static Grantee parseGrantee(JsonObject object) {
        return new Grantee(object.getInt("granteeType"), object.getJsonNumber("granteeId").longValue());
    }

    /**
     * Parse a GrantTarget
     *
     * @param object The originating grant target object
     * @return The target
     */
    @Nonnull
    private static GrantTarget parseTarget(JsonObject object) {
        return new GrantTarget(object.getInt("objectType"), object.getJsonNumber("objectId").longValue());
    }
}
