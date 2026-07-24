// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalArray;
import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalInstant;

import java.io.InputStream;

import io.github.richardqzeng.josm.maprouletteflow.api.model.Project;

import jakarta.annotation.Nonnull;
import jakarta.json.Json;
import jakarta.json.JsonValue;

/**
 * A parser for {@link io.github.richardqzeng.josm.maprouletteflow.api.model.Project} objects
 */
public final class ProjectParser {
    private ProjectParser() {
        // Hide constructor
    }

    /**
     * Parse a project from an {@link InputStream}
     *
     * @param inputStream The stream to parse
     * @return The parsed project
     */
    @Nonnull
    public static Project parse(InputStream inputStream) {
        try (final var reader = Json.createReader(inputStream)) {
            final var structure = reader.read();
            if (structure.getValueType() == JsonValue.ValueType.OBJECT) {
                final var obj = structure.asJsonObject();
                return new Project(obj.getJsonNumber("id").longValue(), obj.getJsonNumber("owner").longValue(),
                        obj.getString("name"), optionalInstant(obj, "created"), optionalInstant(obj, "modified"),
                        obj.getString("description"), optionalArray(obj, "grants", GrantParser::parse),
                        obj.getBoolean("enabled", true), obj.getString("displayName"), obj.getBoolean("deleted", false),
                        obj.getBoolean("featured", false));
            } else {
                throw new IllegalArgumentException("Could not parse Project JSON: " + structure);
            }
        }
    }
}
