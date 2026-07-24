// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.io.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.ChangesetCache;
import org.openstreetmap.josm.data.osm.ChangesetCacheEvent;
import org.openstreetmap.josm.data.osm.ChangesetCacheListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionAuxiliaryRetry;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionDraft;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionResult;
import io.github.richardqzeng.josm.maprouletteflow.workflow.CompletionSubmissionController;
import io.github.richardqzeng.josm.maprouletteflow.workflow.TaskCompletionGateway;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController;
import io.github.richardqzeng.josm.maprouletteflow.workflow.WorkflowController.State;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class FixedUploadCoordinatorTest {
    private final WorkflowController workflow = WorkflowController.getInstance();
    private final FakeGateway gateway = new FakeGateway();
    private final AtomicInteger uploadCleanup = new AtomicInteger();
    private final AtomicInteger listenerCleanup = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private FixedUploadCoordinator coordinator;
    private ChangesetCacheListener registeredListener;
    private Runnable canceled;
    private Node editedNode;

    @BeforeEach
    void setUp() {
        workflow.shutdown();
        coordinator = new FixedUploadCoordinator(workflow,
                new CompletionSubmissionController(workflow, gateway,
                        draft -> new io.github.richardqzeng.josm.maprouletteflow.workflow.TaskReservationService.Result(
                                io.github.richardqzeng.josm.maprouletteflow.workflow.TaskReservationService.Status.EMPTY,
                                null)),
                (layer, data, accepted, cancel) -> {
                    canceled = cancel;
                    return uploadCleanup::incrementAndGet;
                }, listener -> {
                    registeredListener = listener;
                    return () -> {
                        registeredListener = null;
                        listenerCleanup.incrementAndGet();
                    };
                }, Runnable::run, exception -> errors.incrementAndGet());
    }

    @AfterEach
    void tearDown() {
        coordinator.cleanup();
        workflow.shutdown();
    }

    @Test
    void dialogCancellationPreservesDraftAndRemovesObservers() {
        final var draft = enterFixedDraft();
        coordinator.start(draft);
        assertEquals(State.WAITING_FOR_UPLOAD, workflow.state());
        assertNotNull(registeredListener);

        canceled.run();

        assertEquals(State.COMPLETION_DRAFT, workflow.state());
        assertEquals(draft, workflow.snapshot().completionDraft());
        assertNull(registeredListener);
        assertEquals(1, listenerCleanup.get());
        assertEquals(1, uploadCleanup.get());
        assertEquals(0, gateway.statusCalls);
    }

    @Test
    void onlyCorrelatedCleanUploadSubmitsFixedOnce() throws Exception {
        final var draft = enterFixedDraft();
        coordinator.start(draft);

        registeredListener.changesetCacheUpdated(event(changeset(71, "999")));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(State.WAITING_FOR_UPLOAD, workflow.state());
        assertEquals(0, gateway.statusCalls);

        editedNode.setOsmId(1, 1);
        editedNode.setModified(false);
        registeredListener.changesetCacheUpdated(event(changeset(72, "100")));
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(1, gateway.statusCalls);
        assertEquals(1, gateway.changesetCalls);
        assertEquals(State.CHALLENGE_IDLE, workflow.state());
        assertNull(registeredListener);

        coordinator.changesetCacheUpdated(event(changeset(72, "100")));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, gateway.statusCalls);
    }

    @Test
    void observedTransportFailureReturnsToPreservedDraftAndCleansObservers() {
        final var draft = enterFixedDraft();
        coordinator.start(draft);
        final var layer = workflow.snapshot().editLayer();

        layer.setUploadInProgress();
        coordinator.evaluate();
        layer.unsetUploadInProgress();
        coordinator.evaluate();

        assertEquals(State.COMPLETION_DRAFT, workflow.state());
        assertEquals(draft, workflow.snapshot().completionDraft());
        assertNull(registeredListener);
        assertEquals(1, errors.get());
        assertEquals(0, gateway.statusCalls);
    }

    @Test
    void competingPreflightPreservesDraftAndCannotConsumeTaskMetadata() {
        final var draft = enterFixedDraft();
        coordinator.start(draft);
        final var intended = new org.openstreetmap.josm.data.APIDataSet(workflow.snapshot().editLayer().getDataSet());
        final var unrelatedData = new DataSet();
        unrelatedData.addPrimitive(new Node(new LatLon(1, 1)));

        assertTrue(coordinator.checkUpload(intended));
        assertTrue(coordinator.checkUpload(new org.openstreetmap.josm.data.APIDataSet(unrelatedData)));

        assertEquals(State.COMPLETION_DRAFT, workflow.state());
        assertEquals(draft, workflow.snapshot().completionDraft());
        assertNull(registeredListener);
        assertNull(coordinator.consumeMetadataTaskId());
        assertEquals(1, errors.get());
    }

    @Test
    void competingPreflightAfterMetadataConsumptionStillCancelsOnlyFixedAttempt() {
        final var draft = enterFixedDraft();
        coordinator.start(draft);
        final var intended = new org.openstreetmap.josm.data.APIDataSet(workflow.snapshot().editLayer().getDataSet());
        final var unrelatedData = new DataSet();
        unrelatedData.addPrimitive(new Node(new LatLon(1, 1)));

        assertTrue(coordinator.checkUpload(intended));
        assertEquals(100L, coordinator.consumeMetadataTaskId());
        assertTrue(coordinator.checkUpload(new org.openstreetmap.josm.data.APIDataSet(unrelatedData)));

        assertEquals(State.COMPLETION_DRAFT, workflow.state());
        assertNull(registeredListener);
        assertEquals(1, errors.get());
    }

    private CompletionDraft enterFixedDraft() {
        final var challenge = new Challenge(10, "challenge", null, null, null, false, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        final var task = new Task(100, "task", null, null, 10, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, null, 0, null, null, null, false, null, "");
        final var dataSet = new DataSet();
        editedNode = new Node(new LatLon(0, 0));
        dataSet.addPrimitive(editedNode);
        workflow.connect();
        workflow.selectChallenge(challenge);
        workflow.reserveCandidate(task);
        workflow.beginDownload(null);
        workflow.activateTask(task, new OsmDataLayer(dataSet, "test", null));
        final var draft = new CompletionDraft(task, CompletionResult.FIXED, "", "", null, Map.of(),
                NextMode.RANDOM);
        workflow.draftCompletion(draft);
        return draft;
    }

    private static Changeset changeset(int id, String taskId) {
        final var changeset = new Changeset(id);
        changeset.put("maproulette:tasks", taskId);
        return changeset;
    }

    private static ChangesetCacheEvent event(Changeset changeset) {
        return new ChangesetCacheEvent() {
            @Override
            public ChangesetCache getSource() {
                return ChangesetCache.getInstance();
            }

            @Override
            public Collection<Changeset> getAddedChangesets() {
                return List.of(changeset);
            }

            @Override
            public Collection<Changeset> getRemovedChangesets() {
                return List.of();
            }

            @Override
            public Collection<Changeset> getUpdatedChangesets() {
                return List.of();
            }
        };
    }

    private static final class FakeGateway implements TaskCompletionGateway {
        private int statusCalls;
        private int changesetCalls;

        @Override
        public void updateStatus(CompletionDraft draft) {
            statusCalls++;
        }

        @Override
        public void addComment(CompletionAuxiliaryRetry comment) {
        }

        @Override
        public void associateChangeset(long taskId, int changesetId) {
            changesetCalls++;
        }

        @Override
        public boolean hasTaskStatus(long taskId, int status) {
            return false;
        }
    }
}
