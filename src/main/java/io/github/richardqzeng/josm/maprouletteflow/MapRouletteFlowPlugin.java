// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.openstreetmap.josm.actions.UploadAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import io.github.richardqzeng.josm.maprouletteflow.api.CurrentUserAPI;
import io.github.richardqzeng.josm.maprouletteflow.api.TaskAPI;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRoulettePreferences;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference;
import io.github.richardqzeng.josm.maprouletteflow.gui.task.MapRouletteShortcuts;
import io.github.richardqzeng.josm.maprouletteflow.gui.task.list.TaskListPanel;
import io.github.richardqzeng.josm.maprouletteflow.io.upload.EarlyUploadHook;
import io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig;
import io.github.richardqzeng.josm.maprouletteflow.util.AuthenticationManager;
import io.github.richardqzeng.josm.maprouletteflow.util.ExceptionDialogUtil;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowDraftStore;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionResult;
import io.github.richardqzeng.josm.maprouletteflow.workflow.TaskEditTracker;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;

/**
 * MapRoulette Flow plugin entry point.
 *
 * @author Richard Zeng
 */
public class MapRouletteFlowPlugin extends Plugin implements Destroyable {
    private static final AtomicBoolean RECOVERY_IN_PROGRESS = new AtomicBoolean();
    private static final AtomicBoolean CLEANUP_IN_PROGRESS = new AtomicBoolean();
    private final WorkflowController workflow = WorkflowController.getInstance();
    private final EarlyUploadHook earlyUploadHook = new EarlyUploadHook();

    /**
     * Creates the plugin
     *
     * @param info the plugin information describing the plugin.
     */
    public MapRouletteFlowPlugin(PluginInformation info) {
        super(info);
        MapRouletteShortcuts.registerAll();
        TaskEditTracker.getInstance().start();
        this.getPreferenceSetting().ok();
        UploadAction.registerUploadHook(earlyUploadHook);
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new MapRoulettePreferences();
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        super.mapFrameInitialized(oldFrame, newFrame);
        if (oldFrame != null) {
            cleanupWorkflow();
        }
        if (newFrame != null) {
            workflow.setNextMode(MapRouletteTaskPreference.getNextMode());
            newFrame.addToggleDialog(new TaskListPanel());
            authenticateAtStartup(newFrame);
        }
    }

    private void authenticateAtStartup(MapFrame mapFrame) {
        final var baseUrl = AuthenticationManager.normalizeBaseUrl(MapRouletteConfig.getBaseUrl());
        final var existingAccount = AuthenticationManager.getAuthenticatedUser(baseUrl);
        if (existingAccount != null) {
            workflow.authenticatedAs(baseUrl, existingAccount);
            restoreDraft(existingAccount);
            return;
        }
        final var mode = AuthenticationManager.getMode(baseUrl);
        MainApplication.worker.execute(() -> {
            try {
                CurrentUserAPI.authenticateConfigured(baseUrl);
                GuiHelper.runInEDT(() -> {
                    if (MainApplication.getMap() != mapFrame || MapRouletteConfig.getInstance() == null
                            || !baseUrl.equals(AuthenticationManager.normalizeBaseUrl(MapRouletteConfig.getBaseUrl()))
                            || mode != AuthenticationManager.getMode(baseUrl)) {
                        return;
                    }
                    final var account = AuthenticationManager.getAuthenticatedUser(baseUrl);
                    if (account != null) {
                        workflow.authenticatedAs(baseUrl, account);
                        restoreDraft(account);
                    }
                });
            } catch (IOException | RuntimeException exception) {
                Logging.info("MapRoulette startup authentication was unavailable: " + exception.getMessage());
            }
        });
    }

    public static void restoreDraft(io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser account) {
        final var stored = WorkflowDraftStore.load();
        final var baseUrl = AuthenticationManager.normalizeBaseUrl(MapRouletteConfig.getBaseUrl());
        final var currentAccount = AuthenticationManager.getAuthenticatedUser(baseUrl);
        if (stored == null || account == null || currentAccount == null
                || account.id() != currentAccount.id() || account.osmId() != currentAccount.osmId()
                || !AuthenticationManager.normalizeBaseUrl(stored.server()).equals(baseUrl)
                || stored.mapRouletteUserId() != account.id() || stored.osmUserId() != account.osmId()
                || WorkflowController.getInstance().hasPendingWork() || !RECOVERY_IN_PROGRESS.compareAndSet(false, true)) {
            return;
        }
        final String apiKey;
        try {
            apiKey = AuthenticationManager.getApiKey(baseUrl);
        } catch (IOException exception) {
            RECOVERY_IN_PROGRESS.set(false);
            GuiHelper.runInEDT(() -> ExceptionDialogUtil.explainException(exception));
            return;
        }
        MainApplication.worker.execute(() -> {
            var lockAcquired = false;
            var restored = false;
            try {
                final var challenge = io.github.richardqzeng.josm.maprouletteflow.api.ChallengeAPI
                        .challenge(stored.challengeId());
                final var layer = GuiHelper.runInEDTAndWaitAndReturn(() -> findRecoveryLayer(stored.editLayerName()));
                if (!stored.statusCommitted() && stored.result() == CompletionResult.FIXED && layer == null) {
                    return;
                }
                final var task = stored.statusCommitted() ? TaskAPI.get(stored.taskId())
                        : TaskAPI.start(stored.taskId(), baseUrl, apiKey);
                lockAcquired = !stored.statusCommitted();
                restored = GuiHelper.runInEDTAndWaitAndReturn(() -> {
                    final var authenticated = AuthenticationManager.getAuthenticatedUser(baseUrl);
                    if (MainApplication.getMap() == null
                            || !baseUrl.equals(AuthenticationManager.normalizeBaseUrl(MapRouletteConfig.getBaseUrl()))
                            || authenticated == null || authenticated.id() != account.id()
                            || authenticated.osmId() != account.osmId()) {
                        return false;
                    }
                    WorkflowController.getInstance().restoreDraft(stored, challenge, task, layer);
                    return true;
                });
            } catch (IOException | RuntimeException exception) {
                GuiHelper.runInEDT(() -> ExceptionDialogUtil.explainException(exception));
            } finally {
                try {
                    if (lockAcquired && !restored) {
                        try {
                            TaskAPI.release(stored.taskId(), baseUrl, apiKey);
                        } catch (IOException | RuntimeException exception) {
                            GuiHelper.runInEDT(() -> ExceptionDialogUtil.explainException(exception));
                        }
                    }
                } finally {
                    RECOVERY_IN_PROGRESS.set(false);
                }
            }
        });
    }

    private static OsmDataLayer findRecoveryLayer(String layerName) {
        if (MainApplication.getMap() == null || layerName == null) {
            return null;
        }
        final var matchingLayers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).stream()
                .filter(layer -> java.util.Objects.equals(layer.getName(), layerName)).toList();
        return matchingLayers.size() == 1 ? matchingLayers.get(0) : null;
    }

    private void cleanupWorkflow() {
        final var lockedTasks = workflow.getLockedTasks();
        final var owner = workflow.snapshot().accountOwner();
        final var canRelease = owner != null && workflow.isCurrentOwnerAuthenticated();
        workflow.suspend();
        if (!canRelease && !lockedTasks.isEmpty()) {
            return;
        }
        if (lockedTasks.isEmpty()) {
            workflow.shutdown();
            TaskEditTracker.getInstance().discard();
        } else {
            final String releaseKey;
            try {
                releaseKey = AuthenticationManager.getApiKey(owner.baseUrl());
            } catch (IOException exception) {
                GuiHelper.runInEDT(() -> ExceptionDialogUtil.explainException(exception));
                return;
            }
            CLEANUP_IN_PROGRESS.set(true);
            MainApplication.worker.execute(() -> {
                final var errors = new ArrayList<Exception>();
                for (var task : lockedTasks) {
                    try {
                        TaskAPI.release(task.id(), owner.baseUrl(), releaseKey);
                    } catch (IOException | RuntimeException e) {
                        errors.add(e);
                    }
                }
                GuiHelper.runInEDT(() -> {
                    CLEANUP_IN_PROGRESS.set(false);
                    if (errors.isEmpty()) {
                        workflow.shutdown();
                        TaskEditTracker.getInstance().discard();
                        if (MainApplication.getMap() != null) {
                            final var account = AuthenticationManager
                                    .getAuthenticatedUser(MapRouletteConfig.getBaseUrl());
                            if (account != null) {
                                workflow.authenticatedAs(MapRouletteConfig.getBaseUrl(), account);
                                restoreDraft(account);
                            }
                        }
                    } else {
                        workflow.resume();
                        TaskEditTracker.getInstance().start();
                        errors.forEach(ExceptionDialogUtil::explainException);
                    }
                });
            });
        }
    }

    public static boolean isCleanupInProgress() {
        return CLEANUP_IN_PROGRESS.get();
    }

    @Override
    public void destroy() {
        UploadAction.unregisterUploadHook(earlyUploadHook);
        io.github.richardqzeng.josm.maprouletteflow.io.upload.FixedUploadCoordinator.getInstance().cleanup();
        TaskEditTracker.getInstance().pause();
        cleanupWorkflow();
    }
}
