// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;
import org.openstreetmap.josm.plugins.maproulette.api.model.Challenge;
import org.openstreetmap.josm.plugins.maproulette.api.model.Point;
import org.openstreetmap.josm.plugins.maproulette.api.model.Task;
import org.openstreetmap.josm.plugins.maproulette.workflow.TaskDownloadService.DownloadOutcome;
import org.openstreetmap.josm.plugins.maproulette.workflow.WorkflowController.State;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class TaskDownloadServiceTest {
    private final WorkflowController workflow = WorkflowController.getInstance();
    private Task reservedTask;

    @BeforeEach
    void setUp() {
        workflow.shutdown();
        workflow.connect();
        workflow.selectChallenge(new Challenge(10, "challenge", null, null, null, false, null, null, null, null,
                null, null, null, null, null, null, null, null, null));
        reservedTask = task("reserved");
        workflow.reserveCandidate(reservedTask);
    }

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void successfulDownloadActivatesFullTaskAndActualLayer() {
        final var fullTask = task("full");
        final var layer = layer();
        final var completed = new AtomicReference<TaskDownloadService.Result>();
        final var service = service(id -> fullTask, (bounds, operation) -> DownloadOutcome.completed(layer));

        service.start(reservedTask, 10, 100, listener(completed, new AtomicReference<>()));

        assertEquals(State.ACTIVE_EDITING, workflow.state());
        assertSame(fullTask, workflow.snapshot().activeTask());
        assertSame(layer, workflow.snapshot().editLayer());
        assertSame(layer, completed.get().layer());
    }

    @Test
    void failureRetainsReservationAndCanRetry() {
        final var failure = new AtomicReference<Exception>();
        final var failing = service(id -> task("full"), (bounds, operation) -> {
            throw new IOException("download failed");
        });

        failing.start(reservedTask, 10, 100, listener(new AtomicReference<>(), failure));

        assertEquals(State.RECOVERABLE_ERROR, workflow.state());
        assertSame(reservedTask, workflow.snapshot().reservedTask());
        assertEquals("download failed", failure.get().getMessage());

        final var layer = layer();
        service(id -> task("retried"), (bounds, operation) -> DownloadOutcome.completed(layer))
                .start(reservedTask, 10, 100, listener(new AtomicReference<>(), new AtomicReference<>()));
        assertEquals(State.ACTIVE_EDITING, workflow.state());
        assertSame(layer, workflow.snapshot().editLayer());
    }

    @Test
    void cancellationReturnsToReservedPreview() {
        final var canceled = new AtomicBoolean();
        final var service = service(id -> task("full"),
                (bounds, operation) -> DownloadOutcome.canceledDownload());

        service.start(reservedTask, 10, 100, new ListenerAdapter() {
            @Override
            public void canceled(Task task) {
                canceled.set(true);
            }
        });

        assertTrue(canceled.get());
        assertEquals(State.RESERVED_PREVIEW, workflow.state());
        assertSame(reservedTask, workflow.snapshot().reservedTask());
    }

    @Test
    void networkAndDownloadRunOffEdtWhileCallbackRunsOnEdt() throws InterruptedException {
        final var starterOffEdt = new AtomicBoolean();
        final var downloaderOffEdt = new AtomicBoolean();
        final var callbackOnEdt = new AtomicBoolean();
        final var finished = new CountDownLatch(1);
        final var service = new TaskDownloadService(workflow, id -> {
            starterOffEdt.set(!SwingUtilities.isEventDispatchThread());
            return task("full");
        }, (bounds, operation) -> {
            downloaderOffEdt.set(!SwingUtilities.isEventDispatchThread());
            return DownloadOutcome.completed(layer());
        }, task -> List.of(), command -> new Thread(command, "task-download-test").start(),
                SwingUtilities::invokeLater);

        service.start(reservedTask, 10, 100, new ListenerAdapter() {
            @Override
            public void completed(TaskDownloadService.Result result) {
                callbackOnEdt.set(SwingUtilities.isEventDispatchThread());
                finished.countDown();
            }
        });

        assertTrue(finished.await(10, TimeUnit.SECONDS));
        assertTrue(starterOffEdt.get());
        assertTrue(downloaderOffEdt.get());
        assertTrue(callbackOnEdt.get());
    }

    private TaskDownloadService service(TaskDownloadService.TaskStarter starter,
            TaskDownloadService.OsmDownloader downloader) {
        return new TaskDownloadService(workflow, starter, downloader, task -> List.of(), Runnable::run,
                Runnable::run);
    }

    private static TaskDownloadService.Listener listener(AtomicReference<TaskDownloadService.Result> completed,
            AtomicReference<Exception> failure) {
        return new ListenerAdapter() {
            @Override
            public void completed(TaskDownloadService.Result result) {
                completed.set(result);
            }

            @Override
            public void failed(Task task, Exception exception) {
                failure.set(exception);
            }
        };
    }

    private static Task task(String name) {
        return new Task(100, name, null, null, 10, "instructions", new Point(50, 60), new DataSet(), null,
                TaskStatus.CREATED, null, null, null, null, 0, null, null, null, false, null, "");
    }

    private static OsmDataLayer layer() {
        return new OsmDataLayer(new DataSet(), "download-test", null);
    }

    private abstract static class ListenerAdapter implements TaskDownloadService.Listener {
        @Override
        public void completed(TaskDownloadService.Result result) {
            // Optional test callback.
        }

        @Override
        public void canceled(Task task) {
            // Optional test callback.
        }

        @Override
        public void failed(Task task, Exception exception) {
            // Optional test callback.
        }
    }
}
