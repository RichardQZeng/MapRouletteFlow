// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.task.list;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

import io.github.richardqzeng.josm.maprouletteflow.api.enums.Achievement;
import io.github.richardqzeng.josm.maprouletteflow.api.model.UserProgress;
import org.openstreetmap.josm.tools.ImageProvider;

/** Compact earned-badge and progress summary for the narrow task sidebar. */
final class UserProgressStrip extends JPanel {
    private static final int BADGE_SIZE = 24;
    private static final int BADGE_OVERLAP = 10;
    private static final int MAX_VISIBLE_BADGES = 3;

    private final BadgeStack badges = new BadgeStack();
    private final JLabel primary = new JLabel(tr("MapRoulette Flow progress"));
    private final JLabel secondary = new JLabel(" ");
    private boolean hasProgress;

    UserProgressStrip() {
        super(new BorderLayout(8, 0));
        final var text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        primary.setFont(primary.getFont().deriveFont(java.awt.Font.BOLD));
        text.add(primary);
        text.add(secondary);
        add(badges, BorderLayout.LINE_START);
        add(text, BorderLayout.CENTER);
        final var separator = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, separator == null ? Color.GRAY : separator),
                BorderFactory.createEmptyBorder(2, 0, 6, 0)));
    }

    void reset() {
        hasProgress = false;
        primary.setText(tr("MapRoulette Flow progress"));
        secondary.setText(" ");
        badges.setAchievements(List.of());
        setToolTipText(null);
    }

    void showLoading() {
        if (!hasProgress) {
            primary.setText(tr("Loading MapRoulette progress..."));
            secondary.setText(" ");
        }
    }

    void showUnavailable() {
        if (!hasProgress) {
            primary.setText(tr("MapRoulette Flow progress unavailable"));
            secondary.setText(" ");
            badges.setAchievements(List.of());
        }
    }

    void setProgress(UserProgress progress) {
        hasProgress = true;
        final var numbers = NumberFormat.getIntegerInstance();
        primary.setText(tr("{0} pts | {1} tasks", numbers.format(progress.score()),
                numbers.format(progress.completedTasks())));
        secondary.setText(tr("Rank {0} all | {1} month", rank(progress.allTimeRank(), numbers),
                rank(progress.pastMonthRank(), numbers)));
        badges.setAchievements(progress.achievements());
        setToolTipText(tr("Earned {0} MapRoulette achievements", progress.achievements().size()));
    }

    private static String rank(Integer value, NumberFormat numbers) {
        return value == null ? tr("not ranked") : "#" + numbers.format(value);
    }

    private static String title(Achievement achievement) {
        return switch (achievement) {
        case MAPPED_ROADS -> tr("Road Mapper");
        case MAPPED_WATER -> tr("Water Mapper");
        case MAPPED_TRANSIT -> tr("Transit Mapper");
        case MAPPED_LANDUSE -> tr("Land-use Mapper");
        case MAPPED_BUILDINGS -> tr("Building Mapper");
        case MAPPED_POI -> tr("POI Mapper");
        case POINTS_100 -> tr("Point Earner: 100+");
        case POINTS_500 -> tr("Point Earner: 500+");
        case POINTS_1000 -> tr("Point Earner: 1,000+");
        case POINTS_5000 -> tr("Point Earner: 5,000+");
        case POINTS_10000 -> tr("Point Earner: 10,000+");
        case POINTS_50000 -> tr("Point Earner: 50,000+");
        case POINTS_100000 -> tr("Point Earner: 100,000+");
        case POINTS_500000 -> tr("Point Earner: 500,000+");
        case POINTS_1000000 -> tr("Point Earner: 1,000,000+");
        case FIXED_TASK -> tr("Task Fixer");
        case REVIEWED_TASK -> tr("Task Reviewer");
        case CREATED_CHALLENGE -> tr("Challenge Creator");
        case FIXED_FINAL_TASK -> tr("Challenge Closer");
        case FIXED_COOP_TASK -> tr("Cooperative Mapper");
        case CHALLENGE_COMPLETED -> tr("Finished Together");
        };
    }

    private static ImageIcon icon(Achievement achievement, int size) {
        return new ImageProvider("maprouletteflow/achievements/" + achievement.imageName()).setSize(size, size)
                .setOptional(true).get();
    }

    private static String pointOverlay(Achievement achievement) {
        return switch (achievement) {
        case POINTS_100 -> "100";
        case POINTS_500 -> "500";
        case POINTS_1000 -> "1K";
        case POINTS_5000 -> "5K";
        case POINTS_10000 -> "10K";
        case POINTS_50000 -> "50K";
        case POINTS_100000 -> "100K";
        case POINTS_500000 -> "500K";
        case POINTS_1000000 -> "1M";
        default -> null;
        };
    }

    private static void paintBadge(JComponent component, Graphics graphics, Achievement achievement, ImageIcon icon,
            int x, int y, int size) {
        if (icon != null) {
            icon.paintIcon(component, graphics, x, y);
        }
        final var overlay = pointOverlay(achievement);
        if (overlay == null) {
            return;
        }
        final var graphics2d = (Graphics2D) graphics.create();
        try {
            graphics2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final var fontSize = Math.max(7f, size / (overlay.length() > 3 ? 5f : 4f));
            graphics2d.setFont(component.getFont().deriveFont(java.awt.Font.BOLD, fontSize));
            final var metrics = graphics2d.getFontMetrics();
            final var textX = x + (size - metrics.stringWidth(overlay)) / 2;
            final var textY = y + (size + metrics.getAscent() - metrics.getDescent()) / 2;
            graphics2d.setColor(Color.BLACK);
            graphics2d.drawString(overlay, textX + 1, textY + 1);
            graphics2d.setColor(Color.WHITE);
            graphics2d.drawString(overlay, textX, textY);
        } finally {
            graphics2d.dispose();
        }
    }

    private static final class BadgeStack extends JPanel {
        private final Map<Achievement, ImageIcon> icons = new EnumMap<>(Achievement.class);
        private List<Achievement> achievements = List.of();

        BadgeStack() {
            setOpaque(false);
            setFocusable(true);
            setToolTipText(" ");
            getAccessibleContext().setAccessibleName(tr("MapRoulette Flow achievements"));
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!achievements.isEmpty()) {
                        showAllBadges();
                    }
                }
            });
            final var showAction = new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (!achievements.isEmpty()) {
                        showAllBadges();
                    }
                }
            };
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "showBadges");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "showBadges");
            getActionMap().put("showBadges", showAction);
            updateSize();
        }

        void setAchievements(List<Achievement> earned) {
            final var latestFirst = new ArrayList<>(earned);
            Collections.reverse(latestFirst);
            achievements = List.copyOf(latestFirst);
            achievements.forEach(value -> icons.computeIfAbsent(value, key -> icon(key, BADGE_SIZE)));
            updateSize();
            repaint();
        }

        private void updateSize() {
            final var visible = Math.min(MAX_VISIBLE_BADGES, achievements.size());
            final var width = visible == 0 ? BADGE_SIZE : BADGE_SIZE + (visible - 1) * BADGE_OVERLAP
                    + (achievements.size() > visible ? 24 : 0);
            final var size = new Dimension(width, BADGE_SIZE + 2);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            final var visible = Math.min(MAX_VISIBLE_BADGES, achievements.size());
            for (var index = visible - 1; index >= 0; index--) {
                final var badgeIcon = icons.get(achievements.get(index));
                if (badgeIcon != null) {
                    paintBadge(this, graphics, achievements.get(index), badgeIcon, index * BADGE_OVERLAP, 0,
                            BADGE_SIZE);
                }
            }
            if (achievements.size() > visible) {
                final var foreground = UIManager.getColor("Label.foreground");
                graphics.setColor(foreground == null ? Color.DARK_GRAY : foreground);
                graphics.drawString("+" + (achievements.size() - visible), BADGE_SIZE + visible * BADGE_OVERLAP - 9,
                        BADGE_SIZE / 2 + 5);
            } else if (visible == 0) {
                graphics.setColor(new Color(128, 128, 128, 90));
                graphics.drawOval(1, 1, BADGE_SIZE - 3, BADGE_SIZE - 3);
            }
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            if (achievements.isEmpty()) {
                return tr("No achievements earned yet");
            }
            final var visible = Math.min(MAX_VISIBLE_BADGES, achievements.size());
            final var visibleWidth = BADGE_SIZE + Math.max(0, visible - 1) * BADGE_OVERLAP;
            if (achievements.size() > visible && event.getX() >= visibleWidth) {
                return tr("{0} more achievements; click to view all", achievements.size() - visible);
            }
            final int index;
            if (event.getX() < BADGE_SIZE) {
                index = 0;
            } else {
                index = Math.min(achievements.size() - 1,
                        1 + (event.getX() - BADGE_SIZE) / BADGE_OVERLAP);
            }
            return title(achievements.get(index));
        }

        private void showAllBadges() {
            final var popup = new JPopupMenu();
            final var grid = new JPanel(new GridLayout(0, 4, 6, 6));
            grid.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            for (var achievement : achievements) {
                grid.add(new BadgeLabel(achievement));
            }
            popup.add(grid);
            popup.show(this, 0, getHeight());
        }
    }

    private static final class BadgeLabel extends JPanel {
        private final Achievement achievement;
        private final ImageIcon icon;

        BadgeLabel(Achievement achievement) {
            this.achievement = achievement;
            this.icon = icon(achievement, 48);
            setPreferredSize(new Dimension(48, 48));
            setToolTipText(title(achievement));
            getAccessibleContext().setAccessibleName(title(achievement));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            paintBadge(this, graphics, achievement, icon, 0, 0, 48);
        }
    }
}
