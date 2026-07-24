// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class HttpClientUtilsTest {
    @Test
    void centrallyEncodesEveryQueryKeyAndValue() {
        final var query = new TreeMap<>(Map.of("request review", "true", "tags", "one & two,slash/value"));
        assertEquals("?request%20review=true&tags=one%20%26%20two%2Cslash%2Fvalue", HttpClientUtils.query(query));
    }
}
