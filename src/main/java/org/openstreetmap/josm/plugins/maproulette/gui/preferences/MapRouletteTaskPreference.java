// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.preferences;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.tools.GBC;

/**
 * MapRoulette task workflow preferences.
 */
public class MapRouletteTaskPreference implements SubPreferenceSetting {
    private static final StringProperty NEXT_MODE = new StringProperty("maproulette.task.next-mode", NextMode.RANDOM.name());
    private static final IntegerProperty POINT_RADIUS = new IntegerProperty("maproulette.task.point-radius", 100);
    private static final IntegerProperty GEOMETRY_PADDING = new IntegerProperty("maproulette.task.geometry-padding", 10);
    private static final BooleanProperty AUTO_CENTER = new BooleanProperty("maproulette.task.auto-center", true);
    private final JComboBox<NextMode> nextMode = new JComboBox<>(NextMode.values());
    private final JSpinner pointRadius = new JSpinner(
            new SpinnerNumberModel(POINT_RADIUS.get().intValue(), 1, 10_000, 10));
    private final JSpinner geometryPadding = new JSpinner(
            new SpinnerNumberModel(GEOMETRY_PADDING.get().intValue(), 0, 100, 1));
    private final JCheckBox autoCenter = new JCheckBox(tr("Automatically center reserved and active tasks"),
            AUTO_CENTER.get());
    private boolean guiInitialized;

    /** Supported next-task selection modes. */
    public enum NextMode {
        RANDOM,
        NEARBY;

        @Override
        public String toString() {
            return this == RANDOM ? tr("Random") : tr("Nearby");
        }
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        guiInitialized = true;
        final var panel = new JPanel(new GridBagLayout());
        nextMode.setSelectedItem(getNextMode());
        panel.add(new JLabel(tr("Default next task:")), GBC.std().anchor(GBC.LINE_START));
        panel.add(nextMode, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(new JLabel(tr("Point-task download radius (meters):")), GBC.std().anchor(GBC.LINE_START));
        panel.add(pointRadius, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(new JLabel(tr("Geometry download padding (percent):")), GBC.std().anchor(GBC.LINE_START));
        panel.add(geometryPadding, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(autoCenter, GBC.eol().fill(GBC.HORIZONTAL));
        getTabPreferenceSetting(gui).addSubTab(this, tr("Task Preferences"), panel,
                tr("MapRoulette Task Preferences"));
    }

    public static NextMode getNextMode() {
        try {
            return NextMode.valueOf(NEXT_MODE.get());
        } catch (IllegalArgumentException exception) {
            return NextMode.RANDOM;
        }
    }

    public static int getPointRadius() {
        return POINT_RADIUS.get();
    }

    public static int getGeometryPadding() {
        return GEOMETRY_PADDING.get();
    }

    public static boolean isAutoCenter() {
        return AUTO_CENTER.get();
    }

    @Override
    public TabPreferenceSetting getTabPreferenceSetting(PreferenceTabbedPane gui) {
        return gui.getSetting(MapRoulettePreferences.class);
    }

    @Override
    public boolean ok() {
        if (!guiInitialized) {
            return false;
        }
        NEXT_MODE.put(((NextMode) nextMode.getSelectedItem()).name());
        WorkflowController.getInstance().setNextMode((NextMode) nextMode.getSelectedItem());
        POINT_RADIUS.put((Integer) pointRadius.getValue());
        GEOMETRY_PADDING.put((Integer) geometryPadding.getValue());
        AUTO_CENTER.put(autoCenter.isSelected());
        return false;
    }

    @Override
    public boolean isExpert() {
        return true;
    }
}
