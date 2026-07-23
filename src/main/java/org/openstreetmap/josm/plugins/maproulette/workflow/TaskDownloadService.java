// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.maproulette.api.TaskAPI;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.data.TaskPrimitives;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;

/** Starts a reserved task and prepares its editable OSM data without blocking Swing. */
public final class TaskDownloadService {
    private final WorkflowController workflow;
    private final TaskStarter taskStarter;
    private final OsmDownloader downloader;
    private final Function<Task, Collection<PrimitiveId>> primitiveIdResolver;
    private final Executor worker;
    private final Consumer<Runnable> uiExecutor;

    public TaskDownloadService(WorkflowController workflow) {
        this(workflow, TaskAPI::start, new JosmOsmDownloader(), TaskPrimitives::getPrimitiveIds,
                ForkJoinPool.commonPool(), GuiHelper::runInEDT);
    }

    TaskDownloadService(WorkflowController workflow, TaskStarter taskStarter, OsmDownloader downloader,
            Function<Task, Collection<PrimitiveId>> primitiveIdResolver, Executor worker,
            Consumer<Runnable> uiExecutor) {
        this.workflow = Objects.requireNonNull(workflow);
        this.taskStarter = Objects.requireNonNull(taskStarter);
        this.downloader = Objects.requireNonNull(downloader);
        this.primitiveIdResolver = Objects.requireNonNull(primitiveIdResolver);
        this.worker = Objects.requireNonNull(worker);
        this.uiExecutor = Objects.requireNonNull(uiExecutor);
    }

    /** Start or retry preparation of the currently reserved task. */
    public void start(Task reservedTask, int geometryPaddingPercent, int pointRadiusMeters, Listener listener) {
        Objects.requireNonNull(reservedTask);
        Objects.requireNonNull(listener);
        final var operation = new Operation();
        if (workflow.state() == State.RESERVED_PREVIEW) {
            workflow.beginDownload(operation::cancel);
        } else if (workflow.state() == State.RECOVERABLE_ERROR) {
            workflow.retryDownload(operation::cancel);
        } else {
            throw new IllegalStateException("A task download can only start from a retained reservation");
        }
        try {
            worker.execute(() -> run(reservedTask, geometryPaddingPercent, pointRadiusMeters, operation, listener));
        } catch (RuntimeException exception) {
            finishFailure(reservedTask, operation, listener, exception);
        }
    }

    /** Download fresh OSM data for the active task into its exact retained edit layer. */
    public void redownloadActive(int geometryPaddingPercent, int pointRadiusMeters, Listener listener) {
        Objects.requireNonNull(listener);
        final var snapshot = workflow.snapshot();
        final var activeTask = Objects.requireNonNull(snapshot.activeTask(), "No active task is retained");
        final var editLayer = Objects.requireNonNull(snapshot.editLayer(), "No active task edit layer is retained");
        final var operation = new Operation();
        if (snapshot.state() == State.ACTIVE_EDITING) {
            workflow.beginRedownload(operation::cancel);
        } else if (workflow.canRetryActiveRedownload()) {
            workflow.retryRedownload(operation::cancel);
        } else {
            throw new IllegalStateException("An active task re-download cannot start from the current workflow");
        }
        try {
            worker.execute(() -> runRedownload(activeTask, editLayer, geometryPaddingPercent, pointRadiusMeters,
                    operation, listener));
        } catch (RuntimeException exception) {
            finishRedownloadFailure(activeTask, editLayer, operation, listener, exception);
        }
    }

    private void run(Task reservedTask, int geometryPaddingPercent, int pointRadiusMeters, Operation operation,
            Listener listener) {
        try {
            final var fullTask = taskStarter.start(reservedTask.id());
            if (operation.isCanceled()) {
                finishCanceled(reservedTask, operation, listener);
                return;
            }
            final var bounds = TaskDownloadBounds.forTask(fullTask, geometryPaddingPercent, pointRadiusMeters)
                    .orElseThrow(() -> new IOException("The task has no usable geometry or location to download"));
            final var outcome = downloader.download(bounds, operation);
            if (operation.isCanceled() || outcome.canceled()) {
                finishCanceled(reservedTask, operation, listener);
                return;
            }
            final var primitiveIds = primitiveIdResolver.apply(fullTask);
            finishSuccess(reservedTask, operation, listener,
                    new Result(fullTask, bounds, outcome.layer(), primitiveIds));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finishCanceled(reservedTask, operation, listener);
        } catch (CancellationException exception) {
            finishCanceled(reservedTask, operation, listener);
        } catch (Exception exception) {
            finishFailure(reservedTask, operation, listener, exception);
        }
    }

    private void runRedownload(Task activeTask, OsmDataLayer editLayer, int geometryPaddingPercent,
            int pointRadiusMeters, Operation operation, Listener listener) {
        try {
            final var bounds = TaskDownloadBounds.forTask(activeTask, geometryPaddingPercent, pointRadiusMeters)
                    .orElseThrow(() -> new IOException("The task has no usable geometry or location to download"));
            final var outcome = downloader.redownload(bounds, editLayer, operation);
            if (operation.isCanceled() || outcome.canceled()) {
                finishRedownloadCanceled(activeTask, editLayer, operation, listener);
                return;
            }
            if (outcome.layer() != editLayer) {
                throw new IOException("JOSM did not re-download into the active task edit layer");
            }
            final var primitiveIds = primitiveIdResolver.apply(activeTask);
            finishRedownloadSuccess(activeTask, editLayer, operation, listener,
                    new Result(activeTask, bounds, editLayer, primitiveIds));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finishRedownloadCanceled(activeTask, editLayer, operation, listener);
        } catch (CancellationException exception) {
            finishRedownloadCanceled(activeTask, editLayer, operation, listener);
        } catch (Exception exception) {
            finishRedownloadFailure(activeTask, editLayer, operation, listener, exception);
        }
    }

    private void finishSuccess(Task reservedTask, Operation operation, Listener listener, Result result) {
        uiExecutor.accept(() -> {
            if (!isCurrentDownload(reservedTask)) {
                return;
            }
            if (operation.isCanceled()) {
                workflow.cancelDownload();
                listener.canceled(reservedTask);
                return;
            }
            operation.finish();
            workflow.activateTask(result.task(), result.layer());
            listener.completed(result);
        });
    }

    private void finishCanceled(Task reservedTask, Operation operation, Listener listener) {
        uiExecutor.accept(() -> {
            if (!isCurrentDownload(reservedTask)) {
                return;
            }
            workflow.cancelDownload();
            listener.canceled(reservedTask);
        });
    }

    private void finishFailure(Task reservedTask, Operation operation, Listener listener, Exception exception) {
        uiExecutor.accept(() -> {
            if (!isCurrentDownload(reservedTask)) {
                return;
            }
            if (operation.isCanceled()) {
                workflow.cancelDownload();
                listener.canceled(reservedTask);
                return;
            }
            operation.finish();
            workflow.failRecoverably();
            listener.failed(reservedTask, exception);
        });
    }

    private void finishRedownloadSuccess(Task activeTask, OsmDataLayer editLayer, Operation operation,
            Listener listener, Result result) {
        uiExecutor.accept(() -> {
            if (!isCurrentRedownload(activeTask, editLayer)) {
                return;
            }
            if (operation.isCanceled()) {
                workflow.cancelRedownload();
                listener.canceled(activeTask);
                return;
            }
            operation.finish();
            workflow.redownloadSucceeded(activeTask, editLayer);
            listener.completed(result);
        });
    }

    private void finishRedownloadCanceled(Task activeTask, OsmDataLayer editLayer, Operation operation,
            Listener listener) {
        uiExecutor.accept(() -> {
            if (!isCurrentRedownload(activeTask, editLayer)) {
                return;
            }
            workflow.cancelRedownload();
            listener.canceled(activeTask);
        });
    }

    private void finishRedownloadFailure(Task activeTask, OsmDataLayer editLayer, Operation operation,
            Listener listener, Exception exception) {
        uiExecutor.accept(() -> {
            if (!isCurrentRedownload(activeTask, editLayer)) {
                return;
            }
            if (operation.isCanceled()) {
                workflow.cancelRedownload();
                listener.canceled(activeTask);
                return;
            }
            operation.finish();
            workflow.failRecoverably();
            listener.failed(activeTask, exception);
        });
    }

    private boolean isCurrentDownload(Task task) {
        final var snapshot = workflow.snapshot();
        return snapshot.state() == State.STARTING_DOWNLOAD && snapshot.reservedTask() != null
                && snapshot.reservedTask().id() == task.id();
    }

    private boolean isCurrentRedownload(Task task, OsmDataLayer layer) {
        final var snapshot = workflow.snapshot();
        return snapshot.state() == State.REDOWNLOADING && snapshot.activeTask() != null
                && snapshot.activeTask().id() == task.id() && snapshot.editLayer() == layer;
    }

    /** Result delivered only after JOSM has merged into or created the actual edit layer. */
    public record Result(Task task, Bounds downloadBounds, OsmDataLayer layer, Collection<PrimitiveId> primitiveIds) {
        public Result {
            Objects.requireNonNull(task);
            Objects.requireNonNull(downloadBounds);
            Objects.requireNonNull(layer);
            primitiveIds = primitiveIds == null ? List.of() : List.copyOf(primitiveIds);
        }
    }

    /** EDT callbacks for panel presentation. */
    public interface Listener {
        void completed(Result result);

        void canceled(Task reservedTask);

        void failed(Task reservedTask, Exception exception);
    }

    @FunctionalInterface
    interface TaskStarter {
        Task start(long taskId) throws Exception;
    }

    @FunctionalInterface
    interface OsmDownloader {
        DownloadOutcome download(Bounds bounds, Operation operation) throws Exception;

        default DownloadOutcome redownload(Bounds bounds, OsmDataLayer target, Operation operation) throws Exception {
            return download(bounds, operation);
        }
    }

    record DownloadOutcome(OsmDataLayer layer, boolean canceled) {
        DownloadOutcome {
            if (!canceled) {
                Objects.requireNonNull(layer);
            }
        }

        static DownloadOutcome completed(OsmDataLayer layer) {
            return new DownloadOutcome(layer, false);
        }

        static DownloadOutcome canceledDownload() {
            return new DownloadOutcome(null, true);
        }
    }

    static final class Operation {
        private final AtomicBoolean canceled = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicReference<Runnable> cancelAction = new AtomicReference<>();

        void onCancel(Runnable action) {
            Objects.requireNonNull(action);
            if (!cancelAction.compareAndSet(null, action)) {
                throw new IllegalStateException("A download cancellation action is already registered");
            }
            if (canceled.get()) {
                action.run();
            }
        }

        boolean isCanceled() {
            return canceled.get();
        }

        void cancel() {
            if (!finished.get() && canceled.compareAndSet(false, true)) {
                final var action = cancelAction.get();
                if (action != null) {
                    action.run();
                }
            }
        }

        void finish() {
            finished.set(true);
            cancelAction.set(null);
        }
    }

    private static final class JosmOsmDownloader implements OsmDownloader {
        @Override
        public DownloadOutcome download(Bounds bounds, Operation operation) throws Exception {
            return download(bounds, null, operation);
        }

        @Override
        public DownloadOutcome redownload(Bounds bounds, OsmDataLayer target, Operation operation) throws Exception {
            return download(bounds, Objects.requireNonNull(target), operation);
        }

        private DownloadOutcome download(Bounds bounds, OsmDataLayer exactTarget, Operation operation)
                throws Exception {
            if (SwingUtilities.isEventDispatchThread()) {
                throw new IllegalStateException("OSM downloads must not run on the Swing event dispatch thread");
            }
            if (exactTarget != null) {
                return downloadIsolated(bounds, exactTarget, operation);
            }
            final var layerManager = MainApplication.getLayerManager();
            final var plan = GuiHelper.runInEDTAndWaitAndReturn(() -> TaskDownloadLayerSelector.prepare(layerManager));
            final var monitor = GuiHelper.runInEDTAndWaitAndReturn(
                    () -> new PleaseWaitProgressMonitor(MainApplication.getMainFrame(), tr("Downloading OSM task data")));
            final var download = new DownloadOsmTask();
            download.setZoomAfterDownload(false);
            try {
                final var future = download.download(new DownloadParams().withNewLayer(false), bounds, monitor);
                operation.onCancel(() -> {
                    download.cancel();
                    monitor.cancel();
                    future.cancel(true);
                });
                future.get();
                if (operation.isCanceled() || download.isCanceled()) {
                    return DownloadOutcome.canceledDownload();
                }
                if (download.isFailed()) {
                    throw downloadFailure(download);
                }
                final var layer = GuiHelper.runInEDTAndWaitAndReturn(() -> {
                    final var after = List.copyOf(layerManager.getLayersOfType(OsmDataLayer.class));
                    return TaskDownloadLayerSelector.resolveResult(plan.layersBefore(), plan.target(),
                            layerManager.getEditLayer(), after);
                });
                if (layer == null) {
                    throw new IOException("JOSM completed the download without an editable OSM data layer");
                }
                return DownloadOutcome.completed(layer);
            } finally {
                GuiHelper.runInEDT(monitor::close);
            }
        }

        private DownloadOutcome downloadIsolated(Bounds bounds, OsmDataLayer target, Operation operation)
                throws Exception {
            final var layerManager = MainApplication.getLayerManager();
            GuiHelper.runInEDTAndWaitAndReturn(() -> {
                TaskDownloadLayerSelector.prepareExact(layerManager, target);
                return null;
            });
            final var monitor = GuiHelper.runInEDTAndWaitAndReturn(
                    () -> new PleaseWaitProgressMonitor(MainApplication.getMainFrame(), tr("Re-downloading OSM task data")));
            final var download = new DownloadOsmTask();
            download.setZoomAfterDownload(false);
            try {
                final var parameters = new DownloadParams().withNewLayer(true).withLayerName(tr("MapRoulette re-download"));
                final var future = download.download(parameters, bounds, monitor);
                operation.onCancel(() -> {
                    download.cancel();
                    monitor.cancel();
                    future.cancel(true);
                });
                future.get();
                if (operation.isCanceled() || download.isCanceled()) {
                    return DownloadOutcome.canceledDownload();
                }
                if (download.isFailed()) {
                    throw downloadFailure(download);
                }
                final var downloaded = download.getDownloadedData();
                if (downloaded == null) {
                    throw new IOException("JOSM completed the re-download without OSM data");
                }
                return GuiHelper.runInEDTAndWaitAndReturn(() -> {
                    if (operation.isCanceled()) {
                        return DownloadOutcome.canceledDownload();
                    }
                    TaskDownloadLayerSelector.prepareExact(layerManager, target);
                    target.mergeFrom(downloaded);
                    layerManager.setActiveLayer(target);
                    return DownloadOutcome.completed(target);
                });
            } finally {
                GuiHelper.runInEDTAndWaitAndReturn(() -> {
                    final var downloaded = download.getDownloadedData();
                    if (downloaded != null) {
                        layerManager.getLayersOfType(OsmDataLayer.class).stream()
                                .filter(layer -> layer != target && layer.getDataSet() == downloaded).findFirst()
                                .ifPresent(layerManager::removeLayer);
                    }
                    return null;
                });
                GuiHelper.runInEDT(monitor::close);
            }
        }

        private static IOException downloadFailure(DownloadOsmTask download) {
            final var errors = download.getErrorObjects();
            if (!errors.isEmpty() && errors.get(0) instanceof Exception exception) {
                return new IOException("JOSM could not download OSM data", exception);
            }
            final var detail = errors.isEmpty() ? "unknown error" : String.valueOf(errors.get(0));
            return new IOException("JOSM could not download OSM data: " + detail);
        }
    }

}
