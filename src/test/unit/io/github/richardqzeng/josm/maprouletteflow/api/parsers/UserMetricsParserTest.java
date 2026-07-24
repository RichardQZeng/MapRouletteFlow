// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class UserMetricsParserTest {
    @Test
    void parsesCompletedTaskTotal() throws IOException {
        assertEquals(3_377, parse("{\"tasks\":{\"total\":3377,\"fixed\":3000},\"reviewTasks\":{}}"));
    }

    @Test
    void rejectsMalformedMetricsWithoutEchoingContent() {
        final var exception = assertThrows(IOException.class, () -> parse("{\"private\":\"secret\"}"));
        assertEquals("MapRoulette returned an invalid user metrics response", exception.getMessage());
    }

    private static long parse(String json) throws IOException {
        return UserMetricsParser.parseCompletedTasks(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
