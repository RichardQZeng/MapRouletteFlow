// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.model;

/**
 * A grant
 *
 * @param id      The id of the grant
 * @param name    The name of the grant
 * @param grantee The grantee information
 * @param role    The role of the grant
 * @param target  The target of the grant
 */
public record Grant(long id, String name, Grantee grantee, int role, GrantTarget target) implements Identifier {
}
