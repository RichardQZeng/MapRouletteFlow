// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChallengeInputParserTest {
    @Test
    void parsesNumericIdsAndSupportedChallengeUrls() {
        assertEquals(123, ChallengeInputParser.parse(" 123 ").orElseThrow());
        assertEquals(456, ChallengeInputParser.parse("https://maproulette.org/challenge/456").orElseThrow());
        assertEquals(789,
                ChallengeInputParser.parse("https://www.maproulette.org/challenge/789/task/12?foo=bar#map")
                        .orElseThrow());
        assertEquals(234,
                ChallengeInputParser.parse("http://maproulette.org/browse/challenges/234/").orElseThrow());
    }

    @Test
    void extractsTaskIdsFromSupportedTaskUrls() {
        final var selection = ChallengeInputParser
                .parseSelection("https://www.maproulette.org/challenge/789/task/12?foo=bar#map").orElseThrow();

        assertEquals(789, selection.challengeId());
        assertEquals(12, selection.taskId());
        assertEquals(42, ChallengeInputParser.parseTaskId(" 42 ").orElseThrow());
    }

    @Test
    void rejectsInvalidIdsHostsAndPaths() {
        assertTrue(ChallengeInputParser.parse(null).isEmpty());
        assertTrue(ChallengeInputParser.parse("0").isEmpty());
        assertTrue(ChallengeInputParser.parse("-2").isEmpty());
        assertTrue(ChallengeInputParser.parse("999999999999999999999999").isEmpty());
        assertTrue(ChallengeInputParser.parse("https://evilmaproulette.org/challenge/1").isEmpty());
        assertTrue(ChallengeInputParser.parse("https://maproulette.org/project/1").isEmpty());
        assertTrue(ChallengeInputParser.parse("ftp://maproulette.org/challenge/1").isEmpty());
        assertTrue(ChallengeInputParser.parseSelection("https://maproulette.org/challenge/1/task").isEmpty());
        assertTrue(ChallengeInputParser.parseSelection("https://maproulette.org/challenge/1/task/0").isEmpty());
        assertTrue(ChallengeInputParser.parseTaskId("https://maproulette.org/challenge/1/task/2").isEmpty());
        assertTrue(ChallengeInputParser.parseTaskId("-2").isEmpty());
    }
}
