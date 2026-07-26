// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import jakarta.annotation.Nullable;

/** Parses challenge IDs accepted by the single-task workflow. */
public final class ChallengeInputParser {
    /** Parsed challenge selection, optionally narrowed to one task. */
    public record Selection(long challengeId, @Nullable Long taskId) {
    }

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
        final var selection = parseSelection(input);
        return selection.isPresent() ? OptionalLong.of(selection.orElseThrow().challengeId()) : OptionalLong.empty();
    }

    /**
     * Parse a positive challenge ID or an official MapRoulette challenge/task URL.
     *
     * @param input user input
     * @return parsed challenge and optional task selection
     */
    public static Optional<Selection> parseSelection(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        final var value = input.strip();
        if (value.chars().allMatch(Character::isDigit)) {
            final var challengeId = positiveLong(value);
            return challengeId.isPresent() ? Optional.of(new Selection(challengeId.getAsLong(), null))
                    : Optional.empty();
        }
        try {
            final var uri = new URI(value);
            final var scheme = uri.getScheme();
            final var host = uri.getHost();
            if (scheme == null || host == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return Optional.empty();
            }
            final var normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!("maproulette.org".equals(normalizedHost) || "www.maproulette.org".equals(normalizedHost))) {
                return Optional.empty();
            }
            final var segments = uri.getPath().split("/");
            for (int index = 0; index + 1 < segments.length; index++) {
                if ("challenge".equals(segments[index]) || "challenges".equals(segments[index])) {
                    final var challengeId = positiveLong(segments[index + 1]);
                    if (challengeId.isEmpty()) {
                        return Optional.empty();
                    }
                    if (index + 2 < segments.length
                            && ("task".equals(segments[index + 2]) || "tasks".equals(segments[index + 2]))) {
                        if (index + 3 >= segments.length) {
                            return Optional.empty();
                        }
                        final var taskId = positiveLong(segments[index + 3]);
                        return taskId.isPresent()
                                ? Optional.of(new Selection(challengeId.getAsLong(), taskId.getAsLong()))
                                : Optional.empty();
                    }
                    return Optional.of(new Selection(challengeId.getAsLong(), null));
                }
            }
        } catch (URISyntaxException exception) {
            // Unsupported input is reported by the caller.
        }
        return Optional.empty();
    }

    /** Parse a positive numeric task ID entered in the dedicated task field. */
    public static OptionalLong parseTaskId(String input) {
        if (input == null || input.isBlank()) {
            return OptionalLong.empty();
        }
        final var value = input.strip();
        return value.chars().allMatch(Character::isDigit) ? positiveLong(value) : OptionalLong.empty();
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
