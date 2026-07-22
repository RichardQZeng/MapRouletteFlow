// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.gui.task.list;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.plugins.maproulette.api.ChallengeAPI;
import org.openstreetmap.josm.plugins.maproulette.api.TaskAPI;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.api.model.TaskClusteredPoint;
import org.openstreetmap.josm.plugins.maproulette.data.IgnoreList;
import org.openstreetmap.josm.plugins.maproulette.data.TaskPrimitives;
import org.openstreetmap.josm.plugins.maproulette.gui.layer.MapRouletteClusteredPointLayer;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRoulettePreferences;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.plugins.maproulette.gui.task.current.CurrentTaskPanel;
import org.openstreetmap.josm.plugins.maproulette.util.ExceptionDialogUtil;
import org.openstreetmap.josm.plugins.maproulette.workflow.ChallengeInputParser;
import org.openstreetmap.josm.plugins.maproulette.workflow.ReservationLockRefresher;
import org.openstreetmap.josm.plugins.maproulette.workflow.TaskDownloadService;
import org.openstreetmap.josm.plugins.maproulette.workflow.TaskReservationService;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.Snapshot;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;
import org.openstreetmap.josm.tools.GBC;
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
    private final JLabel challengeName = new JLabel(tr("Challenge: None"));
    private final JLabel challengeDetails = new JLabel(" ");
    private final JRadioButton randomMode = new JRadioButton(tr("Random"));
    private final JRadioButton nearbyMode = new JRadioButton(tr("Nearby"));
    private final JLabel taskName = new JLabel(tr("No task reserved"));
    private final JLabel reservation = new JLabel(" ");
    private final JLabel taskDetails = new JLabel(" ");
    private final JLabel message = new JLabel(" ");
    private final StartDownloadAction startDownloadAction = new StartDownloadAction();
    private final RetryDownloadAction retryDownloadAction = new RetryDownloadAction();
    private final ReleaseAction releaseAction = new ReleaseAction();
    private final PropertyChangeListener workflowListener = this::workflowChanged;
    private volatile boolean destroyed;
    private boolean loading;
    private long requestGeneration;

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

        final var modePanel = new JPanel();
        modePanel.add(randomMode);
        modePanel.add(nearbyMode);
        final var inputPanel = new JPanel(new GridBagLayout());
        inputPanel.add(challengeInput, GBC.std().fill(GBC.HORIZONTAL));
        inputPanel.add(loadChallenge, GBC.eol());

        final var panel = new JPanel(new GridBagLayout());
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

        workflow.addPropertyChangeListener(workflowListener);
        createLayout(panel, true, Collections.emptyList());
        updateFromSnapshot(workflow.snapshot());
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
            final var result = reservations.reserve(challengeId, workflow.snapshot().nextMode(), null,
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
        updateChallenge(challenge);
        switch (result.status()) {
        case RESERVED -> {
            final var task = result.task();
            showTaskOnMap(task);
            startRefreshTimer(task.id());
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
        if (reserved == null || reserved.id() != taskId) {
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
        final var task = workflow.snapshot().reservedTask();
        if (task == null) {
            return;
        }
        loading = true;
        ++requestGeneration;
        message.setText(tr("Releasing task {0}...", task.id()));
        updateFromSnapshot(workflow.snapshot());
        MainApplication.worker.execute(() -> {
            Exception failure = null;
            try {
                TaskAPI.release(task.id());
            } catch (IOException exception) {
                failure = exception;
            }
            final var releaseFailure = failure;
            GuiHelper.runInEDT(() -> {
                loading = false;
                if (releaseFailure == null && isReserved(task.id())) {
                    workflow.releaseReservation();
                    clearTaskPreview();
                }
                if (destroyed) {
                    return;
                }
                if (releaseFailure == null) {
                    message.setText(tr("Reservation released."));
                } else {
                    message.setText(tr("Server release failed; the reservation remains active."));
                    ExceptionDialogUtil.explainException(releaseFailure);
                }
                updateFromSnapshot(workflow.snapshot());
            });
        });
    }

    private void startTaskDownload() {
        final var snapshot = workflow.snapshot();
        final var task = snapshot.reservedTask();
        if (task == null || snapshot.state() != State.RESERVED_PREVIEW
                && snapshot.state() != State.RECOVERABLE_ERROR) {
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

    private void finishTaskDownload(TaskDownloadService.Result result) {
        if (destroyed) {
            return;
        }
        loading = false;
        clearTaskPreview();
        MainApplication.getLayerManager().setActiveLayer(result.layer());
        final var primitives = TaskPrimitives.findPrimitives(result.layer().getDataSet(), result.primitiveIds());
        result.layer().getDataSet().setSelected(primitives);
        if (MapRouletteTaskPreference.isAutoCenter()) {
            TaskPreviewBounds.forTask(result.task()).ifPresent(bounds -> {
                if (bounds.isCollapsed()) {
                    MainApplication.getMap().mapView.zoomTo(bounds.getMax());
                } else {
                    MainApplication.getMap().mapView.zoomTo(bounds);
                }
            });
        }
        showCurrentTask(result.task());
        message.setText(tr("Task {0} is ready for editing. Selected {1} referenced OSM primitives.",
                result.task().id(), primitives.size()));
        updateFromSnapshot(workflow.snapshot());
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

    private static void showCurrentTask(Task task) {
        final var map = MainApplication.getMap();
        if (map == null) {
            return;
        }
        var panel = map.getToggleDialog(CurrentTaskPanel.class);
        if (panel == null) {
            panel = new CurrentTaskPanel();
            map.addToggleDialog(panel);
        }
        panel.refreshModel(task);
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
        if (MapRouletteTaskPreference.isAutoCenter()) {
            TaskPreviewBounds.forTask(task).ifPresent(bounds -> {
                if (bounds.isCollapsed()) {
                    MainApplication.getMap().mapView.zoomTo(bounds.getMax());
                } else {
                    MainApplication.getMap().mapView.zoomTo(bounds);
                }
            });
        }
    }

    private void clearTaskPreview() {
        MainApplication.getLayerManager().getLayersOfType(MapRouletteClusteredPointLayer.class)
                .forEach(layer -> layer.replaceTasks(Collections.emptyList()));
    }

    private void setNextMode(NextMode mode) {
        if (workflow.snapshot().nextMode() != mode) {
            workflow.setNextMode(mode);
        }
    }

    private void workflowChanged(PropertyChangeEvent event) {
        if (WorkflowController.SNAPSHOT_PROPERTY.equals(event.getPropertyName())) {
            updateFromSnapshot((Snapshot) event.getNewValue());
        }
    }

    private void updateFromSnapshot(Snapshot snapshot) {
        if (!SwingUtilities.isEventDispatchThread()) {
            GuiHelper.runInEDT(() -> updateFromSnapshot(snapshot));
            return;
        }
        randomMode.setSelected(snapshot.nextMode() == NextMode.RANDOM);
        nearbyMode.setSelected(snapshot.nextMode() == NextMode.NEARBY);
        updateChallenge(snapshot.activeChallenge());
        final var task = snapshot.reservedTask() != null ? snapshot.reservedTask() : snapshot.activeTask();
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
        loadChallenge.setEnabled(!loading && snapshot.state() == State.CHALLENGE_IDLE
                && workflow.canSelectChallenge());
        randomMode.setEnabled(!loading && snapshot.state() == State.CHALLENGE_IDLE);
        nearbyMode.setEnabled(!loading && snapshot.state() == State.CHALLENGE_IDLE);
        startDownloadAction.setEnabled(!loading && snapshot.state() == State.RESERVED_PREVIEW);
        retryDownloadAction.setEnabled(!loading && snapshot.state() == State.RECOVERABLE_ERROR
                && snapshot.reservedTask() != null);
        releaseAction.setEnabled(!loading && (snapshot.state() == State.RESERVED_PREVIEW
                || snapshot.state() == State.RECOVERABLE_ERROR && snapshot.reservedTask() != null));
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
        workflow.removePropertyChangeListener(workflowListener);
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
            startTaskDownload();
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
}
