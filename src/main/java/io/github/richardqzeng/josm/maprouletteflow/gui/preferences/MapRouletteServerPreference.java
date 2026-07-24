// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.gui.preferences;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.util.Arrays;
import java.util.Objects;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.JosmPasswordField;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import io.github.richardqzeng.josm.maprouletteflow.api.CurrentUserAPI;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;
import io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationMode;
import io.github.richardqzeng.josm.maprouletteflow.util.ExceptionDialogUtil;
import io.github.richardqzeng.josm.maprouletteflow.util.OsmPreferenceUtils;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Utils;

/**
 * Set the server and account preferences for MapRoulette.
 */
public class MapRouletteServerPreference implements SubPreferenceSetting {
    private static final StringProperty MAPROULETTE_URL = new StringProperty("maprouletteflow.api.url",
            "https://maproulette.org/api/v2");
    private final JosmComboBox<String> apiUrl = new JosmComboBox<>();
    private final JRadioButton automaticMode = new JRadioButton(tr("Automatic from OSM preferences"));
    private final JRadioButton directMode = new JRadioButton(tr("Direct API key"));
    private final JosmPasswordField directApiKey = new JosmPasswordField(32);
    private final JCheckBox rememberKey = new JCheckBox(tr("Remember key"));
    private final JosmTextField osmPreferenceName = new JosmTextField(32);
    private final JPanel modeCards = new JPanel(new CardLayout());
    private final JButton testConnection = new JButton(tr("Test Connection"));
    private final JLabel account = new JLabel(tr("Not authenticated"));
    private AuthenticatedUser testedAccount;
    private String testedBaseUrl;
    private String testedApiKey;
    private AuthenticationMode testedMode;
    private String loadedBaseUrl;
    private String loadedDirectKey;
    private String loadedOsmPreferenceName;
    private boolean loadedRememberKey;
    private boolean guiInitialized;

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        guiInitialized = true;
        final var panel = new JPanel(new GridBagLayout());
        loadedBaseUrl = MAPROULETTE_URL.get();
        apiUrl.addItem(loadedBaseUrl);
        if (MAPROULETTE_URL.isSet()) {
            apiUrl.addItem(MAPROULETTE_URL.getDefaultValue());
        }
        apiUrl.setSelectedItem(MAPROULETTE_URL.get());
        apiUrl.setEditable(true);

        final var modeGroup = new ButtonGroup();
        modeGroup.add(automaticMode);
        modeGroup.add(directMode);
        final var selectedMode = AuthenticationManager.getMode(loadedBaseUrl);
        automaticMode.setSelected(selectedMode == AuthenticationMode.AUTOMATIC);
        directMode.setSelected(selectedMode == AuthenticationMode.DIRECT);

        final var automaticPanel = new JPanel(new GridBagLayout());
        loadedOsmPreferenceName = AuthenticationManager.getOsmPreferenceName(loadedBaseUrl);
        osmPreferenceName.setText(loadedOsmPreferenceName);
        automaticPanel.add(new JLabel(tr("OSM preference name:")), GBC.std().anchor(GBC.LINE_START));
        automaticPanel.add(osmPreferenceName, GBC.eol().fill(GBC.HORIZONTAL));

        final var directPanel = new JPanel(new GridBagLayout());
        loadedDirectKey = AuthenticationManager.getDirectApiKey(loadedBaseUrl);
        if (loadedDirectKey != null) {
            directApiKey.setText(loadedDirectKey);
        }
        loadedRememberKey = AuthenticationManager.isDirectKeyRemembered(MAPROULETTE_URL.get());
        rememberKey.setSelected(loadedRememberKey);
        directPanel.add(new JLabel(tr("API key:")), GBC.std().anchor(GBC.LINE_START));
        directPanel.add(directApiKey, GBC.eol().fill(GBC.HORIZONTAL));
        directPanel.add(rememberKey, GBC.eol().insets(0, 4, 0, 0));
        directPanel.add(new JLabel(tr("Warning: remembered keys are stored in ordinary JOSM preferences.")),
                GBC.eol().insets(0, 0, 0, 4));

        modeCards.add(automaticPanel, AuthenticationMode.AUTOMATIC.name());
        modeCards.add(directPanel, AuthenticationMode.DIRECT.name());
        automaticMode.addActionListener(event -> showMode(AuthenticationMode.AUTOMATIC));
        directMode.addActionListener(event -> showMode(AuthenticationMode.DIRECT));
        showMode(selectedMode);
        testConnection.addActionListener(event -> testConnection());

        panel.add(new JLabel(tr("MapRoulette API URL:")), GBC.std().anchor(GBC.LINE_START));
        panel.add(apiUrl, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(new JLabel(tr("Authentication:")), GBC.std().anchor(GBC.LINE_START));
        panel.add(automaticMode, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(new JLabel(), GBC.std());
        panel.add(directMode, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(modeCards, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(testConnection, GBC.std().insets(0, 8, 8, 4));
        panel.add(account, GBC.eol().fill(GBC.HORIZONTAL).insets(0, 8, 0, 4));

        final var authenticated = AuthenticationManager.getAuthenticatedUser(MAPROULETTE_URL.get());
        if (authenticated != null) {
            showAccount(authenticated);
        }
        getTabPreferenceSetting(gui).addSubTab(this, tr("Server Settings"), panel, tr("MapRoulette Flow Server Settings"));
    }

    private AuthenticationMode selectedMode() {
        return directMode.isSelected() ? AuthenticationMode.DIRECT : AuthenticationMode.AUTOMATIC;
    }

    private void showMode(AuthenticationMode mode) {
        ((CardLayout) modeCards.getLayout()).show(modeCards, mode.name());
        clearTestResult();
    }

    private void testConnection() {
        final String baseUrl;
        try {
            baseUrl = AuthenticationManager.normalizeBaseUrl(apiUrl.getText());
        } catch (IllegalArgumentException exception) {
            account.setText(tr("Enter a MapRoulette API URL"));
            return;
        }
        final var mode = selectedMode();
        final var directCandidate = new String(directApiKey.getPassword()).strip();
        final var automaticPreference = Utils.isStripEmpty(osmPreferenceName.getText())
                ? AuthenticationManager.DEFAULT_OSM_PREFERENCE
                : osmPreferenceName.getText().strip();
        testConnection.setEnabled(false);
        account.setText(tr("Testing connection..."));
        new SwingWorker<AuthenticatedUser, Void>() {
            private String candidateKey;

            @Override
            protected AuthenticatedUser doInBackground() throws Exception {
                candidateKey = mode == AuthenticationMode.DIRECT ? directCandidate
                        : OsmPreferenceUtils.getMapRouletteApiKey(baseUrl, automaticPreference);
                return CurrentUserAPI.validate(baseUrl, candidateKey);
            }

            @Override
            protected void done() {
                testConnection.setEnabled(true);
                try {
                    testedAccount = get();
                    testedBaseUrl = baseUrl;
                    testedApiKey = candidateKey;
                    testedMode = mode;
                    showAccount(testedAccount);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    clearTestResult();
                } catch (Exception exception) {
                    clearTestResult();
                    account.setText(tr("Authentication failed"));
                    final var cause = exception.getCause();
                    ExceptionDialogUtil.explainException(cause instanceof Exception error ? error : exception);
                }
            }
        }.execute();
    }

    private void showAccount(AuthenticatedUser currentAccount) {
        account.setText(tr("MapRoulette ID: {0}; OSM: {1} ({2}); score: {3}", currentAccount.id(),
                currentAccount.osmDisplayName(), currentAccount.osmId(), currentAccount.score()));
    }

    private void clearTestResult() {
        testedAccount = null;
        testedBaseUrl = null;
        testedMode = null;
        testedApiKey = null;
        account.setText(tr("Not authenticated"));
    }

    @Override
    public TabPreferenceSetting getTabPreferenceSetting(PreferenceTabbedPane gui) {
        return gui.getSetting(MapRoulettePreferences.class);
    }

    @Override
    public boolean ok() {
        if (!guiInitialized) {
            MapRouletteConfig.setInstance(new MapRouletteConfig(MAPROULETTE_URL.get()));
            return false;
        }
        final var newApiUrl = Utils.isStripEmpty(apiUrl.getText()) ? MAPROULETTE_URL.get()
                : AuthenticationManager.normalizeBaseUrl(apiUrl.getText());
        final var mode = selectedMode();
        final var password = directApiKey.getPassword();
        final var directKey = new String(password).strip();
        final var normalizedDirectKey = directKey.isEmpty() ? null : directKey;
        Arrays.fill(password, '\0');
        final var serverChanged = !newApiUrl.equals(loadedBaseUrl);
        final var workflow = WorkflowController.getInstance();
        final var authenticationChanged = serverChanged || mode != AuthenticationManager.getMode(loadedBaseUrl)
                || !Objects.equals(osmPreferenceName.getText().strip(), loadedOsmPreferenceName)
                || !Objects.equals(normalizedDirectKey, loadedDirectKey) || rememberKey.isSelected() != loadedRememberKey;
        if (workflow.hasPendingWork() && authenticationChanged) {
            javax.swing.JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                    tr("Release or complete the current MapRoulette task before changing accounts or servers."),
                    tr("MapRoulette Flow task is active"), javax.swing.JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (newApiUrl.equals(MAPROULETTE_URL.getDefaultValue())) {
            MAPROULETTE_URL.remove();
        } else {
            MAPROULETTE_URL.put(newApiUrl);
        }
        final var testedNewServer = testedAccount != null && newApiUrl.equals(testedBaseUrl);
        if (!serverChanged || !Objects.equals(osmPreferenceName.getText().strip(), loadedOsmPreferenceName)
                || testedNewServer && testedMode == AuthenticationMode.AUTOMATIC) {
            AuthenticationManager.setOsmPreferenceName(newApiUrl, osmPreferenceName.getText());
        }
        if (!serverChanged || !Objects.equals(normalizedDirectKey, loadedDirectKey)
                || testedNewServer && testedMode == AuthenticationMode.DIRECT) {
            AuthenticationManager.setDirectApiKey(newApiUrl, directKey, rememberKey.isSelected());
        }
        AuthenticationManager.setMode(newApiUrl, mode);
        MapRouletteConfig.setInstance(new MapRouletteConfig(newApiUrl));

        final var candidateKey = mode == AuthenticationMode.DIRECT ? directKey : testedApiKey;
        if (testedAccount != null && newApiUrl.equals(testedBaseUrl) && mode == testedMode
                && testedApiKey.equals(candidateKey)) {
            if (workflow.hasPendingWork() && !workflow.isOwnedBy(newApiUrl, testedAccount)) {
                javax.swing.JOptionPane.showMessageDialog(MainApplication.getMainFrame(),
                        tr("The current task belongs to another MapRoulette account."),
                        tr("MapRoulette Flow task is active"), javax.swing.JOptionPane.WARNING_MESSAGE);
                return false;
            }
            AuthenticationManager.setAuthenticated(newApiUrl, mode, candidateKey, testedAccount);
            workflow.authenticatedAs(newApiUrl, testedAccount);
            if (workflow.snapshot().suspended()
                    && !io.github.richardqzeng.josm.maprouletteflow.MapRouletteFlowPlugin.isCleanupInProgress()) {
                workflow.resume();
            }
            if (MainApplication.getMap() != null) {
                io.github.richardqzeng.josm.maprouletteflow.MapRouletteFlowPlugin.restoreDraft(testedAccount);
            }
        } else if (!workflow.hasPendingWork()) {
            AuthenticationManager.clearActiveAuthentication();
        }
        return false;
    }

    @Override
    public boolean isExpert() {
        return true;
    }
}
