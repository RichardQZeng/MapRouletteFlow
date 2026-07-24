// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.model;

import jakarta.annotation.Nullable;

/**
 * A location
 *
 * @param latitude  The latitude
 * @param longitude The longitude
 * @param name      The name of the location
 */
public record Location(double latitude, double longitude, @Nullable String name) {
}
