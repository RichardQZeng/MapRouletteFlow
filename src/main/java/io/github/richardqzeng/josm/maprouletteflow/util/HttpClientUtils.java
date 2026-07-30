// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.util;

import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.github.richardqzeng.josm.maprouletteflow.api.UnauthorizedException;
import org.openstreetmap.josm.tools.HttpClient;

/**
 * A utility class for making clients for use with the MapRoulette API
 */
public final class HttpClientUtils {
    /**
     * Prevent the utils class from being instantiated
     */
    private HttpClientUtils() {
        // Hide the constructor
    }

    /** Connect an authenticated request and enforce its exact successful response status. */
    public static HttpClient.Response connectExpecting(HttpClient client, String baseUrl, int expectedStatus,
            String operation) throws IOException {
        client.setAccept("application/json");
        final var response = client.connect();
        final var actualStatus = response.getResponseCode();
        if (actualStatus == HTTP_UNAUTHORIZED) {
            final var rejectedKey = client.getRequestHeader("apiKey");
            if (rejectedKey != null) {
                AuthenticationManager.handleUnauthorized(baseUrl, rejectedKey);
            }
            throw new UnauthorizedException("MapRoulette rejected the API key");
        }
        if (actualStatus != expectedStatus) {
            throw new IOException("MapRoulette " + operation + " returned HTTP " + actualStatus + "; expected "
                    + expectedStatus);
        }
        return response;
    }

    /**
     * Get data
     *
     * @param url The url to GET
     * @return The client to use
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    public static HttpClient get(String url) throws UnauthorizedException {
        return get(url, Collections.emptyMap());
    }

    /**
     * Get data
     *
     * @param url             The url to GET
     * @param queryParameters The query parameters
     * @return The client to use
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    public static HttpClient get(String url, Map<String, String> queryParameters) throws UnauthorizedException {
        var client = HttpClient.create(safeUrl(url, queryParameters));
        sign(client);
        return client;
    }

    /**
     * Create a GET request signed only with the supplied candidate key.
     *
     * @param url URL to retrieve
     * @param apiKey candidate MapRoulette API key
     * @return configured client
     */
    public static HttpClient getWithApiKey(String url, String apiKey) {
        return getWithApiKey(url, Collections.emptyMap(), apiKey);
    }

    /** Create a GET request with encoded query parameters and an explicit key. */
    public static HttpClient getWithApiKey(String url, Map<String, String> queryParameters, String apiKey) {
        final var client = HttpClient.create(safeUrl(url, queryParameters));
        client.setHeader("apiKey", apiKey);
        return client;
    }

    /**
     * Get the url in a safe manner
     *
     * @param url             The url to create
     * @param queryParameters The parameters to send
     * @return The URL
     */
    private static URL safeUrl(String url, Map<String, String> queryParameters) {
        try {
            if (queryParameters == null || queryParameters.isEmpty()) {
                return new URL(url);
            } else {
                return new URL(url + query(queryParameters));
            }
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Sign the client
     *
     * @param client The client to add the api key to
     * @throws UnauthorizedException if the user isn't logged in or hasn't logged in to MapRoulette before
     */
    private static void sign(HttpClient client) throws UnauthorizedException {
        client.setHeader("apiKey", AuthenticationManager.getApiKey(
                io.github.richardqzeng.josm.maprouletteflow.config.MapRouletteConfig.getBaseUrl()));
    }

    /**
     * Convert a map of parameters to a string
     *
     * @param queryParameters The query parameters to send the server
     * @return The parameters to send the server
     */
    static String query(Map<String, String> queryParameters) {
        return queryParameters.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&", "?", ""));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Put data
     *
     * @param url             The url to PUT
     * @param queryParameters The query parameters
     * @return The client to use
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    public static HttpClient put(String url, Map<String, String> queryParameters) throws UnauthorizedException {
        var client = put(url, Collections.emptyMap(),
                query(queryParameters).substring(1).getBytes(StandardCharsets.UTF_8));
        client.setHeader("Content-Type", "application/x-www-form-urlencoded");
        return client;
    }

    /**
     * Put data
     *
     * @param url             The url to PUT
     * @param queryParameters The query parameters
     * @param body The body to use
     * @return The client to use
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    public static HttpClient put(String url, Map<String, String> queryParameters, byte[] body)
            throws UnauthorizedException {
        var client = HttpClient.create(safeUrl(url, queryParameters), "PUT");
        client.setRequestBody(Objects.requireNonNullElse(body, new byte[0]));
        sign(client);
        return client;
    }

    /**
     * POST data
     *
     * @param url             The URL to POST
     * @param queryParameters The query parameters to be put in the body
     * @return The client to use
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    public static HttpClient post(String url, Map<String, String> queryParameters) throws UnauthorizedException {
        var client = HttpClient.create(safeUrl(url, Collections.emptyMap()), "POST");
        client.setRequestBody(query(queryParameters).substring(1).getBytes(StandardCharsets.UTF_8));
        sign(client);
        return client;
    }

    /** Create a POST request with encoded query parameters and an explicit body. */
    public static HttpClient post(String url, Map<String, String> queryParameters, byte[] body)
            throws UnauthorizedException {
        final var client = HttpClient.create(safeUrl(url, queryParameters), "POST");
        client.setRequestBody(Objects.requireNonNullElse(body, new byte[0]));
        sign(client);
        return client;
    }

    /**
     * DELETE data
     *
     * @param url The URL to DELETE
     * @return The client to use
     * @throws UnauthorizedException if the user hasn't logged in to MapRoulette
     */
    public static HttpClient delete(String url) throws UnauthorizedException {
        var client = HttpClient.create(safeUrl(url, Collections.emptyMap()), "DELETE");
        sign(client);
        return client;
    }
}
