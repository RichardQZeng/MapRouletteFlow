// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongPredicate;

import io.github.richardqzeng.josm.maprouletteflow.api.ChallengeAPI;
import io.github.richardqzeng.josm.maprouletteflow.api.TaskAPI;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.gui.preferences.MapRouletteTaskPreference.NextMode;

import jakarta.annotation.Nullable;

/** Performs the lock-mutating one-task candidate request behind workflow guards. */
public final class TaskReservationService {
    /** Maximum number of replacement requests after the first excluded candidate. */
    public static final int MAX_EXCLUDED_RETRIES = 3;

    /** Reservation outcome. */
    public enum Status {
        RESERVED,
        EMPTY,
        EXCLUDED_RETRIES_EXHAUSTED,
        REQUEST_FAILED
    }

    /**
     * Reservation result.
     *
     * @param status result status
     * @param task reserved task when successful
     */
    public record Result(Status status, @Nullable Task task) {
    }

    interface Api {
        @Nullable Task prioritizedTask(long challengeId, @Nullable Long proximityTaskId) throws IOException;

        Task start(long taskId) throws IOException;

        void release(long taskId) throws IOException;
    }

    private final WorkflowController workflow;
    private final Api api;

    public TaskReservationService(WorkflowController workflow) {
        this(workflow, new Api() {
            @Override
            public Task prioritizedTask(long challengeId, Long proximityTaskId) throws IOException {
                return ChallengeAPI.prioritizedTask(challengeId, proximityTaskId);
            }

            @Override
            public Task start(long taskId) throws IOException {
                return TaskAPI.start(taskId);
            }

            @Override
            public void release(long taskId) throws IOException {
                TaskAPI.release(taskId);
            }
        });
    }

    TaskReservationService(WorkflowController workflow, Api api) {
        this.workflow = Objects.requireNonNull(workflow);
        this.api = Objects.requireNonNull(api);
    }

    /**
     * Reserve one non-excluded candidate. Every candidate request is guarded because the endpoint replaces user locks.
     *
     * @param challengeId active challenge ID
     * @param mode random or nearby mode
     * @param completedTaskId optional completed task used only by nearby mode
     * @param ignoredTask ignored-task predicate
     * @return reservation outcome
     * @throws IOException on API failure
     */
    public Result reserve(long challengeId, NextMode mode, @Nullable Long completedTaskId, LongPredicate ignoredTask)
            throws IOException {
        return reserve(challengeId, mode, completedTaskId, ignoredTask, workflow::canRequestCandidate,
                workflow::reserveCandidate);
    }

    /** Reserve one next candidate after status and auxiliary completion operations commit. */
    public Result reserveAfterCompletion(long challengeId, NextMode mode, long completedTaskId,
            LongPredicate ignoredTask) throws IOException {
        return reserve(challengeId, mode, completedTaskId, ignoredTask,
                () -> workflow.canRequestNextCandidate(challengeId, completedTaskId), workflow::submissionSucceeded);
    }

    /** Reserve one explicitly requested task without asking the server to choose a replacement candidate. */
    public Result reserveSpecific(long challengeId, long taskId) throws IOException {
        if (!workflow.canRequestCandidate()) {
            throw new IllegalStateException("Pending MapRoulette work blocks task reservation");
        }
        workflow.requireCurrentOwnerAuthenticated();
        final var task = api.start(taskId);
        if (task.id() != taskId) {
            api.release(task.id());
            throw new IOException("MapRoulette returned a different task than requested");
        }
        if (task.parentId() != challengeId) {
            api.release(task.id());
            throw new IOException("The requested task belongs to a different challenge");
        }
        try {
            workflow.reserveCandidate(task);
        } catch (RuntimeException exception) {
            api.release(task.id());
            throw exception;
        }
        return new Result(Status.RESERVED, task);
    }

    private Result reserve(long challengeId, NextMode mode, @Nullable Long completedTaskId, LongPredicate ignoredTask,
            BooleanSupplier guard, Consumer<Task> accept) throws IOException {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(ignoredTask);
        final Long proximity = mode == NextMode.NEARBY ? completedTaskId : null;
        for (int retry = 0; retry <= MAX_EXCLUDED_RETRIES; retry++) {
            if (!guard.getAsBoolean()) {
                throw new IllegalStateException("Pending MapRoulette work blocks candidate replacement");
            }
            workflow.requireCurrentOwnerAuthenticated();
            final var task = api.prioritizedTask(challengeId, proximity);
            if (task == null) {
                return new Result(Status.EMPTY, null);
            }
            if (ignoredTask.test(task.id())) {
                api.release(task.id());
                continue;
            }
            if (task.parentId() != challengeId) {
                api.release(task.id());
                throw new IOException("MapRoulette returned a task from a different challenge");
            }
            try {
                accept.accept(task);
            } catch (RuntimeException exception) {
                api.release(task.id());
                throw exception;
            }
            return new Result(Status.RESERVED, task);
        }
        return new Result(Status.EXCLUDED_RETRIES_EXHAUSTED, null);
    }
}
