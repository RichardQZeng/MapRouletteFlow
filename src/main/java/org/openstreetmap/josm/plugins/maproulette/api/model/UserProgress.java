// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.model;

import java.util.List;

import org.openstreetmap.josm.plugins.maproulette.api.enums.Achievement;

/** Compact progress data displayed while mapping. Null ranks mean the user is not currently ranked. */
public record UserProgress(List<Achievement> achievements, long score, long completedTasks,
                           Integer allTimeRank, Integer pastMonthRank) {
    public UserProgress {
        achievements = List.copyOf(achievements);
    }
}
