// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.maproulette.api.model.LeaderboardEntry;

class LeaderboardEntryParserTest {
    @Test
    void parsesOneLeaderboardRow() throws IOException {
        assertEquals(new LeaderboardEntry(42, 16_065, 3_377, 440), parse("""
                [{"userId":42,"score":16065,"rank":440,"completedTasks":3377,"ignored":"value"}]
                """).orElseThrow());
    }

    @Test
    void acceptsEmptyAndNullCompletedTasks() throws IOException {
        assertTrue(parse("[]").isEmpty());
        assertEquals(0, parse("[{\"userId\":42,\"score\":0,\"rank\":1,\"completedTasks\":null}]")
                .orElseThrow().completedTasks());
    }

    @Test
    void rejectsMultipleOrMalformedRowsWithoutEchoingContent() {
        final var multiple = assertThrows(IOException.class, () -> parse("""
                [{"userId":1,"score":1,"rank":1},{"userId":2,"score":1,"rank":2}]
                """));
        final var malformed = assertThrows(IOException.class, () -> parse("{\"private\":\"secret\"}"));
        assertEquals("MapRoulette returned an invalid leaderboard response", multiple.getMessage());
        assertEquals("MapRoulette returned an invalid leaderboard response", malformed.getMessage());
    }

    private static java.util.Optional<LeaderboardEntry> parse(String json) throws IOException {
        return LeaderboardEntryParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
