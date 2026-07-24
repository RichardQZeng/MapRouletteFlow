// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.data;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.gui.util.GuiHelper;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ChallengeExtra;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.api_caching.ChallengeCache;
import io.github.richardqzeng.josm.maprouletteflow.util.ExceptionDialogUtil;
import org.openstreetmap.josm.tools.Utils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A class for getting the primitives for a task
 */
public final class TaskPrimitives {
    /**
     * The pattern to use to check if the string only contains numbers
     */
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^\\d+$");

    /**
     * Hide the constructor
     */
    private TaskPrimitives() {
        // Hide constructor
    }

    /**
     * Get all the primitive ids for a task
     *
     * @param task The task to look through
     * @return The primitive ids from the task, if any
     */
    @Nonnull
    public static Collection<PrimitiveId> getPrimitiveIds(@Nullable Task task) {
        return getPrimitiveIdMap(task).keySet();
    }

    /** Resolve task primitive IDs without fetching challenge metadata. */
    @Nonnull
    public static Collection<PrimitiveId> getPrimitiveIds(@Nullable Task task, @Nullable String osmIdProperty) {
        if (task == null) {
            return Collections.emptyList();
        }
        if (!Utils.isStripEmpty(osmIdProperty)) {
            final var primitives = getPrimitiveIdMap(task, osmIdProperty);
            if (!primitives.isEmpty()) {
                return primitives.keySet();
            }
        }
        for (var defaultProperty : ChallengeExtra.DEFAULT_OSM_ID_PROPERTIES) {
            final var primitives = getPrimitiveIdMap(task, defaultProperty);
            if (!primitives.isEmpty()) {
                return primitives.keySet();
            }
        }
        return Collections.emptyList();
    }

    /** Find the referenced primitives that are actually present in a downloaded data set. */
    @Nonnull
    public static Collection<OsmPrimitive> findPrimitives(@Nonnull DataSet dataSet,
            @Nonnull Collection<? extends PrimitiveId> primitiveIds) {
        return primitiveIds.stream().map(dataSet::getPrimitiveById).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * Get the primitive id map for a task
     *
     * @param task The task to look for primitive mappings
     * @return The map of primitive id to primitive
     */
    @Nonnull
    public static Map<PrimitiveId, IPrimitive> getPrimitiveIdMap(@Nullable Task task) {
        if (task != null) {
            try {
                final var challenge = ChallengeCache.challenge(task.parentId());
                final var property = challenge.extra().osmIdProperty();
                if (!Utils.isStripEmpty(property)) {
                    final var primitives = getPrimitiveIdMap(task, property);
                    if (!primitives.isEmpty()) {
                        return primitives;
                    }
                }
            } catch (IOException ioException) {
                GuiHelper.runInEDT(() -> ExceptionDialogUtil.explainException(ioException));
            }
            for (var defaultProperty : ChallengeExtra.DEFAULT_OSM_ID_PROPERTIES) {
                final var primitives = getPrimitiveIdMap(task, defaultProperty);
                if (!primitives.isEmpty()) {
                    return primitives;
                }
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Get the primitive ids for a task given a property
     *
     * @param task     The task to get ids from
     * @param property The property to use
     * @return The primitive ids
     */
    @Nonnull
    private static Map<PrimitiveId, IPrimitive> getPrimitiveIdMap(@Nullable Task task, @Nullable String property) {
        if (task != null && property != null) {
            final var map = new HashMap<PrimitiveId, IPrimitive>();
            for (var primitive : task.geometries().allPrimitives()) {
                if (primitive.hasTag(property)) {
                    final var primitiveId = getPrimitiveId(primitive, primitive.get(property));
                    if (primitiveId != null) {
                        map.put(primitiveId, primitive);
                    }
                }
            }
            return Collections.unmodifiableMap(map);
        }
        return Collections.emptyMap();
    }

    /**
     * Get the primitive id from a formatted string
     *
     * @param id The id to parse
     * @return The parsed primitive id
     */
    @Nullable
    static PrimitiveId getPrimitiveId(@Nullable IPrimitive primitive, @Nullable String id) {
        if (id != null && primitive != null) {
            if (SimplePrimitiveId.ID_PATTERN.matcher(id).matches()) {
                return SimplePrimitiveId.fromString(id);
            } else if (INTEGER_PATTERN.matcher(id).matches()) {
                final var type = Optional.ofNullable(primitive.get("type"))
                        .or(() -> Optional.ofNullable(primitive.get("@type")))
                        .or(() -> Optional.ofNullable(primitive.get("@osm_type"))).orElse("");
                final OsmPrimitiveType osmType = switch (type) {
                case "n", "node" -> OsmPrimitiveType.NODE;
                case "w", "way" -> OsmPrimitiveType.WAY;
                case "r", "relation" -> OsmPrimitiveType.RELATION;
                default -> primitive.getType(); // Fall back to the primitive type sent in
                };
                final var osmId = Long.parseLong(id);
                return new SimplePrimitiveId(osmId, osmType);
            }
        }
        return null;
    }
}
