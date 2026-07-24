// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.enums.Achievement;
import io.github.richardqzeng.josm.maprouletteflow.api.model.AuthenticatedUser;

class AuthenticatedUserParserTest {
    @Test
    void parsesOnlyRequiredAccountFields() throws IOException {
        final var response = """
                {"id":42,"score":135,"apiKey":"do-not-retain","email":"private@example.test",
                 "osmProfile":{"id":1234,"displayName":"Mapper","description":"private"}}
                """;

        assertEquals(new AuthenticatedUser(42, 1234, "Mapper", 135), parse(response));
    }

    @Test
    void rejectsMissingOrMalformedFieldsWithoutIncludingResponse() {
        final var missing = assertThrows(IOException.class,
                () -> parse("{\"id\":42,\"score\":1,\"apiKey\":\"sensitive\"}"));
        final var malformed = assertThrows(IOException.class, () -> parse("{\"apiKey\":\"sensitive\""));

        assertEquals("MapRoulette returned an invalid account response", missing.getMessage());
        assertEquals("MapRoulette returned an invalid account response", malformed.getMessage());
    }

    @Test
    void parsesKnownAchievementsAndIgnoresDuplicatesAndFutureIds() throws IOException {
        final var response = """
                {"id":42,"score":135,"achievements":[1,7,7,21,22,999999999999999999999],
                 "osmProfile":{"id":1234,"displayName":"Mapper"}}
                """;

        assertEquals(java.util.List.of(Achievement.MAPPED_ROADS, Achievement.POINTS_100,
                Achievement.CHALLENGE_COMPLETED), parse(response).achievements());
    }

    @Test
    void treatsMissingNullAndEmptyAchievementsAsNone() throws IOException {
        assertEquals(java.util.List.of(), parse(accountJson("")).achievements());
        assertEquals(java.util.List.of(), parse(accountJson(",\"achievements\":null")).achievements());
        assertEquals(java.util.List.of(), parse(accountJson(",\"achievements\":[]")).achievements());
    }

    private static AuthenticatedUser parse(String json) throws IOException {
        return AuthenticatedUserParser.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String accountJson(String addition) {
        return "{\"id\":42,\"score\":135,\"osmProfile\":{\"id\":1234,\"displayName\":\"Mapper\"}"
                + addition + "}";
    }
}
