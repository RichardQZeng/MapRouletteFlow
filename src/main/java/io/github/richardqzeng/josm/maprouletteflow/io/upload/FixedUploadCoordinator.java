// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.io.upload;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.actions.UploadAction;
import org.openstreetmap.josm.data.APIDataSet;
import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.data.osm.ChangesetCache;
import org.openstreetmap.josm.data.osm.ChangesetCacheListener;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.io.UploadDialog;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Logging;
import io.github.richardqzeng.josm.maprouletteflow.util.ExceptionDialogUtil;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionDraft;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionResult;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionSubmissionController;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController.State;

/** Correlates a normal JOSM upload with one Fixed MapRoulette completion draft. */
public final class FixedUploadCoordinator implements ChangesetCacheListener {
    @FunctionalInterface
    interface UploadLauncher {
        Runnable launch(OsmDataLayer layer, APIDataSet data, Runnable accepted, Runnable canceled);
    }

    @FunctionalInterface
    interface ListenerRegistrar {
        Runnable register(ChangesetCacheListener listener);
    }

    private static final FixedUploadCoordinator INSTANCE = new FixedUploadCoordinator();
    private final WorkflowController workflow;
    private final CompletionSubmissionController submissions;
    private final UploadLauncher uploadLauncher;
    private final ListenerRegistrar listenerRegistrar;
    private final Executor executor;
    private final Consumer<String> warningHandler;
    private final Consumer<Exception> errorHandler;
    private OsmDataLayer layer;
    private volatile long taskId;
    private Set<Object> uploadPrimitives = Collections.emptySet();
    private volatile Changeset correlatedChangeset;
    private Runnable cacheCleanup;
    private Runnable uploadCleanup;
    private Timer pollTimer;
    private boolean metadataArmed;
    private boolean metadataPrepared;
    private boolean preflightComplete;
    private boolean submissionStarted;
    private boolean uploadStarted;
    private int cleanWithoutCorrelationPolls;

    private FixedUploadCoordinator() {
        this(WorkflowController.getInstance(),
                new CompletionSubmissionController(WorkflowController.getInstance(),
                        new io.github.richardqzeng.josm.maprouletteflow.workflow.ApiTaskCompletionGateway()),
                FixedUploadCoordinator::launchUpload, FixedUploadCoordinator::registerListener,
                command -> MainApplication.worker.execute(command),
                message -> SwingUtilities.invokeLater(() -> showWarning(message)),
                exception -> SwingUtilities.invokeLater(() -> ExceptionDialogUtil.explainException(exception)));
    }

    FixedUploadCoordinator(WorkflowController workflow, CompletionSubmissionController submissions,
            UploadLauncher uploadLauncher, ListenerRegistrar listenerRegistrar, Executor executor,
            Consumer<String> warningHandler, Consumer<Exception> errorHandler) {
        this.workflow = workflow;
        this.submissions = submissions;
        this.uploadLauncher = uploadLauncher;
        this.listenerRegistrar = listenerRegistrar;
        this.executor = executor;
        this.warningHandler = warningHandler;
        this.errorHandler = errorHandler;
    }

    public static FixedUploadCoordinator getInstance() {
        return INSTANCE;
    }

    public boolean hasPendingEdits() {
        final var editLayer = workflow.snapshot().editLayer();
        return editLayer != null && !new APIDataSet(editLayer.getDataSet()).isEmpty();
    }

    /** Start observing before opening JOSM's normal upload dialog. */
    public void start(CompletionDraft draft) {
        if (draft.result() != CompletionResult.FIXED) {
            throw new IllegalArgumentException("Only Fixed completion requires an OSM upload");
        }
        if (workflow.state() == State.WAITING_FOR_UPLOAD) {
            workflow.cancelUpload();
        }
        final var snapshot = workflow.snapshot();
        if (snapshot.state() != State.COMPLETION_DRAFT || snapshot.completionDraft() == null
                || snapshot.completionDraft().task().id() != draft.task().id() || snapshot.editLayer() == null) {
            throw new IllegalStateException("The Fixed draft has no captured edit layer");
        }
        final var data = new APIDataSet(snapshot.editLayer().getDataSet());
        if (data.isEmpty()) {
            throw new IllegalStateException("The captured edit layer has no changes to upload");
        }
        cleanup();
        layer = snapshot.editLayer();
        taskId = draft.task().id();
        uploadPrimitives = identitySet(data);
        correlatedChangeset = null;
        metadataArmed = false;
        metadataPrepared = false;
        preflightComplete = false;
        submissionStarted = false;
        uploadStarted = false;
        cleanWithoutCorrelationPolls = 0;
        cacheCleanup = listenerRegistrar.register(this);
        pollTimer = new Timer(250, event -> evaluate());
        pollTimer.start();
        workflow.waitForUpload(this::cleanup);
        try {
            uploadCleanup = uploadLauncher.launch(layer, data, () -> preflightComplete = true, this::cancel);
        } catch (RuntimeException exception) {
            cancel();
            throw exception;
        }
    }

    public boolean canRetry() {
        return workflow.state() == State.WAITING_FOR_UPLOAD && layer != null && !layer.isUploadInProgress()
                && !UploadDialog.getUploadDialog().isVisible();
    }

    /** Match the intended preflight set without ever vetoing or tagging a competing upload. */
    public boolean checkUpload(APIDataSet data) {
        if (workflow.state() != State.WAITING_FOR_UPLOAD || taskId == 0 || preflightComplete) {
            return true;
        }
        if (uploadPrimitives.equals(identitySet(data))) {
            metadataArmed = !metadataPrepared;
        } else {
            warnAndCancel(tr("Another OSM upload started; the MapRoulette Fixed draft was preserved"));
        }
        return true;
    }

    public Long consumeMetadataTaskId() {
        if (!metadataArmed || workflow.state() != State.WAITING_FOR_UPLOAD) {
            return null;
        }
        metadataArmed = false;
        metadataPrepared = true;
        return taskId;
    }

    @Override
    public void changesetCacheUpdated(org.openstreetmap.josm.data.osm.ChangesetCacheEvent event) {
        java.util.stream.Stream.concat(event.getAddedChangesets().stream(), event.getUpdatedChangesets().stream())
                .filter(this::matches).findFirst().ifPresent(changeset -> {
                    correlatedChangeset = changeset;
                    SwingUtilities.invokeLater(this::evaluate);
                });
    }

    private boolean matches(Changeset changeset) {
        if (changeset.getId() <= 0) {
            return false;
        }
        final var expected = Long.toString(taskId);
        return changeset.getKeys().entrySet().stream()
                .filter(entry -> entry.getKey().equals("maproulette:tasks")
                        || entry.getKey().startsWith("maproulette:tasks:"))
                .flatMap(entry -> java.util.Arrays.stream(entry.getValue().split(";"))).map(String::strip)
                .anyMatch(expected::equals);
    }

    void evaluate() {
        if (submissionStarted || layer == null || workflow.state() != State.WAITING_FOR_UPLOAD
                || workflow.snapshot().editLayer() != layer) {
            return;
        }
        if (layer.isUploadInProgress()) {
            uploadStarted = true;
            return;
        }
        final var clean = !layer.getDataSet().requiresUploadToServer()
                && new APIDataSet(layer.getDataSet()).isEmpty();
        if (correlatedChangeset == null || !clean) {
            if (uploadStarted && (!clean || ++cleanWithoutCorrelationPolls >= 8)) {
                warnAndCancel(clean
                        ? tr("OSM upload completed without the expected MapRoulette task tag; "
                                + "the Fixed draft was preserved")
                        : tr("OSM upload did not complete; the Fixed draft was preserved"));
            }
            return;
        }
        submissionStarted = true;
        final var changesetId = correlatedChangeset.getId();
        executor.execute(() -> {
            try {
                submissions.submitFixedAfterUpload(changesetId);
            } catch (Exception exception) {
                errorHandler.accept(exception);
            }
        });
    }

    public void cancel() {
        if (workflow.state() == State.WAITING_FOR_UPLOAD) {
            workflow.cancelUpload();
        } else {
            cleanup();
        }
    }

    private void warnAndCancel(String message) {
        cancel();
        warningHandler.accept(message);
    }

    private static void showWarning(String message) {
        Logging.warn(message);
        new Notification(message).setIcon(JOptionPane.WARNING_MESSAGE).setDuration(Notification.TIME_LONG).show();
    }

    public void cleanup() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
        if (cacheCleanup != null) {
            cacheCleanup.run();
            cacheCleanup = null;
        }
        if (uploadCleanup != null) {
            uploadCleanup.run();
            uploadCleanup = null;
        }
        uploadPrimitives = Collections.emptySet();
        correlatedChangeset = null;
        layer = null;
        taskId = 0;
        metadataArmed = false;
        metadataPrepared = false;
        preflightComplete = false;
        uploadStarted = false;
        cleanWithoutCorrelationPolls = 0;
    }

    private static Set<Object> identitySet(APIDataSet data) {
        final Set<Object> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(data.getPrimitives());
        return result;
    }

    private static Runnable registerListener(ChangesetCacheListener listener) {
        ChangesetCache.getInstance().addChangesetCacheListener(listener);
        return () -> ChangesetCache.getInstance().removeChangesetCacheListener(listener);
    }

    private static Runnable launchUpload(OsmDataLayer layer, APIDataSet data, Runnable accepted, Runnable canceled) {
        final var dialog = UploadDialog.getUploadDialog();
        final var listener = new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent event) {
                dialog.removeComponentListener(this);
                if (dialog.isCanceled()) {
                    canceled.run();
                } else {
                    accepted.run();
                }
            }
        };
        dialog.addComponentListener(listener);
        ((UploadAction) MainApplication.getMenu().upload).uploadData(layer, data);
        return () -> dialog.removeComponentListener(listener);
    }
}
