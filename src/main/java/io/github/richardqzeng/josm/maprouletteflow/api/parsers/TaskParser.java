// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalInstant;
import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalLong;
import static io.github.richardqzeng.josm.maprouletteflow.api.parsers.ParsingUtils.optionalObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.io.IllegalDataException;
import org.openstreetmap.josm.io.OsmChangeReader;
import io.github.richardqzeng.josm.maprouletteflow.api.UnauthorizedException;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ElementCreate;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ElementTagChange;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ElementUpdate;
import io.github.richardqzeng.josm.maprouletteflow.api.model.OSMChange;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Point;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import org.openstreetmap.josm.tools.ExceptionUtil;
import org.openstreetmap.josm.tools.JosmRuntimeException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;

/**
 * Parse tasks
 */
public final class TaskParser {
    /**
     * Don't allow instantiation of this parser
     */
    private TaskParser() {
        // Hide constructor
    }

    /**
     * Parse a task
     *
     * @param inputStream the stream to get the task from
     * @return The new task. May be a singular task or an array of tasks.
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    @Nonnull
    public static Object parseTask(InputStream inputStream) throws UnauthorizedException {
        try (var reader = Json.createParser(inputStream)) {
            while (reader.hasNext()) {
                var value = switch (reader.next()) {
                case START_OBJECT -> parseTask(reader.getObject());
                case START_ARRAY -> reader.getArrayStream().filter(JsonObject.class::isInstance)
                        .map(JsonObject.class::cast).map(obj -> {
                            try {
                                return parseTask(obj);
                            } catch (UnauthorizedException e) {
                                throw new UncheckedIOException(e);
                            }
                        }).toArray(Task[]::new);
                default -> null;
                };
                if (value != null) {
                    return value;
                }
            }
        } catch (UncheckedIOException e) {
            if (e.getCause()instanceof UnauthorizedException unauthorizedException) {
                throw unauthorizedException;
            }
            throw e;
        }
        throw new IllegalArgumentException("InputStream did not contain expected JSON data");
    }

    /**
     * Parse a task
     *
     * @param obj the JsonObject to get the task from
     * @return The new task
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    @Nonnull
    private static Task parseTask(JsonObject obj) throws UnauthorizedException {
        MessageParser.parse(obj);
        try {
            return new Task(obj.getJsonNumber("id").longValue(), obj.getString("name"),
                    Instant.parse(obj.getString("created")), Instant.parse(obj.getString("modified")),
                    obj.getJsonNumber("parent").longValue(), obj.getString("instruction"),
                    optionalObject(obj, "location", TaskParser::parseLocation),
                    GeometryParser.parse(obj.getJsonObject("geometries").toString()),
                    optionalObject(obj, "cooperativeWork", TaskParser::parseCooperativeWork),
                    TaskStatus.values()[obj.getInt("status")], optionalInstant(obj, "mappedOn"),
                    optionalLong(obj, "completedTimeSpent"), optionalLong(obj, "completedBy"), obj.getInt("priority"),
                    optionalLong(obj, "changesetId"), obj.getString("completionResponses", null),
                    optionalLong(obj, "bundleId"), obj.getBoolean("isBundlePrimary", false),
                    obj.getString("errorTags"));
        } catch (IllegalDataException e) {
            throw new JosmRuntimeException(e);
        }
    }

    /**
     * Parse a location
     *
     * @param jsonObject The object with the location
     * @return The parsed location
     */
    @Nonnull
    private static Point parseLocation(JsonObject jsonObject) {
        if (jsonObject.containsKey("type") && "Point".equals(jsonObject.getString("type"))) {
            double[] location = jsonObject.getJsonArray("coordinates").getValuesAs(JsonNumber.class).stream()
                    .mapToDouble(JsonNumber::doubleValue).toArray();
            return new Point(location[1], location[0]);
        } else {
            throw new IllegalArgumentException("Unknown type: " + jsonObject);
        }
    }

    /**
     * Parse a cooperative work object
     *
     * @param object The object to parse
     * @return The changes to make to OSM
     */
    @Nullable
    private static Object parseCooperativeWork(JsonObject object) {
        final var meta = object.getJsonObject("meta");
        final var metaType = meta.getInt("type", -1);
        final var metaVersion = meta.getInt("version", -1);
        if (metaType == 1 && metaVersion == 2) {
            final var modifies = new ArrayList<ElementUpdate>();
            final var operations = object.getJsonArray("operations");
            for (var operation : operations.getValuesAs(JsonObject.class)) {
                final var type = operation.getString("operationType");
                if ("modifyElement".equals(type)) {
                    final var data = operation.getJsonObject("data");
                    final var id = parseId(data.getJsonString("id").getString());
                    final var elemOperations = data.getJsonArray("operations");
                    for (var elemOp : elemOperations.getValuesAs(JsonObject.class)) {
                        final var elemType = elemOp.getString("operation");
                        if ("setTags".equals(elemType)) {
                            final var elemData = elemOp.getJsonObject("data").entrySet().stream()
                                    .filter(entry -> entry.getValue() instanceof JsonString).collect(Collectors.toMap(
                                            Map.Entry::getKey, entry -> ((JsonString) entry.getValue()).getString()));
                            modifies.add(new ElementUpdate(id.getUniqueId(), id.getType(), Integer.MIN_VALUE,
                                    new ElementTagChange(elemData, new String[0])));
                        } else if ("unsetTags".equals(elemType)) {
                            final var elemData = elemOp.getJsonArray("data").stream()
                                    .filter(JsonString.class::isInstance).map(JsonString.class::cast)
                                    .map(JsonString::getString).toArray(String[]::new);
                            modifies.add(new ElementUpdate(id.getUniqueId(), id.getType(), Integer.MIN_VALUE,
                                    new ElementTagChange(Collections.emptyMap(), elemData)));
                        } else {
                            throw new IllegalArgumentException(object.toString());
                        }
                    }
                } else {
                    throw new IllegalArgumentException(object.toString());
                }
            }
            return new OSMChange(new ElementCreate[0], modifies.toArray(new ElementUpdate[0]));
        } else if (metaType == 2 && metaVersion == 2) {
            final var file = object.getJsonObject("file");
            if ("xml".equals(file.getString("type", null)) && "osc".equals(file.getString("format", null))
                    && "base64".equals(file.getString("encoding"))) {
                final var dataString = Base64.getDecoder()
                        .decode(file.getString("content").getBytes(StandardCharsets.UTF_8));
                try {
                    return OsmChangeReader.parseDataSetAndNotes(new ByteArrayInputStream(dataString),
                            NullProgressMonitor.INSTANCE);
                } catch (IllegalDataException e) {
                    ExceptionUtil.explainException(e);
                }

            }
        }
        return null;
    }

    /**
     * Parse an id
     *
     * @param id The id to parse
     * @return The parsed primitive id
     */
    @Nonnull
    private static PrimitiveId parseId(@Nonnull String id) {
        String[] parts = id.split("/");
        final var osmId = Long.parseLong(parts[1]);
        return switch (parts[0]) {
        case "node" -> new SimplePrimitiveId(osmId, OsmPrimitiveType.NODE);
        case "way" -> new SimplePrimitiveId(osmId, OsmPrimitiveType.WAY);
        case "relation" -> new SimplePrimitiveId(osmId, OsmPrimitiveType.RELATION);
        default -> throw new IllegalArgumentException(id);
        };
    }
}
