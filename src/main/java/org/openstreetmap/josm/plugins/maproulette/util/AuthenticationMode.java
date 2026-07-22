// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.util;

/**
 * Supported MapRoulette authentication sources.
 */
public enum AuthenticationMode {
    /** A key entered directly in JOSM. */
    DIRECT,
    /** A key read from the active OSM user's server preferences. */
    AUTOMATIC
}
