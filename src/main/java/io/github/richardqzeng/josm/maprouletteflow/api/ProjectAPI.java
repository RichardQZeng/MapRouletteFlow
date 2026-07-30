// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.IOException;

import io.github.richardqzeng.josm.maprouletteflow.api.model.Project;
import io.github.richardqzeng.josm.maprouletteflow.api.parsers.ProjectParser;
import io.github.richardqzeng.josm.maprouletteflow.util.HttpClientUtils;

import jakarta.annotation.Nonnull;

/**
 * A class for getting data from Project APIs
 */
public final class ProjectAPI {
    /**
     * The base project path
     */
    private static final String PROJECT = "/project";

    private ProjectAPI() {
        // Hide constructor
    }

    /**
     * Get a specified project
     *
     * @param id The project to get
     * @return The parsed project object
     * @throws IOException if there was a problem communicating with the server
     */
    @Nonnull
    public static Project get(long id) throws IOException {
        final var baseUrl = getBaseUrl();
        final var client = HttpClientUtils.get(baseUrl + PROJECT + "/" + id);
        try {
            final var response = HttpClientUtils.connectExpecting(client, baseUrl, HTTP_OK, "project request");
            try (var inputstream = response.getContent()) {
                return ProjectParser.parse(inputstream);
            }
        } finally {
            client.disconnect();
        }
    }
}
