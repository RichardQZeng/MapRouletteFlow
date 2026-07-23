// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.list;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.maproulette.api.enums.Achievement;
import org.openstreetmap.josm.plugins.maproulette.api.model.UserProgress;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.ImageProvider;

@BasicPreferences
class UserProgressStripTest {
    @Test
    void rendersCompactStatsAndUnrankedState() {
        final var strip = new UserProgressStrip();
        strip.setProgress(new UserProgress(List.of(Achievement.MAPPED_WATER, Achievement.POINTS_10000),
                16_065, 3_377, 440, null));

        final var text = descendants(strip).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .map(JLabel::getText).toList();
        assertTrue(text.stream().anyMatch(value -> value.contains("16,065") && value.contains("3,377")));
        assertTrue(text.stream().anyMatch(value -> value.contains("#440") && value.contains("not ranked")));
        assertTrue(strip.getToolTipText().contains("2"));
    }

    @Test
    void officialBadgeArtworkIsPackaged() {
        for (var achievement : Achievement.values()) {
            assertNotNull(new ImageProvider("maproulette/achievements/" + achievement.imageName()).setSize(24, 24)
                    .setOptional(true).get(), achievement.name());
        }
    }

    private static Stream<Component> descendants(Container container) {
        return Arrays.stream(container.getComponents())
                .flatMap(component -> component instanceof Container child
                        ? Stream.concat(Stream.of(component), descendants(child))
                        : Stream.of(component));
    }
}
