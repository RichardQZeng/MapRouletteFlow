// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.model;

/** User totals and ranking returned by the MapRoulette leaderboard. */
public record LeaderboardEntry(long userId, long score, long completedTasks, int rank) {
}
