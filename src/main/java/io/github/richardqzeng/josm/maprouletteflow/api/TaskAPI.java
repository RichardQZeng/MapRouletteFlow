// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.api;

import static io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl;
import static java.net.HttpURLConnection.HTTP_OK;

import java.io.IOException;

import io.github.richardqzeng.josm.maprouletteflow.api.model.Task;
import io.github.richardqzeng.josm.maprouletteflow.api.parsers.TaskParser;
import io.github.richardqzeng.josm.maprouletteflow.util.HttpClientUtils;

/**
 * Task API
 */
public final class TaskAPI {
    /**
     * The basic task api endpoint
     */
    private static final String TASK = "/task";
    /**
     * Don't allow instantiation of this utility class
     */
    private TaskAPI() {
        // Hide constructor
    }

    /**
     * Get a task without locking it
     *
     * @param task The task to get
     * @return The task for the id
     * @throws IOException if there was a problem communicating with the server
     */
    public static Task get(long task) throws IOException {
        final var baseUrl = getBaseUrl();
        return parseTask(HttpClientUtils.get(baseUrl + TASK + "/" + task), baseUrl);
    }

    /**
     * Start and lock a task
     *
     * @param task The task to start
     * @return The updated task
     * @throws IOException if there was a problem communicating with the server
     * @throws UnauthorizedException If we aren't authorized for the server
     */
    public static Task start(long task) throws IOException {
        final var baseUrl = getBaseUrl();
        return parseTask(HttpClientUtils.get(baseUrl + TASK + "/" + task + "/start"), baseUrl);
    }

    /** Start a task using a credential already validated for the supplied server. */
    public static Task start(long task, String baseUrl, String apiKey) throws IOException {
        final var client = HttpClientUtils.getWithApiKey(baseUrl + TASK + "/" + task + "/start", apiKey);
        return parseTask(client, baseUrl);
    }

    private static Task parseTask(org.openstreetmap.josm.tools.HttpClient client, String baseUrl) throws IOException {
        try {
            final var response = HttpClientUtils.connectExpecting(client, baseUrl, HTTP_OK, "task request");
            try (var inputstream = response.getContent()) {
                return (Task) TaskParser.parseTask(inputstream);
            }
        } finally {
            client.disconnect();
        }
    }

    /**
     * Release a tasks lock (the user must hold the lock)
     *
     * @param task The task to unlock
     * @return The unlocked task
     * @throws IOException if there was a problem communicating with the server
     */
    public static Task release(long task) throws IOException {
        final var baseUrl = getBaseUrl();
        return parseTask(HttpClientUtils.get(baseUrl + TASK + "/" + task + "/release"), baseUrl);
    }

    /** Release a task using the same validated credential that acquired its recovery lock. */
    public static Task release(long task, String baseUrl, String apiKey) throws IOException {
        final var client = HttpClientUtils.getWithApiKey(baseUrl + TASK + "/" + task + "/release", apiKey);
        return parseTask(client, baseUrl);
    }

    /**
     * Refresh an existing lock on a task
     *
     * @param task The task to update
     * @return The updated task
     * @throws IOException if there was a problem communicating with the server
     */
    public static Task refreshLock(long task) throws IOException {
        final var baseUrl = getBaseUrl();
        return parseTask(HttpClientUtils.get(baseUrl + TASK + "/" + task + "/refreshLock"), baseUrl);
    }

}
