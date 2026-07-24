// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class TaskReservationServiceTest {
    private final WorkflowController workflow = WorkflowController.getInstance();

    @BeforeEach
    void setUp() {
        workflow.shutdown();
        workflow.connect();
        workflow.selectChallenge(challenge(10));
    }

    @AfterEach
    void tearDown() {
        workflow.shutdown();
    }

    @Test
    void acceptsExactlyOneCandidateAndUsesNoRandomProximity() throws Exception {
        final var candidate = task(100, 10);
        final var api = new FakeApi(candidate, task(101, 10));
        final var service = new TaskReservationService(workflow, api);

        final var result = service.reserve(10, NextMode.RANDOM, 55L, ignored -> false);

        assertEquals(TaskReservationService.Status.RESERVED, result.status());
        assertSame(candidate, result.task());
        assertSame(candidate, workflow.snapshot().reservedTask());
        assertEquals(Collections.singletonList(null), api.proximities);
        assertEquals(1, api.candidates.size());
    }

    @Test
    void nearbyUsesOptionalCompletedTask() throws Exception {
        final var api = new FakeApi();
        final var result = new TaskReservationService(workflow, api).reserve(10, NextMode.NEARBY, 55L,
                ignored -> false);

        assertEquals(TaskReservationService.Status.EMPTY, result.status());
        assertNull(result.task());
        assertEquals(List.of(55L), api.proximities);
    }

    @Test
    void excludedCandidatesAreReleasedImmediatelyWithOnlyThreeRetries() throws Exception {
        final var excluded = task(100, 10);
        final var api = new FakeApi(excluded, excluded, excluded, excluded, task(101, 10));

        final var result = new TaskReservationService(workflow, api).reserve(10, NextMode.RANDOM, null,
                ignored -> true);

        assertEquals(TaskReservationService.Status.EXCLUDED_RETRIES_EXHAUSTED, result.status());
        assertNull(workflow.snapshot().reservedTask());
        assertEquals(4, api.candidates.size());
        assertEquals(List.of("candidate", "release:100", "candidate", "release:100", "candidate",
                "release:100", "candidate", "release:100"), api.operations);
    }

    @Test
    void guardPreventsLockMutatingCallWhenReservationExists() throws Exception {
        workflow.reserveCandidate(task(100, 10));
        final var api = new FakeApi(task(101, 10));

        assertThrows(IllegalStateException.class,
                () -> new TaskReservationService(workflow, api).reserve(10, NextMode.RANDOM, null,
                        ignored -> false));
        assertEquals(0, api.candidates.size());
    }

    @Test
    void automaticNearbyReservationUsesCompletedTaskAndTransitionsDirectlyToPreview() throws Exception {
        final var completed = enterSubmittingCompletion();
        final var candidate = task(200, 10);
        final var api = new FakeApi(candidate);

        final var result = new TaskReservationService(workflow, api).reserveAfterCompletion(10, NextMode.NEARBY,
                completed.id(), ignored -> false);

        assertEquals(TaskReservationService.Status.RESERVED, result.status());
        assertEquals(List.of(100L), api.proximities);
        assertSame(candidate, workflow.snapshot().reservedTask());
        assertEquals(100L, workflow.snapshot().completedTaskId());
        assertEquals(WorkflowController.State.RESERVED_PREVIEW, workflow.state());
    }

    @Test
    void automaticRandomReservationOmitsProximity() throws Exception {
        enterSubmittingCompletion();
        final var api = new FakeApi();

        final var result = new TaskReservationService(workflow, api).reserveAfterCompletion(10, NextMode.RANDOM, 100,
                ignored -> false);

        assertEquals(TaskReservationService.Status.EMPTY, result.status());
        assertEquals(Collections.singletonList(null), api.proximities);
        assertEquals(WorkflowController.State.SUBMITTING, workflow.state());
    }

    @Test
    void automaticExcludedCandidatesAreReleasedAndStopAfterThreeRetries() throws Exception {
        enterSubmittingCompletion();
        final var excluded = task(200, 10);
        final var api = new FakeApi(excluded, excluded, excluded, excluded, task(201, 10));

        final var result = new TaskReservationService(workflow, api).reserveAfterCompletion(10, NextMode.NEARBY, 100,
                ignored -> true);

        assertEquals(TaskReservationService.Status.EXCLUDED_RETRIES_EXHAUSTED, result.status());
        assertEquals(4, api.candidates.size());
        assertEquals(4, api.operations.stream().filter(operation -> operation.startsWith("release:")).count());
        assertEquals(WorkflowController.State.SUBMITTING, workflow.state());
    }

    private Task enterSubmittingCompletion() {
        final var completed = task(100, 10);
        workflow.reserveCandidate(completed);
        workflow.beginDownload(null);
        workflow.activateTask(completed, new OsmDataLayer(new DataSet(), "test", null));
        workflow.draftCompletion(new CompletionDraft(completed, CompletionResult.CANT_COMPLETE, "", "", null,
                java.util.Map.of(), NextMode.NEARBY));
        workflow.beginSubmission();
        workflow.statusCommitted(null);
        return completed;
    }

    private static final class FakeApi implements TaskReservationService.Api {
        private final ArrayDeque<Task> responses = new ArrayDeque<>();
        private final List<Task> candidates = new ArrayList<>();
        private final List<Long> proximities = new ArrayList<>();
        private final List<String> operations = new ArrayList<>();

        FakeApi(Task... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public Task prioritizedTask(long challengeId, Long proximityTaskId) {
            operations.add("candidate");
            proximities.add(proximityTaskId);
            final var task = responses.poll();
            if (task != null) {
                candidates.add(task);
            }
            return task;
        }

        @Override
        public void release(long taskId) {
            operations.add("release:" + taskId);
        }
    }

    private static Task task(long id, long challengeId) {
        return new Task(id, "task", null, null, challengeId, null, null, new DataSet(), null, TaskStatus.CREATED,
                null, null, null, 0, null, null, null, false, "");
    }

    private static Challenge challenge(long id) {
        return new Challenge(id, "challenge", null, null, null, false, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
