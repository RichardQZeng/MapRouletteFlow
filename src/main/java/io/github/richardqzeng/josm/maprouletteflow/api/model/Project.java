// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.model;

import java.time.Instant;

/**
 * A project
 *
 * @param id          The id of the project
 * @param owner       The owner of the project
 * @param name        The name of the project
 * @param created     The time that the project was created
 * @param modified    The time that the project was last modified
 * @param description The description for the project
 * @param enabled     {@code true} if the project is enabled
 * @param displayName The display name form the project
 * @param deleted     {@code true} if the project was deleted
 * @param featured    {@code true} if the project is featured
 */
public record Project(long id, long owner, String name, Instant created, Instant modified,
                       String description, boolean enabled,
                      String displayName, boolean deleted, boolean featured) implements Identifier {
}
