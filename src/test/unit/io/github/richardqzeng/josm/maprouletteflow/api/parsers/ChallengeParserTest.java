// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api.parsers;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static io.github.richardqzeng.josm.maprouletteflow.util.RecordAssertion.assertRecordsEqual;

import java.net.URI;
import java.time.Instant;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.ChallengeAPI;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Challenge;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ChallengeExtra;
import io.github.richardqzeng.josm.maprouletteflow.api.model.ChallengeGeneral;
import io.github.richardqzeng.josm.maprouletteflow.api.model.Point;
import io.github.richardqzeng.josm.maprouletteflow.util.MapRouletteConfig;

/**
 * Test class for challenge parsing
 */
@MapRouletteConfig
class ChallengeParserTest {
    @Test
    void parsesNullOptionalFields() {
        wireMock().register(get("/api/v2/challenge/50561").willReturn(okJson("""
                {
                  "id": 50561,
                  "name": "Challenge with omitted defaults",
                  "created": "2024-12-20T01:07:44.406Z",
                  "modified": "2026-07-22T22:40:24.345Z",
                  "deleted": false,
                  "owner": 8794039,
                  "parent": 57442,
                  "instruction": "Instructions",
                  "difficulty": 2,
                  "enabled": true,
                  "featured": false,
                  "cooperativeType": 0,
                  "popularity": 1,
                  "checkinComment": "Comment",
                  "checkinSource": "",
                  "requiresLocal": false,
                  "defaultPriority": 0,
                  "mediumPriorityRule": null,
                  "defaultZoom": 18,
                  "minZoom": 3,
                  "maxZoom": 19,
                  "limitTags": false,
                  "limitReviewTags": false,
                  "isArchived": false,
                  "lastTaskRefresh": null,
                  "location": null,
                  "bounding": null,
                  "taskStyles": null,
                  "presets": null
                }
                """)));

        final var challenge = assertDoesNotThrow(() -> ChallengeAPI.challenge(50561));

        assertNull(challenge.lastTaskRefresh());
        assertNull(challenge.location());
        assertNull(challenge.bounding());
        assertNull(challenge.extra().taskStyles());
        assertNull(challenge.extra().presets());
    }

    @Test
    void testChallenge15318() {
        final var challenge = assertDoesNotThrow(() -> ChallengeAPI.challenge(15318));
        final var expected = new Challenge(15318, "Add direction to Stop - USA Los Angeles Timezone",
                Instant.parse("2020-11-28T18:22:24.399Z"), Instant.parse("2023-01-24T14:21:34.897Z"),
                "This challenge will show every [highway=stop](https://wiki.openstreetmap.org/wiki/Tag:highway%3Dstop) without [direction=*](https://wiki.openstreetmap.org/wiki/Key:direction). Your goal is to add tag \"direction\" with value: \"forward\", \"backward\" or \"both\" for every stop. Read article [highway=stop](https://wiki.openstreetmap.org/wiki/Tag:highway%3Dstop) to know how to map.\n\n#### Overpass query\n[All my queries](https://wiki.openstreetmap.org/wiki/User:Binnette/OverpassQueries)\n\n#### About Binnette\n[Twitch](https://www.twitch.tv/binnettetv) - [Twitter](https://twitter.com/BinnetteBin) - [Wiki](https://wiki.openstreetmap.org/wiki/User:Binnette)",
                false, "",
                new ChallengeGeneral(918586, 39866,
                        "Add tag direction with value: \"forward\", \"backward\" or \"both\" for every stop. Read article [highway=stop](https://wiki.openstreetmap.org/wiki/Tag:highway%3Dstop) to know how to map.\n\nEx: direction=forward / direction=backward / direction=both\n\nNote:\n- for this usecase iD editor is great and shows directions.\n- with iD to change direction, click on the node and press v (reverse shortcut).",
                        2,
                        "Add direction=forward/backward/both to [highway=stop](https://wiki.openstreetmap.org/wiki/Tag:highway%3Dstop).",
                        true, false, 0, 1674515454, "Add tag direction to highway=stop - USA LA Timezone #maproulette",
                        "", false, new long[0], false),
                new ChallengeExtra(19, 3, 19, -1, "", "", false, "", "", null, null, false, false, "[]", "", false,
                        null),
                3, "Request timeout to overpass-api.de/178.63.48.217:80 after 120000 ms",
                Instant.parse("2022-07-23T07:13:38.609Z"), Instant.parse("2022-07-23T07:13:38.609Z"),
                new Point(38.3241717215975, -120.521573627918),
                "{\"type\":\"Polygon\",\"coordinates\":[[[-124.586248289365,32.5435948864956],[-124.586248289365,48.9940324531592],[-114.103130987674,48.9940324531592],[-114.103130987674,32.5435948864956],[-124.586248289365,32.5435948864956]]]}",
                4, 29734);
        assertRecordsEqual(expected, challenge);
    }

    private static WireMock wireMock() {
        final var server = URI.create(io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl());
        return new WireMock(server.getHost(), server.getPort());
    }
}
