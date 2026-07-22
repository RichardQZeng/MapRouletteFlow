// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.maproulette.api.parsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.plugins.maproulette.api.model.AuthenticatedUser;

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

    private static AuthenticatedUser parse(String json) throws IOException {
        return AuthenticatedUserParser.parse(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
}
