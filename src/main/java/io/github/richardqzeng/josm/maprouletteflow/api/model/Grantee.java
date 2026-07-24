// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.model;

/**
 * A grantee
 *
 * @param granteeType The type of grantee
 * @param granteeId   The id of the grantee
 */
public record Grantee(int granteeType, long granteeId) {
}
