// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.actions.downloadtasks;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.openstreetmap.josm.actions.downloadtasks.AbstractDownloadTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.gui.task.list.TaskListPanel;
import org.openstreetmap.josm.plugins.maproulette.workflow.ChallengeInputParser;

/** Open-location handler that enters the single-task challenge workflow. */
public class MapRouletteDownloadTask extends AbstractDownloadTask<Collection<Task>> {
    private static final String CHALLENGE_PATTERN =
            "https?://(?:www\\.)?maproulette\\.org/.*(?:challenge|challenges)/\\d+.*";
    private final long challenge;

    public MapRouletteDownloadTask() {
        this(-1, -1);
    }

    public MapRouletteDownloadTask(long challenge, long task) {
        this.challenge = challenge;
    }

    @Override
    public Future<?> download(DownloadParams settings, Bounds downloadArea, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public Future<?> loadUrl(DownloadParams settings, String url, ProgressMonitor progressMonitor) {
        final var input = challenge > 0 && (url == null || url.isBlank()) ? Long.toString(challenge) : url;
        if (ChallengeInputParser.parse(input).isEmpty()) {
            return null;
        }
        final var future = new FutureTask<Void>(() -> {
            TaskListPanel.loadChallengeInput(input);
            return null;
        });
        GuiHelper.runInEDT(future);
        return future;
    }

    @Override
    public void cancel() {
        // Loading is handed to the guarded workflow and has no cancellable batch operation.
    }

    @Override
    public String getConfirmationMessage(URL url) {
        if (ChallengeInputParser.parse(url.toExternalForm()).isEmpty()) {
            throw new IllegalArgumentException("Unsupported url: " + url);
        }
        return tr("Open MapRoulette challenge {0}", url.toExternalForm());
    }

    @Override
    public String[] getPatterns() {
        return new String[] { CHALLENGE_PATTERN };
    }

    @Override
    public String getTitle() {
        return tr("Open MapRoulette Challenge");
    }
}
