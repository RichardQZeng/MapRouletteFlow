// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.plugins.maproulette.api.enums.TaskStatus;

/** User-facing MapRoulette completion results and their exact API mappings. */
public enum CompletionResult {
    FIXED(TaskStatus.FIXED),
    ALREADY_FIXED(TaskStatus.ALREADY_FIXED),
    NOT_AN_ISSUE(TaskStatus.FALSE_POSITIVE),
    CANT_COMPLETE(TaskStatus.TOO_HARD),
    SKIP(TaskStatus.SKIPPED);

    private final TaskStatus status;

    CompletionResult(TaskStatus status) {
        this.status = status;
    }

    public TaskStatus status() {
        return status;
    }

    public int actionId() {
        return status.ordinal();
    }

    public String label() {
        return switch (this) {
        case FIXED -> tr("I fixed it!");
        case ALREADY_FIXED -> tr("Already fixed");
        case NOT_AN_ISSUE -> tr("Not an Issue");
        case CANT_COMPLETE -> tr("Can''t Complete");
        case SKIP -> tr("Skip");
        };
    }
}
