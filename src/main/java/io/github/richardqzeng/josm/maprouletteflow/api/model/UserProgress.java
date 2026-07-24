// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.model;

import java.util.List;

import io.github.richardqzeng.josm.maprouletteflow.api.enums.Achievement;

/** Compact progress data displayed while mapping. Null ranks mean the user is not currently ranked. */
public record UserProgress(List<Achievement> achievements, long score, long completedTasks,
                           Integer allTimeRank, Integer pastMonthRank) {
    public UserProgress {
        achievements = List.copyOf(achievements);
    }
}
