// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.model;

/**
 * The non-sensitive portion of the authenticated MapRoulette account.
 *
 * @param id             the MapRoulette user id
 * @param osmId          the linked OpenStreetMap user id
 * @param osmDisplayName the linked OpenStreetMap display name
 * @param score          the current MapRoulette score
 */
public record AuthenticatedUser(long id, long osmId, String osmDisplayName, long score) {
}
