// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.workflow;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.OptionalLong;

/** Parses challenge IDs accepted by the single-task workflow. */
public final class ChallengeInputParser {
    private ChallengeInputParser() {
        // Utility class.
    }

    /**
     * Parse a positive numeric ID or an official MapRoulette challenge URL.
     *
     * @param input user input
     * @return the challenge ID, or an empty value for unsupported input
     */
    public static OptionalLong parse(String input) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }
        final var value = input.strip();
        if (value.chars().allMatch(Character::isDigit)) {
            return positiveLong(value);
        }
        try {
            final var uri = new URI(value);
            final var scheme = uri.getScheme();
            final var host = uri.getHost();
            if (scheme == null || host == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return OptionalLong.empty();
            }
            final var normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!("maproulette.org".equals(normalizedHost) || "www.maproulette.org".equals(normalizedHost))) {
                return OptionalLong.empty();
            }
            final var segments = uri.getPath().split("/");
            for (int index = 0; index + 1 < segments.length; index++) {
                if ("challenge".equals(segments[index]) || "challenges".equals(segments[index])) {
                    return positiveLong(segments[index + 1]);
                }
            }
        } catch (URISyntaxException exception) {
            // Unsupported input is reported by the caller.
        }
        return OptionalLong.empty();
    }

    private static OptionalLong positiveLong(String value) {
        try {
            final var id = Long.parseLong(value);
            return id > 0 ? OptionalLong.of(id) : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }
}
