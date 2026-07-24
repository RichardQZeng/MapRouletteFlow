// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import java.time.Instant;
import java.util.function.Function;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * Utils to avoid some common patterns (e.g., object.containsKey("foo") ? object.getJsonNumber("foo").longValue() : 0)
 */
public final class ParsingUtils {
    /**
     * Prevent instantiation of the utils
     */
    private ParsingUtils() {
        // Hide constructor
    }

    /**
     * Get an optional object
     *
     * @param object The json to read from
     * @param key    The key which might not be present
     * @param parser The parser to parse the object from
     * @param <T>    The type
     * @return The parsed object
     */
    @Nullable
    public static <T> T optionalObject(@Nonnull JsonObject object, @Nonnull String key,
            @Nonnull Function<JsonObject, T> parser) {
        if (object.get(key) instanceof JsonObject value) {
            return parser.apply(value);
        }
        return null;
    }

    /**
     * Get an optional array
     *
     * @param object The json to read from
     * @param key    The key which might not be present
     * @param parser The parser to parse the array from
     * @param <T>    The type
     * @return The parsed array
     */
    @Nullable
    public static <T> T optionalArray(@Nonnull JsonObject object, @Nonnull String key,
            @Nonnull Function<JsonArray, T> parser) {
        if (object.get(key) instanceof JsonArray value) {
            return parser.apply(value);
        }
        return null;
    }

    /**
     * Get an optional instant
     *
     * @param object The object which might have the instant
     * @param key    The key with the instant
     * @return The instant or null
     */
    @Nullable
    public static Instant optionalInstant(JsonObject object, String key) {
        if (object.get(key) instanceof JsonString value) {
            return Instant.parse(value.getString());
        }
        return null;
    }

    /**
     * Get an optional long
     *
     * @param object The object to get info from
     * @param key    The key to get the value for
     * @return The long value, or the default
     */
    @Nullable
    public static Long optionalLong(JsonObject object, String key) {
        if (object.get(key) instanceof JsonNumber value) {
            return value.longValue();
        }
        return null;
    }

    /**
     * Get an optional int
     *
     * @param object The object with the int
     * @param key    The key to look for
     * @return The int, or null
     */
    @Nullable
    public static Integer optionalInteger(JsonObject object, String key) {
        if (object.get(key) instanceof JsonNumber value) {
            return value.intValue();
        }
        return null;
    }
}
