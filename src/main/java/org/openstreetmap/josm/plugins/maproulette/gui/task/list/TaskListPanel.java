// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.list;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.maproulette.api.ChallengeAPI;
import org.openstreetmap.josm.plugins.maproulette.api.CurrentUserAPI;
import org.openstreetmap.josm.plugins.maproulette.api.TaskAPI;
import org.openstreetmap.josm.plugins.maproulette.api.UserProgressAPI;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.api.model.TaskClusteredPoint;
import org.openstreetmap.josm.plugins.maproulette.data.IgnoreList;
import org.openstreetmap.josm.plugins.maproulette.data.TaskPrimitives;
import org.openstreetmap.josm.plugins.maproulette.gui.layer.MapRouletteClusteredPointLayer;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRoulettePreferences;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.plugins.maproulette.gui.task.MapRouletteShortcuts;
import org.openstreetmap.josm.plugins.maproulette.gui.task.current.CurrentTaskPanel;
import org.openstreetmap.josm.plugins.maproulette.util.ExceptionDialogUtil;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig;
import org.openstreetmap.josm.plugins.maproulette.workflow.ChallengeInputParser;
import org.openstreetmap.josm.plugins.maproulette.workflow.ReservationLockRefresher;
import org.openstreetmap.josm.plugins.maproulette.workflow.TaskDownloadService;
import org.openstreetmap.josm.plugins.maproulette.workflow.TaskEditTracker;
import org.openstreetmap.josm.plugins.maproulette.workflow.TaskReservationService;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.Snapshot;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/** Main one-challenge, one-reserved-task MapRoulette workflow panel. */
public final class TaskListPanel extends ToggleDialog {
    @Serial
    private static final long serialVersionUID = -8983504332024481559L;

    private final WorkflowController workflow = WorkflowController.getInstance();
    private final TaskReservationService reservations = new TaskReservationService(workflow);
    private final TaskDownloadService taskDownloads = new TaskDownloadService(workflow);
    private final JosmTextField challengeInput = new JosmTextField();
    private final JButton loadChallenge = new JButton(tr("Load Challenge"));
    private final JButton clearChallenge = new JButton(tr("Clear"));
    private final JLabel challengeName = new JLabel(tr("Challenge: None"));
    private final JLabel challengeDetails = new JLabel(" ");
    private final JRadioButton randomMode = new JRadioButton(tr("Random"));
    private final JRadioButton nearbyMode = new JRadioButton(tr("Nearby"));
    private final JLabel taskName = new JLabel(tr("No task reserved"));
    private final JLabel reservation = new JLabel(" ");
    private final JLabel taskDetails = new JLabel(" ");
    private final JLabel message = new JLabel(" ");
    private final UserProgressStrip userProgress = new UserProgressStrip();
    private final StartDownloadAction startDownloadAction = new StartDownloadAction();
    private final RetryDownloadAction retryDownloadAction = new RetryDownloadAction();
    private final ReleaseAction releaseAction = new ReleaseAction();
    private final CurrentTaskPanel currentTaskPanel = new CurrentTaskPanel();
    private final ShowInstructionsAction showInstructionsAction = new ShowInstructionsAction();
    private final JLabel currentTaskLabel = new JLabel(tr("Current task:"));
    private final JPanel currentTaskActions = new JPanel(new GridLayout(0, 2, 4, 4));
    private final PropertyChangeListener workflowListener = this::workflowChanged;
    private final Runnable authenticationListener = () -> GuiHelper.runInEDT(this::authenticationChanged);
    private JDialog instructionsDialog;
    private volatile boolean destroyed;
    private boolean loading;
    private long requestGeneration;
    private long progressGeneration;
    private ProgressIdentity progressIdentity;

    public TaskListPanel() {
        super(tr("MapRoulette Tasks"), "user_no_image.png", tr("MapRoulette challenge workflow"),
                Shortcut.registerShortcut("maproulette:task_window", tr("MapRoulette challenge workflow"),
                        KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                200, false, MapRoulettePreferences.class);

        final var modeGroup = new ButtonGroup();
        modeGroup.add(randomMode);
        modeGroup.add(nearbyMode);
        randomMode.addActionListener(event -> setNextMode(NextMode.RANDOM));
        nearbyMode.addActionListener(event -> setNextMode(NextMode.NEARBY));
        loadChallenge.addActionListener(event -> requestChallenge(challengeInput.getText()));
        challengeInput.addActionListener(event -> requestChallenge(challengeInput.getText()));
        clearChallenge.setToolTipText(tr("Forget the remembered challenge input"));
        clearChallenge.addActionListener(event -> clearRememberedChallenge());
        if (MapRouletteConfig.getInstance() != null) {
            RecentChallengePreference.get(MapRouletteConfig.getBaseUrl())
                    .ifPresent(id -> challengeInput.setText(Long.toString(id)));
        }

        final var modePanel = new JPanel();
        modePanel.add(randomMode);
        modePanel.add(nearbyMode);
        final var inputPanel = new JPanel(new GridBagLayout());
        inputPanel.add(challengeInput, GBC.std().fill(GBC.HORIZONTAL));
        inputPanel.add(loadChallenge, GBC.std());
        inputPanel.add(clearChallenge, GBC.eol());

        final var panel = new JPanel(new GridBagLayout());
        panel.add(userProgress, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(new JLabel(tr("Challenge ID or URL:")), GBC.eol().anchor(GBC.LINE_START));
        panel.add(inputPanel, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(challengeName, GBC.eol().anchor(GBC.LINE_START).insets(0, 8, 0, 0));
        panel.add(challengeDetails, GBC.eol().anchor(GBC.LINE_START));
        panel.add(new JLabel(tr("Next task:")), GBC.std().anchor(GBC.LINE_START));
        panel.add(modePanel, GBC.eol().anchor(GBC.LINE_START));
        panel.add(taskName, GBC.eol().anchor(GBC.LINE_START).insets(0, 8, 0, 0));
        panel.add(reservation, GBC.eol().anchor(GBC.LINE_START));
        panel.add(taskDetails, GBC.eol().anchor(GBC.LINE_START));
        panel.add(message, GBC.eol().anchor(GBC.LINE_START).fill(GBC.HORIZONTAL));
        final var taskActions = new JPanel();
        taskActions.add(new JButton(startDownloadAction));
        taskActions.add(new JButton(retryDownloadAction));
        taskActions.add(new JButton(releaseAction));
        panel.add(taskActions, GBC.eol().anchor(GBC.LINE_START));
        for (var action : currentTaskPanel.actions()) {
            currentTaskActions.add(new JButton(action));
        }
        currentTaskActions.add(new JButton(showInstructionsAction));
        panel.add(currentTaskLabel, GBC.eol().anchor(GBC.LINE_START).insets(0, 8, 0, 0));
        panel.add(currentTaskActions, GBC.eol().fill(GBC.HORIZONTAL));

        workflow.addPropertyChangeListener(workflowListener);
        AuthenticationManager.addAuthenticationListener(authenticationListener);
        createLayout(panel, true, Collections.emptyList());
        final var snapshot = workflow.snapshot();
        updateFromSnapshot(snapshot);
        if (!snapshot.suspended() && snapshot.reservedTask() != null) {
            presentReservedPreview(snapshot);
        }
        if (snapshot.state() != State.DISCONNECTED) {
            refreshProgress(false);
        }
    }

    /** Route an external MapRoulette challenge URL through the same workflow as panel input. */
    public static void loadChallengeInput(String input) {
        GuiHelper.runInEDT(() -> {
            if (MainApplication.getMap() == null) {
                return;
            }
            var panel = MainApplication.getMap().getToggleDialog(TaskListPanel.class);
            if (panel == null) {
                panel = new TaskListPanel();
                MainApplication.getMap().addToggleDialog(panel);
            }
            panel.challengeInput.setText(input);
            panel.requestChallenge(input);
        });
    }

    private void requestChallenge(String input) {
        if (!SwingUtilities.isEventDispatchThread()) {
            GuiHelper.runInEDT(() -> requestChallenge(input));
            return;
        }
        final var parsed = ChallengeInputParser.parse(input);
        if (parsed.isEmpty()) {
            message.setText(tr("Enter a positive challenge ID or a supported maproulette.org challenge URL."));
            return;
        }
        if (!workflow.canSelectChallenge()) {
            message.setText(tr("Release or complete the current MapRoulette task before loading another challenge."));
            return;
        }
        final var challengeId = parsed.getAsLong();
        if (IgnoreList.isChallengeIgnored(challengeId)) {
            final var choice = JOptionPane.showConfirmDialog(MainApplication.getMainFrame(),
                    tr("Challenge {0} is excluded. Remove it from Exclusions and continue?", challengeId),
                    tr("Excluded MapRoulette challenge"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                message.setText(tr("Challenge {0} remains excluded; no task was requested.", challengeId));
                return;
            }
            IgnoreList.unignoreChallenge(challengeId);
        }

        loading = true;
        final var generation = ++requestGeneration;
        message.setText(tr("Loading challenge {0}...", challengeId));
        updateEnabledState();
        MainApplication.worker.execute(() -> loadAndReserve(challengeId, generation));
    }

    private void loadAndReserve(long challengeId, long generation) {
        try {
            final var challenge = ChallengeAPI.challenge(challengeId);
            workflow.selectChallenge(challenge);
            workflow.setNextMode(MapRouletteTaskPreference.getNextMode(challengeId));
            final var snapshot = workflow.snapshot();
            final var result = reservations.reserve(challengeId, snapshot.nextMode(), snapshot.completedTaskId(),
                    IgnoreList::isTaskIgnored);
            GuiHelper.runInEDT(() -> finishReservation(challenge, result, generation));
        } catch (Exception exception) {
            GuiHelper.runInEDT(() -> finishFailure(exception, generation));
        }
    }

    private void finishReservation(Challenge challenge, TaskReservationService.Result result, long generation) {
        if (destroyed || generation != requestGeneration) {
            return;
        }
        loading = false;
        challengeInput.setText(Long.toString(challenge.id()));
        RecentChallengePreference.remember(MapRouletteConfig.getBaseUrl(), challenge.id());
        updateChallenge(challenge);
        switch (result.status()) {
        case RESERVED -> {
            message.setText(tr("One task is reserved. No OSM data has been downloaded."));
        }
        case EMPTY -> {
            clearTaskPreview();
            message.setText(tr("No available task was returned for this challenge."));
        }
        case EXCLUDED_RETRIES_EXHAUSTED -> {
            clearTaskPreview();
            message.setText(tr("Excluded tasks were returned repeatedly; stopped after {0} retries.",
                    TaskReservationService.MAX_EXCLUDED_RETRIES));
        }
        case REQUEST_FAILED -> message.setText(tr("The next task could not be reserved."));
        }
        updateFromSnapshot(workflow.snapshot());
    }

    private void finishFailure(Exception exception, long generation) {
        if (destroyed || generation != requestGeneration) {
            return;
        }
        loading = false;
        message.setText(tr("Could not load or reserve a task: {0}", exception.getMessage()));
        updateFromSnapshot(workflow.snapshot());
        ExceptionDialogUtil.explainException(exception);
    }

    private void startRefreshTimer(long taskId) {
        ReservationLockRefresher.start(workflow,
                event -> MainApplication.worker.execute(() -> refreshReservation(taskId)));
    }

    private void refreshReservation(long taskId) {
        final var reserved = workflow.snapshot().reservedTask();
        if (reserved == null || reserved.id() != taskId || !isWorkflowAuthenticated()) {
            return;
        }
        try {
            TaskAPI.refreshLock(taskId);
            GuiHelper.runInEDT(() -> {
                if (isReserved(taskId)) {
                    message.setText(tr("Reservation refreshed."));
                }
            });
        } catch (IOException exception) {
            GuiHelper.runInEDT(() -> {
                if (isReserved(taskId)) {
                    message.setText(tr("Reservation refresh failed; use Release if you cannot continue."));
                    ExceptionDialogUtil.explainException(exception);
                }
            });
        }
    }

    private boolean isReserved(long taskId) {
        final var task = workflow.snapshot().reservedTask();
        return task != null && task.id() == taskId;
    }

    private void releaseReservation() {
        final var snapshot = workflow.snapshot();
        final var task = snapshot.reservedTask() != null ? snapshot.reservedTask() : snapshot.activeTask();
        if (task == null) {
            return;
        }
        if (!isWorkflowAuthenticated()) {
            message.setText(tr("Authenticate as the account that reserved this task before releasing it."));
            updateEnabledState();
            return;
        }
        final var active = snapshot.activeTask() != null;
        final var hasEdits = snapshot.editLayer() != null && snapshot.editLayer().getDataSet().requiresUploadToServer();
        final var editLayerName = snapshot.editLayer() == null ? null : snapshot.editLayer().getName();
        if (active) {
            final var warning = hasEdits
                    ? tr("Release task {0} without recording a result? Your OSM edits will remain in layer ''{1}'', "
                            + "but they will no longer be associated with this MapRoulette task.", task.id(),
                            snapshot.editLayer().getName())
                    : tr("Release task {0} without recording a result? The task will return to the available pool.",
                            task.id());
            if (JOptionPane.showConfirmDialog(MainApplication.getMainFrame(), warning, tr("Release MapRoulette task"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
        }
        final String baseUrl;
        final String apiKey;
        try {
            baseUrl = snapshot.accountOwner().baseUrl();
            apiKey = AuthenticationManager.getApiKey(baseUrl);
        } catch (IOException | RuntimeException exception) {
            message.setText(tr("Could not read the credential that owns this task."));
            ExceptionDialogUtil.explainException(exception);
            return;
        }
        loading = true;
        final var generation = ++requestGeneration;
        message.setText(tr("Releasing task {0}...", task.id()));
        workflow.beginRelease(null);
        updateFromSnapshot(workflow.snapshot());
        try {
            MainApplication.worker.execute(() -> {
                Exception failure = null;
                try {
                    TaskAPI.release(task.id(), baseUrl, apiKey);
                } catch (IOException | RuntimeException exception) {
                    failure = exception;
                }
                final var releaseFailure = failure;
                GuiHelper.runInEDT(() -> finishRelease(task.id(), generation, active, hasEdits, editLayerName,
                        releaseFailure));
            });
        } catch (RuntimeException exception) {
            finishRelease(task.id(), generation, active, hasEdits, editLayerName, exception);
        }
    }

    private void finishRelease(long taskId, long generation, boolean active, boolean hasEdits, String editLayerName,
            Exception failure) {
        if (destroyed || generation != requestGeneration || workflow.state() != State.RELEASING) {
            return;
        }
        final var retained = workflow.snapshot().reservedTask() != null ? workflow.snapshot().reservedTask()
                : workflow.snapshot().activeTask();
        if (retained == null || retained.id() != taskId) {
            return;
        }
        loading = false;
        if (failure == null) {
            workflow.releaseSucceeded();
            clearTaskPreview();
            message.setText(active && hasEdits
                    ? tr("Task released. Local OSM edits remain in layer ''{0}''.", editLayerName)
                    : tr("Task released."));
        } else {
            workflow.releaseFailed();
            final var restored = workflow.snapshot().reservedTask();
            if (restored != null) {
                startRefreshTimer(restored.id());
            }
            message.setText(tr("Server release failed; the task remains active."));
            ExceptionDialogUtil.explainException(failure);
        }
        updateFromSnapshot(workflow.snapshot());
    }

    private void startTaskDownload() {
        final var snapshot = workflow.snapshot();
        final var task = snapshot.reservedTask();
        if (task == null || snapshot.state() != State.RESERVED_PREVIEW
                && snapshot.state() != State.RECOVERABLE_ERROR) {
            return;
        }
        if (!isWorkflowAuthenticated()) {
            message.setText(tr("Authenticate as the account that reserved this task before starting it."));
            updateEnabledState();
            return;
        }
        loading = true;
        message.setText(snapshot.state() == State.RECOVERABLE_ERROR
                ? tr("Retrying OSM download for task {0}...", task.id())
                : tr("Starting task {0} and downloading OSM data...", task.id()));
        updateEnabledState();
        taskDownloads.start(task, MapRouletteTaskPreference.getGeometryPadding(),
                MapRouletteTaskPreference.getPointRadius(), new TaskDownloadListener());
    }

    private void redownloadActiveTask() {
        final var snapshot = workflow.snapshot();
        final var task = snapshot.activeTask();
        if (task == null || snapshot.state() != State.ACTIVE_EDITING && !workflow.canRetryActiveRedownload()) {
            return;
        }
        if (!isWorkflowAuthenticated()) {
            message.setText(tr("Authenticate as the account that reserved this task before re-downloading it."));
            updateEnabledState();
            return;
        }
        if (snapshot.state() == State.ACTIVE_EDITING
                && JOptionPane.showConfirmDialog(MainApplication.getMainFrame(),
                        tr("Re-download current OSM data for task {0}? JOSM will merge server updates into layer ''{1}'' "
                                + "and preserve your local edits.", task.id(), snapshot.editLayer().getName()),
                        tr("Re-download MapRoulette task"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        loading = true;
        message.setText(tr("Re-downloading OSM data for task {0}...", task.id()));
        updateEnabledState();
        taskDownloads.redownloadActive(MapRouletteTaskPreference.getGeometryPadding(),
                MapRouletteTaskPreference.getPointRadius(), new ActiveRedownloadListener());
    }

    private void finishTaskDownload(TaskDownloadService.Result result) {
        if (destroyed) {
            return;
        }
        loading = false;
        clearTaskPreview();
        MainApplication.getLayerManager().setActiveLayer(result.layer());
        final var primitives = TaskPrimitives.findPrimitives(result.layer().getDataSet(), result.primitiveIds());
        result.layer().getDataSet().setSelected(primitives);
        CurrentTaskZoom.zoom(result.task());
        message.setText(tr("Task {0} is ready for editing. Selected {1} referenced OSM primitives.",
                result.task().id(), primitives.size()));
        updateFromSnapshot(workflow.snapshot());
    }

    private void finishActiveRedownload(TaskDownloadService.Result result) {
        if (destroyed) {
            return;
        }
        loading = false;
        MainApplication.getLayerManager().setActiveLayer(result.layer());
        final var primitives = TaskPrimitives.findPrimitives(result.layer().getDataSet(), result.primitiveIds());
        result.layer().getDataSet().setSelected(primitives);
        TaskEditTracker.getInstance().redownloaded(result.task(), result.layer());
        CurrentTaskZoom.zoom(result.task());
        message.setText(tr("Task {0} data was refreshed. Local edits were preserved.", result.task().id()));
        updateFromSnapshot(workflow.snapshot());
    }

    private void finishActiveRedownloadCancellation(Task task) {
        if (!destroyed) {
            loading = false;
            message.setText(tr("Re-download canceled. Task {0} remains active.", task.id()));
            updateFromSnapshot(workflow.snapshot());
        }
    }

    private void finishActiveRedownloadFailure(Task task, Exception exception) {
        if (!destroyed) {
            loading = false;
            message.setText(tr("Re-download failed. Task {0} remains active; use Retry or Release.", task.id()));
            updateFromSnapshot(workflow.snapshot());
            ExceptionDialogUtil.explainException(exception);
        }
    }

    private void finishTaskDownloadCancellation(Task task) {
        if (destroyed) {
            return;
        }
        loading = false;
        startRefreshTimer(task.id());
        message.setText(tr("OSM download was canceled. Task {0} remains reserved; use Retry or Release.", task.id()));
        updateFromSnapshot(workflow.snapshot());
    }

    private void finishTaskDownloadFailure(Task task, Exception exception) {
        if (destroyed) {
            return;
        }
        loading = false;
        startRefreshTimer(task.id());
        message.setText(tr("OSM download failed. Task {0} remains reserved; use Retry or Release.", task.id()));
        updateFromSnapshot(workflow.snapshot());
        ExceptionDialogUtil.explainException(exception);
    }

    private void showTaskOnMap(Task task) {
        final var layers = MainApplication.getLayerManager().getLayersOfType(MapRouletteClusteredPointLayer.class);
        final MapRouletteClusteredPointLayer layer;
        if (layers.isEmpty()) {
            layer = new MapRouletteClusteredPointLayer(TaskPreviewBounds.forTask(task).orElse(null), List.of(task));
            MainApplication.getLayerManager().addLayer(layer);
            MainApplication.getMap().mapView.addMouseListener(layer);
        } else {
            layer = layers.get(0);
            layer.replaceTasks(List.of(task));
            layers.stream().skip(1).forEach(other -> other.replaceTasks(Collections.emptyList()));
        }
        GuiHelper.runInEDT(() -> CurrentTaskZoom.zoom(task));
    }

    private void clearTaskPreview() {
        MainApplication.getLayerManager().getLayersOfType(MapRouletteClusteredPointLayer.class)
                .forEach(layer -> layer.replaceTasks(Collections.emptyList()));
    }

    private void setNextMode(NextMode mode) {
        final var snapshot = workflow.snapshot();
        if (snapshot.nextMode() != mode) {
            if (snapshot.activeChallenge() == null) {
                MapRouletteTaskPreference.setNextMode(mode);
            } else {
                MapRouletteTaskPreference.setNextMode(snapshot.activeChallenge().id(), mode);
            }
        }
    }

    private void clearRememberedChallenge() {
        challengeInput.setText("");
        if (MapRouletteConfig.getInstance() != null) {
            RecentChallengePreference.clear(MapRouletteConfig.getBaseUrl());
        }
    }

    private void workflowChanged(PropertyChangeEvent event) {
        if (WorkflowController.SNAPSHOT_PROPERTY.equals(event.getPropertyName())) {
            final var oldSnapshot = (Snapshot) event.getOldValue();
            final var newSnapshot = (Snapshot) event.getNewValue();
            final var oldReserved = oldSnapshot.reservedTask();
            final var newReserved = newSnapshot.reservedTask();
            if (!newSnapshot.suspended() && newReserved != null
                    && (oldSnapshot.suspended() || oldReserved == null || oldReserved.id() != newReserved.id())) {
                presentReservedPreview(newSnapshot);
            } else if (oldSnapshot.state() == State.SUBMITTING && newSnapshot.state() == State.CHALLENGE_IDLE) {
                showAutomaticReservationOutcome(newSnapshot);
            }
            updateFromSnapshot(newSnapshot);
            if (!Objects.equals(oldSnapshot.accountOwner(), newSnapshot.accountOwner())) {
                refreshProgress(false);
            } else if (oldSnapshot.state() == State.DISCONNECTED && newSnapshot.state() == State.CHALLENGE_IDLE) {
                refreshProgress(false);
            } else if (oldSnapshot.state() == State.SUBMITTING && newSnapshot.state() != State.SUBMITTING
                    && newSnapshot.completedTaskId() != null) {
                refreshProgress(true);
            }
        }
    }

    private void refreshProgress(boolean refreshAccount) {
        if (destroyed || MapRouletteConfig.getInstance() == null) {
            return;
        }
        final var baseUrl = AuthenticationManager.normalizeBaseUrl(MapRouletteConfig.getBaseUrl());
        final var current = AuthenticationManager.getAuthenticatedUser(baseUrl);
        if (current == null) {
            progressIdentity = null;
            userProgress.reset();
            return;
        }
        final var identity = new ProgressIdentity(baseUrl, current.id(), current.osmId());
        if (!identity.equals(progressIdentity)) {
            progressIdentity = identity;
            userProgress.reset();
        }
        final var expectedUserId = current.id();
        final var generation = ++progressGeneration;
        userProgress.showLoading();
        MainApplication.worker.execute(() -> {
            try {
                final var account = refreshAccount ? CurrentUserAPI.authenticateConfigured(baseUrl) : current;
                final var progress = UserProgressAPI.fetch(baseUrl, account);
                GuiHelper.runInEDT(() -> {
                    final var authenticated = MapRouletteConfig.getInstance() == null ? null
                            : AuthenticationManager.getAuthenticatedUser(baseUrl);
                    if (!destroyed && generation == progressGeneration && authenticated != null
                            && identity.equals(progressIdentity) && authenticated.id() == expectedUserId
                            && authenticated.osmId() == identity.osmUserId()
                            && baseUrl.equals(AuthenticationManager.normalizeBaseUrl(MapRouletteConfig.getBaseUrl()))) {
                        userProgress.setProgress(progress);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                Logging.info("MapRoulette progress was unavailable: " + exception.getMessage());
                GuiHelper.runInEDT(() -> {
                    if (!destroyed && generation == progressGeneration) {
                        userProgress.showUnavailable();
                        updateEnabledState();
                    }
                });
            }
        });
    }

    private void authenticationChanged() {
        if (destroyed) {
            return;
        }
        if (MapRouletteConfig.getInstance() == null) {
            ++progressGeneration;
            progressIdentity = null;
            userProgress.reset();
            return;
        }
        final var baseUrl = AuthenticationManager.normalizeBaseUrl(MapRouletteConfig.getBaseUrl());
        final var account = AuthenticationManager.getAuthenticatedUser(baseUrl);
        if (account == null) {
            ++progressGeneration;
            progressIdentity = null;
            userProgress.reset();
        } else {
            final var identity = new ProgressIdentity(baseUrl, account.id(), account.osmId());
            if (!identity.equals(progressIdentity)) {
                ++progressGeneration;
                progressIdentity = identity;
                userProgress.reset();
                if (workflow.isOwnedBy(baseUrl, account)) {
                    refreshProgress(false);
                }
            }
        }
        updateEnabledState();
    }

    private void presentReservedPreview(Snapshot snapshot) {
        final var task = snapshot.reservedTask();
        if (task == null) {
            return;
        }
        if (MainApplication.getMap() != null) {
            showTaskOnMap(task);
            startRefreshTimer(task.id());
        }
        if (snapshot.completedTaskId() == null) {
            message.setText(tr("One task is reserved. No OSM data has been downloaded."));
        } else {
            message.setText(tr("Task {0} was completed. Task {1} is reserved. No OSM data has been downloaded; "
                    + "choose Start & Download to begin editing.", snapshot.completedTaskId(), task.id()));
        }
    }

    private void showAutomaticReservationOutcome(Snapshot snapshot) {
        clearTaskPreview();
        final var completed = snapshot.completedTaskId();
        if (snapshot.reservationStatus() == TaskReservationService.Status.EMPTY) {
            message.setText(tr("Task {0} was completed. No more tasks are available for this challenge.", completed));
        } else if (snapshot.reservationStatus() == TaskReservationService.Status.EXCLUDED_RETRIES_EXHAUSTED) {
            message.setText(tr("Task {0} was completed, but excluded tasks were returned repeatedly. "
                    + "Use Load Challenge to try again or review Exclusions.", completed));
        } else if (snapshot.reservationStatus() == TaskReservationService.Status.REQUEST_FAILED) {
            message.setText(tr("Task {0} was completed, but the next task could not be reserved. "
                    + "Use Load Challenge to try again.", completed));
        }
    }

    private void updateFromSnapshot(Snapshot snapshot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            GuiHelper.runInEDTAndWaitAndReturn(() -> {
                updateFromSnapshot(snapshot);
                return null;
            });
            return;
        }
        randomMode.setSelected(snapshot.nextMode() == NextMode.RANDOM);
        nearbyMode.setSelected(snapshot.nextMode() == NextMode.NEARBY);
        updateChallenge(snapshot.activeChallenge());
        final var task = snapshot.reservedTask() != null ? snapshot.reservedTask() : snapshot.activeTask();
        currentTaskPanel.refreshModel(snapshot.activeTask());
        currentTaskLabel.setVisible(snapshot.activeTask() != null);
        currentTaskActions.setVisible(snapshot.activeTask() != null);
        if (snapshot.activeTask() == null && instructionsDialog != null) {
            instructionsDialog.setVisible(false);
        }
        if (task == null) {
            taskName.setText(tr("No task reserved"));
            reservation.setText(" ");
            taskDetails.setText(" ");
        } else {
            taskName.setText(tr("Task: {0} (#{1})", task.name(), task.id()));
            reservation.setText(snapshot.reservedTask() == null ? tr("Active") : tr("Reserved"));
            taskDetails.setText(tr("Status: {0} | Priority: {1} | Geometry objects: {2}", task.status(),
                    task.priority(), task.geometries().allNonDeletedPrimitives().size()));
        }
        updateEnabledState();
    }

    private void updateChallenge(Challenge challenge) {
        if (challenge == null) {
            challengeName.setText(tr("Challenge: None"));
            challengeDetails.setText(" ");
        } else {
            challengeName.setText(tr("Challenge: {0} (#{1})", challenge.name(), challenge.id()));
            final var remaining = challenge.tasksRemaining() == null ? tr("unknown") : challenge.tasksRemaining();
            challengeDetails.setText(tr("Tasks remaining: {0}", remaining));
        }
    }

    private void updateEnabledState() {
        final var snapshot = workflow.snapshot();
        final var authenticated = !snapshot.suspended() && isWorkflowAuthenticated();
        loadChallenge.setEnabled(authenticated && !loading && snapshot.state() == State.CHALLENGE_IDLE
                && workflow.canSelectChallenge());
        clearChallenge.setEnabled(!loading);
        randomMode.setEnabled(authenticated && !loading && snapshot.state() == State.CHALLENGE_IDLE);
        nearbyMode.setEnabled(authenticated && !loading && snapshot.state() == State.CHALLENGE_IDLE);
        startDownloadAction.setEnabled(authenticated && !loading && snapshot.state() == State.RESERVED_PREVIEW);
        retryDownloadAction.putValue(AbstractAction.NAME,
                snapshot.state() == State.ACTIVE_EDITING ? tr("Re-download")
                        : workflow.canRetryActiveRedownload() ? tr("Retry Re-download") : tr("Retry Download"));
        retryDownloadAction.setEnabled(authenticated && !loading && (snapshot.state() == State.ACTIVE_EDITING
                || workflow.canRetryInitialDownload() || workflow.canRetryActiveRedownload()));
        releaseAction.setEnabled(authenticated && !loading && workflow.canReleaseTask());
        showInstructionsAction.updateEnabledState();
    }

    private boolean isWorkflowAuthenticated() {
        if (MapRouletteConfig.getInstance() == null) {
            return false;
        }
        final var account = AuthenticationManager.getAuthenticatedUser(MapRouletteConfig.getBaseUrl());
        return account != null && workflow.isOwnedBy(MapRouletteConfig.getBaseUrl(), account);
    }

    /** Current workflow selection used by the preview layer and later completion actions. */
    public Collection<TaskClusteredPoint> getSelected() {
        final var snapshot = workflow.snapshot();
        final var task = snapshot.reservedTask() != null ? snapshot.reservedTask() : snapshot.activeTask();
        return task == null ? Collections.emptyList() : List.of(task);
    }

    @Override
    public void destroy() {
        destroyed = true;
        ++requestGeneration;
        ++progressGeneration;
        workflow.removePropertyChangeListener(workflowListener);
        AuthenticationManager.removeAuthenticationListener(authenticationListener);
        if (instructionsDialog != null) {
            instructionsDialog.dispose();
        }
        currentTaskPanel.destroy();
        showInstructionsAction.destroy();
        super.destroy();
    }

    private final class StartDownloadAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 1L;

        StartDownloadAction() {
            super(tr("Start & Download"));
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            startTaskDownload();
        }
    }

    private final class RetryDownloadAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 1L;

        RetryDownloadAction() {
            super(tr("Retry"));
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            if (workflow.state() == State.ACTIVE_EDITING || workflow.canRetryActiveRedownload()) {
                redownloadActiveTask();
            } else {
                startTaskDownload();
            }
        }
    }

    private final class TaskDownloadListener implements TaskDownloadService.Listener {
        @Override
        public void completed(TaskDownloadService.Result result) {
            finishTaskDownload(result);
        }

        @Override
        public void canceled(Task reservedTask) {
            finishTaskDownloadCancellation(reservedTask);
        }

        @Override
        public void failed(Task reservedTask, Exception exception) {
            finishTaskDownloadFailure(reservedTask, exception);
        }
    }

    private final class ActiveRedownloadListener implements TaskDownloadService.Listener {
        @Override
        public void completed(TaskDownloadService.Result result) {
            finishActiveRedownload(result);
        }

        @Override
        public void canceled(Task task) {
            finishActiveRedownloadCancellation(task);
        }

        @Override
        public void failed(Task task, Exception exception) {
            finishActiveRedownloadFailure(task, exception);
        }
    }

    private final class ReleaseAction extends AbstractAction {
        @Serial
        private static final long serialVersionUID = 1L;

        ReleaseAction() {
            super(tr("Release"));
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            releaseReservation();
        }
    }

    private final class ShowInstructionsAction extends JosmAction {
        @Serial
        private static final long serialVersionUID = 1L;

        ShowInstructionsAction() {
            super(tr("Instructions..."), null, tr("Show task instructions"),
                    MapRouletteShortcuts.instructions(),
                    false, true);
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            final var task = workflow.snapshot().activeTask();
            if (task == null) {
                return;
            }
            if (instructionsDialog == null) {
                instructionsDialog = new JDialog(MainApplication.getMainFrame(), false);
                instructionsDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
                instructionsDialog.setContentPane(currentTaskPanel);
                instructionsDialog.setSize(600, 500);
                instructionsDialog.setLocationRelativeTo(MainApplication.getMainFrame());
            }
            instructionsDialog.setTitle(tr("MapRoulette task {0}: Instructions", task.id()));
            instructionsDialog.setVisible(true);
            instructionsDialog.toFront();
        }

        @Override
        public void updateEnabledState() {
            final var snapshot = workflow.snapshot();
            setEnabled(!snapshot.suspended() && snapshot.activeTask() != null);
        }
    }

    private record ProgressIdentity(String baseUrl, long mapRouletteUserId, long osmUserId) {
    }
}
