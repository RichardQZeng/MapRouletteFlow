// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.awt.event.ActionListener;
import java.time.Duration;
import java.util.Objects;

import javax.swing.Timer;

/** A Swing timer whose cleanup is owned by the workflow controller. */
public final class ReservationLockRefresher {
    /** MapRoulette reservation refresh interval. */
    public static final Duration REFRESH_INTERVAL = Duration.ofMinutes(10);

    private final Timer timer;

    private ReservationLockRefresher(Timer timer) {
        this.timer = timer;
    }

    /**
     * Start refreshing the current reservation and register timer cleanup with the controller.
     *
     * @param workflow workflow owner
     * @param refresh action that queues refresh work off the EDT
     * @return refresh timer handle
     */
    public static ReservationLockRefresher start(WorkflowController workflow, ActionListener refresh) {
        return start(workflow, refresh, Math.toIntExact(REFRESH_INTERVAL.toMillis()));
    }

    static ReservationLockRefresher start(WorkflowController workflow, ActionListener refresh, int delayMillis) {
        Objects.requireNonNull(workflow);
        final var timer = new Timer(delayMillis, Objects.requireNonNull(refresh));
        timer.setRepeats(true);
        final var refresher = new ReservationLockRefresher(timer);
        timer.start();
        workflow.setReservationRefreshCleanup(refresher::stop);
        return refresher;
    }

    public void stop() {
        timer.stop();
    }

    public boolean isRunning() {
        return timer.isRunning();
    }
}
