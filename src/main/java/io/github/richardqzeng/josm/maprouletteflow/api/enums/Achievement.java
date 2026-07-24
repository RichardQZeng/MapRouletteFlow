// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * User achievements
 */
public enum Achievement {
    /**
     * The user has mapped roads
     */
    MAPPED_ROADS(1, "roads"),
    /**
     * The user has mapped water
     */
    MAPPED_WATER(2, "water"),
    /**
     * The user has mapped transit data
     */
    MAPPED_TRANSIT(3, "transit"),
    /**
     * The user has mapped landuses
     */
    MAPPED_LANDUSE(4, "landuse"),
    /**
     * The user has mapped buildings
     */
    MAPPED_BUILDINGS(5, "buildings"),
    /**
     * The user has mapped points of interest
     */
    MAPPED_POI(6, "poi"),
    /**
     * The user has gained 100+ points
     */
    POINTS_100(7, "points100"),
    /**
     * The user has gained 500+ points
     */
    POINTS_500(8, "points500"),
    /**
     * The user has gained 1,000+ points
     */
    POINTS_1000(9, "points1000"),
    /**
     * The user has gained 5,000+ points
     */
    POINTS_5000(10, "points5000"),
    /**
     * The user has gained 10,000+ points
     */
    POINTS_10000(11, "points10000"),
    /**
     * The user has gained 50,000+ points
     */
    POINTS_50000(12, "points50000"),
    /**
     * The user has gained 100,000+ points
     */
    POINTS_100000(13, "points100k"),
    /**
     * The user has gained 500,000+ points
     */
    POINTS_500000(14, "points500k"),
    /**
     * The user has gained 1,000,000+ points
     */
    POINTS_1000000(15, "points1m"),
    /**
     * The user has fixed a task
     */
    FIXED_TASK(16, "rocket"),
    /**
     * The user has reviewed a task
     */
    REVIEWED_TASK(17, "nyan-cat"),
    /**
     * The user has created a challenge
     */
    CREATED_CHALLENGE(18, "godzilla"),
    /**
     * A user has fixed the final task of a challenge
     */
    FIXED_FINAL_TASK(19, "unicorn"),
    /**
     * The user has fixed a cooperative challenge task
     */
    FIXED_COOP_TASK(20, "high-five"),
    /**
     * The user has fixed a task in a challenge that has been completed
     */
    CHALLENGE_COMPLETED(21, "mountain-flag");

    private final int apiId;
    private final String imageName;

    Achievement(int apiId, String imageName) {
        this.apiId = apiId;
        this.imageName = imageName;
    }

    public int apiId() {
        return apiId;
    }

    public String imageName() {
        return imageName;
    }

    /** Unknown future achievement IDs are intentionally ignored. */
    public static Optional<Achievement> fromApiId(int apiId) {
        return Arrays.stream(values()).filter(value -> value.apiId == apiId).findFirst();
    }
}
