// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.enums;

/**
 * The task log actions
 */
public enum TaskLogAction {
    /**
     * The task had a comment
     */
    COMMENT, // 0
    /**
     * The task had a status change
     */
    STATUS_CHANGE, // 1
    /**
     * The task was reviewed
     */
    REVIEW, // 2
    /**
     * The task was updated
     */
    UPDATE, // 3
    /**
     * The task had a meta review
     */
    META_REVIEW, // 4
}
