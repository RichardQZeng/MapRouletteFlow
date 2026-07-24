// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import java.util.Optional;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;

/** Computes the OSM download area for a full MapRoulette task. */
public final class TaskDownloadBounds {
    static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private TaskDownloadBounds() {
        // Utility class.
    }

    /**
     * Compute padded geometry bounds, falling back to a radius around the task location for point tasks.
     *
     * @param task full task returned by the start endpoint
     * @param geometryPaddingPercent padding to add on every side as a percentage of geometry width/height
     * @param pointRadiusMeters radius around the task location when no usable area geometry exists
     * @return a downloadable area, or empty when the task has neither area geometry nor a location
     */
    public static Optional<Bounds> forTask(Task task, int geometryPaddingPercent, int pointRadiusMeters) {
        if (task == null) {
            return Optional.empty();
        }
        if (geometryPaddingPercent < 0 || pointRadiusMeters <= 0) {
            throw new IllegalArgumentException("Task download padding must be nonnegative and radius must be positive");
        }

        Bounds geometryBounds = null;
        boolean hasNonPointGeometry = false;
        if (task.geometries() != null) {
            for (var primitive : task.geometries().allNonDeletedPrimitives()) {
                geometryBounds = extend(geometryBounds, primitive.getBBox());
                hasNonPointGeometry |= primitive.getType() != OsmPrimitiveType.NODE;
            }
        }
        if (hasNonPointGeometry && geometryBounds != null && !geometryBounds.isCollapsed()) {
            return Optional.of(pad(geometryBounds, geometryPaddingPercent));
        }
        if (task.location() == null) {
            return Optional.empty();
        }
        return Optional.of(around(task.location().lat(), task.location().lon(), pointRadiusMeters));
    }

    private static Bounds pad(Bounds bounds, int percent) {
        final var factor = percent / 100d;
        final var latitudePadding = bounds.getHeight() * factor;
        final var longitudePadding = bounds.getWidth() * factor;
        return bounded(bounds.getMinLat() - latitudePadding, bounds.getMinLon() - longitudePadding,
                bounds.getMaxLat() + latitudePadding, bounds.getMaxLon() + longitudePadding);
    }

    private static Bounds around(double latitude, double longitude, int radiusMeters) {
        final var angularDistance = radiusMeters / EARTH_RADIUS_METERS;
        final var latitudeDelta = Math.toDegrees(angularDistance);
        final var cosine = Math.cos(Math.toRadians(latitude));
        final var longitudeDelta = Math.abs(cosine) < 1e-12
                ? 180d
                : Math.min(180d, Math.toDegrees(angularDistance / Math.abs(cosine)));
        return bounded(latitude - latitudeDelta, longitude - longitudeDelta, latitude + latitudeDelta,
                longitude + longitudeDelta);
    }

    private static Bounds bounded(double minLat, double minLon, double maxLat, double maxLon) {
        return new Bounds(Math.max(-90d, minLat), Math.max(-180d, minLon), Math.min(90d, maxLat),
                Math.min(180d, maxLon));
    }

    private static Bounds extend(Bounds bounds, BBox box) {
        if (box == null || !box.isValid()) {
            return bounds;
        }
        if (bounds == null) {
            bounds = new Bounds(box.getBottomRight());
        }
        bounds.extend(box.getTopLeft());
        bounds.extend(box.getBottomRight());
        return bounds;
    }
}
