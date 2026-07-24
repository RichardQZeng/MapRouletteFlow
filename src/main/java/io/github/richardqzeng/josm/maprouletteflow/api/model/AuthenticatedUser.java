// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.model;

import java.util.List;

import io.github.richardqzeng.josm.maprouletteflow.api.enums.Achievement;

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
