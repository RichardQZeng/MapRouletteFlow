// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette;

import java.io.IOException;
import java.util.ArrayList;

import org.openstreetmap.josm.actions.UploadAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.download.OSMDownloadSource;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.maproulette.actions.downloadtasks.MapRouletteDownloadChallengeTask;
import org.openstreetmap.josm.plugins.maproulette.actions.downloadtasks.MapRouletteDownloadTask;
import org.openstreetmap.josm.plugins.maproulette.api.TaskAPI;
import org.openstreetmap.josm.plugins.maproulette.gui.download.MapRouletteDownloadSource;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRoulettePreferences;
import org.openstreetmap.josm.plugins.maproulette.gui.preferences.MapRouletteTaskPreference;
import org.openstreetmap.josm.plugins.maproulette.gui.task.list.TaskListPanel;
import org.openstreetmap.josm.plugins.maproulette.io.upload.EarlyUploadHook;
import org.openstreetmap.josm.plugins.maproulette.io.upload.LateUploadHook;
import org.openstreetmap.josm.plugins.maproulette.config.MapRouletteConfig;
import org.openstreetmap.josm.plugins.maproulette.util.AuthenticationManager;
import org.openstreetmap.josm.plugins.maproulette.util.ExceptionDialogUtil;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController;
import org.openstreetmap.josm.gui.util.GuiHelper;

/**
 * The POJO entry point
 */
public class MapRoulette extends Plugin {
    private final WorkflowController workflow = WorkflowController.getInstance();

    /**
     * Creates the plugin
     *
     * @param info the plugin information describing the plugin.
     */
    public MapRoulette(PluginInformation info) {
        super(info);
        this.getPreferenceSetting().ok();
        UploadAction.registerUploadHook(new EarlyUploadHook());
        UploadAction.registerUploadHook(new LateUploadHook(), true);
        OSMDownloadSource.addDownloadType(new MapRouletteDownloadSource());
        MainApplication.getMenu().openLocation.addDownloadTaskClass(MapRouletteDownloadTask.class);
        MainApplication.getMenu().openLocation.addDownloadTaskClass(MapRouletteDownloadChallengeTask.class);
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
            if (AuthenticationManager.isAuthenticated(MapRouletteConfig.getBaseUrl())) {
                workflow.connect();
            }
            newFrame.addToggleDialog(new TaskListPanel());
        }
    }

    private void cleanupWorkflow() {
        final var lockedTasks = workflow.getLockedTasks();
        workflow.shutdown();
        if (!lockedTasks.isEmpty()) {
            MainApplication.worker.execute(() -> {
                final var errors = new ArrayList<IOException>();
                for (var task : lockedTasks) {
                    try {
                        TaskAPI.release(task.id());
                    } catch (IOException e) {
                        errors.add(e);
                    }
                }
                GuiHelper.runInEDT(() -> errors.forEach(ExceptionDialogUtil::explainException));
            });
        }
    }
}
