// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static io.github.richardqzeng.josm.maprouletteflow.util.RecordAssertion.assertRecordsEqual;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.TaskAPI;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.TaskStatus;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ClusteredPoint;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Point;
import io.github.richardqzeng.josm.maprouletteflow.api.model.PointReview;
import io.github.richardqzeng.josm.maprouletteflow.api.model.PublicUser;
import io.github.richardqzeng.josm.maprouletteflow.util.MapRouletteConfig;

/**
 * Test class for {@link ClusteredPointParser}
 */
@MapRouletteConfig
class ClusteredPointParserTest {
    @Test
    void acceptsEmptyBoundingObject() {
        final var json = """
                {"id":1,"owner":-1,"ownerName":"","title":"task","parentId":2,"parentName":"challenge",
                 "point":{"lat":1,"lng":2},"bounding":{},"blurb":"","modified":"2026-01-01T00:00:00Z","type":1,
                 "pointReview":{}}
                 """;

        final var point = (ClusteredPoint) ClusteredPointParser
                .parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertNull(point.bounding());
    }

    @Test
    void testTask136226437() throws IOException {
        final var box = TaskAPI.box(-108.4962538, 39.082404, -108.4962538, 39.082404, 1_000, 0, true, null, null, false,
                true, true);
        assertEquals(1, box.length);
        final var photoMC = new PublicUser(11197, null, "Photo_MC", null, null);
        final var actual = box[0];
        final var expected = new ClusteredPoint(136226437L, -1L, "", "861207014_way_1", 27887L,
                "United States | Polygon has self intersection", new Point(39.082404, -108.4962538), "", "",
                Instant.parse("2023-01-31T19:28:24.909Z"), null, 2, TaskStatus.TOO_HARD, null,
                Instant.parse("2022-10-18T03:19:56.028Z"), 923162L, photoMC,
                new PointReview(1, photoMC, new PublicUser(9724, null, "Jenn_Bh", null, null),
                        Instant.parse("2022-10-18T07:09:14.455Z"), null, null, null,
                        Instant.parse("2022-10-18T07:08:52.469Z"), new long[0]),
                0, null, false);
        assertRecordsEqual(expected, actual);
    }
}
