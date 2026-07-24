// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.model;

/**
 * The target of a grant
 *
 * @param objectType The type of the object
 * @param objectId   The id of the object
 */
public record GrantTarget(int objectType, long objectId) {
}
