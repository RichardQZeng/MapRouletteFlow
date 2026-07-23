// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.model;

import java.util.List;

import org.openstreetmap.josm.plugins.maproulette.api.enums.Achievement;

/**
 * The non-sensitive portion of the authenticated MapRoulette account.
 *
 * @param id             the MapRoulette user id
 * @param osmId          the linked OpenStreetMap user id
 * @param osmDisplayName the linked OpenStreetMap display name
 * @param score          the current MapRoulette score
 * @param achievements   earned MapRoulette achievements
 */
public record AuthenticatedUser(long id, long osmId, String osmDisplayName, long score,
                                List<Achievement> achievements) {
    public AuthenticatedUser {
        achievements = List.copyOf(achievements);
    }

    public AuthenticatedUser(long id, long osmId, String osmDisplayName, long score) {
        this(id, osmId, osmDisplayName, score, List.of());
    }
}
