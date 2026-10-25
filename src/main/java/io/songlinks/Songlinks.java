package io.songlinks;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.songlinks.model.LookupResponse;
import io.songlinks.model.PersonResponse;
import io.songlinks.model.PersonWithdrawnResponse;
import io.songlinks.model.PersonWork;
import io.songlinks.model.VersionResponse;
import io.songlinks.model.WritersResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * The Songlinks API client. Thread-safe: build one and share it.
 *
 * <pre>{@code
 * Songlinks songlinks = Songlinks.builder().userAgent("my-app/1.0").build();
 * LookupResponse answer = songlinks.lookup("mark hoppus");
 * }</pre>
 */
public final class Songlinks {
    /** This SDK's release, sent in the User-Agent so the API's logs can tell SDK versions apart. */
    public static final String VERSION = "0.1.0";
    /** The most IPI name numbers {@code /v1/writers} accepts in one call. */
    public static final int WRITERS_MAX = 50;
    /** The most works one person read returns; {@link #personWorks} pages through the rest. */
    public static final int WORKS_PAGE_MAX = 200;

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final URI baseUrl;
    private final HttpClient http;
    private final Duration timeout;
    private final int maxRetries;
    private final String userAgent;

    private Songlinks(Builder b) {
        String base = b.baseUrl.replaceAll("/+$", "");
        this.baseUrl = URI.create(base);
        this.http = b.httpClient != null ? b.httpClient : HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)   // a merged person ID is a 307/308 to the survivor
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
        this.userAgent = "songlinks-java/" + VERSION + (b.userAgent == null ? "" : " " + b.userAgent);
    }

    /**
     * Creates a client with every default: https://songlinks.io, a 30-second timeout and two retries.
     *
     * @return a new client
     */
    public static Songlinks create() {
        return builder().build();
    }

    /**
     * Starts building a client with chosen options.
     *
     * @return a builder holding the defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Looks up a songwriter by name.
     *
     * @param name a songwriter's full name, such as {@code mark hoppus}
     * @return the people the name could be, each with the registrations behind them
     * @throws SonglinksException when the API answers with an error
     */
    public LookupResponse lookup(String name) {
        return lookup(name, LookupOptions.defaults());
    }

    /**
     * Looks up a songwriter by name, with options such as a song they wrote.
     *
     * @param name    a songwriter's full name, such as {@code mark hoppus}
     * @param options the song, threshold or routes to use; any left null are decided by the API
     * @return the people the name could be, each with the registrations behind them
     * @throws SonglinksException when the API answers with an error
     */
    public LookupResponse lookup(String name, LookupOptions options) {
        Objects.requireNonNull(name, "name");
        Map<String, String> q = new LinkedHashMap<>();
        q.put("name", name);
        if (options.song() != null) q.put("song", options.song());
        if (options.threshold() != null) q.put("threshold", String.valueOf(options.threshold()));
        if (options.routes() != null) q.put("routes", options.routes().isEmpty() ? "none" : String.join(",", options.routes()));
        return get("/v1/lookup", q, LookupResponse.class);
    }

    /**
     * Reads a person by ID, with the first page of their works. A merged ID is followed to the person it now points
     * at, and the answer's {@code redirectedFrom()} names the ID you asked for.
     *
     * @param personId a person ID, such as {@code SLP-ZYM7-GD3E-S}
     * @return the stored person: their registrations, names, IPI name numbers and works
     * @throws PersonWithdrawnException when the ID has been withdrawn
     * @throws SonglinksException       when the API answers with any other error, such as {@code not_found}
     */
    public PersonResponse person(String personId) {
        return person(personId, null, null);
    }

    /**
     * Reads a person by ID, with one chosen page of their works.
     *
     * @param personId    a person ID, such as {@code SLP-ZYM7-GD3E-S}
     * @param worksOffset the position of the first work to return, from 0; null for the first page
     * @param worksLimit  how many works to return, from 1 to 200; null for the API's default
     * @return the stored person, with the requested page of works
     * @throws PersonWithdrawnException when the ID has been withdrawn
     * @throws SonglinksException       when the API answers with any other error
     */
    public PersonResponse person(String personId, Integer worksOffset, Integer worksLimit) {
        Objects.requireNonNull(personId, "personId");
        Map<String, String> q = new LinkedHashMap<>();
        if (worksOffset != null) q.put("works_offset", String.valueOf(worksOffset));
        if (worksLimit != null) q.put("works_limit", String.valueOf(worksLimit));
        return get("/v1/person/" + encode(personId), q, PersonResponse.class);
    }

    /**
     * Streams every work of a person, in the API's order. Pages are requested only as the stream is read, so taking
     * the first few works costs one request.
     *
     * @param personId a person ID, such as {@code SLP-ZYM7-GD3E-S}
     * @return the person's works, 200 to a request
     * @throws SonglinksException when the API answers with an error while the stream is read
     */
    public Stream<PersonWork> personWorks(String personId) {
        return personWorks(personId, WORKS_PAGE_MAX);
    }

    /**
     * Streams every work of a person, with a chosen number of works to a request.
     *
     * @param personId a person ID, such as {@code SLP-ZYM7-GD3E-S}
     * @param pageSize how many works to request at a time, from 1 to 200
     * @return the person's works
     * @throws SonglinksException when the API answers with an error while the stream is read
     */
    public Stream<PersonWork> personWorks(String personId, int pageSize) {
        Iterator<PersonWork> works = new Iterator<>() {
            private Iterator<PersonWork> page = List.<PersonWork>of().iterator();
            private Integer nextOffset = 0;

            @Override
            public boolean hasNext() {
                while (!page.hasNext() && nextOffset != null) {
                    PersonResponse p = person(personId, nextOffset, pageSize);
                    page = p.worksList().items().iterator();
                    nextOffset = p.worksList().nextOffset();
                }
                return page.hasNext();
            }

            @Override
            public PersonWork next() {
                if (!hasNext()) throw new NoSuchElementException();
                return page.next();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(works, Spliterator.ORDERED), false);
    }

    /**
     * Checks up to 50 IPI name numbers at once. The API refuses a longer list rather than shortening it, and so does
     * this method: for more, call it once for each batch of {@link #WRITERS_MAX}.
     *
     * @param ipiNameNumbers the numbers to check; leading zeros may be left off
     * @return for each number, the stored person who holds it and a status
     * @throws IllegalArgumentException when the list is empty or holds more than 50 numbers
     * @throws SonglinksException       when the API answers with an error
     */
    public WritersResponse writers(List<String> ipiNameNumbers) {
        if (ipiNameNumbers.isEmpty()) throw new IllegalArgumentException("writers() needs at least one IPI name number");
        if (ipiNameNumbers.size() > WRITERS_MAX) {
            throw new IllegalArgumentException("writers() takes at most " + WRITERS_MAX + " IPI name numbers per call");
        }
        return get("/v1/writers", Map.of("ipi", String.join(",", ipiNameNumbers)), WritersResponse.class);
    }

    /**
     * Asks what is serving.
     *
     * @return the API's release, the model and code in use, and the date of the registration data
     * @throws SonglinksException when the API answers with an error
     */
    public VersionResponse version() {
        return get("/v1/version", Map.of(), VersionResponse.class);
    }

    private <T> T get(String path, Map<String, String> query, Class<T> type) {
        String qs = query.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        URI uri = URI.create(baseUrl + path + (qs.isEmpty() ? "" : "?" + qs));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // A network failure or timeout: retried like a 503, then rethrown.
                if (attempt < maxRetries) {
                    sleep(backoff(attempt, null));
                    continue;
                }
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while calling the Songlinks API", e);
            }
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                try {
                    return JSON.readValue(response.body(), type);
                } catch (IOException e) {
                    throw new SonglinksException(status, "unreadable_answer", "The API's answer could not be read: "
                            + e.getMessage(), null);
                }
            }
            Duration retryAfter = parseRetryAfter(response.headers().firstValue("Retry-After").orElse(null));
            if ((status == 429 || status == 503) && attempt < maxRetries) {
                sleep(backoff(attempt, retryAfter));
                continue;
            }
            throw toException(status, response.body(), retryAfter);
        }
    }

    private static SonglinksException toException(int status, String text, Duration retryAfter) {
        JsonNode body = null;
        try {
            body = text == null || text.isEmpty() ? null : JSON.readTree(text);
        } catch (IOException ignored) {
            // Not JSON (a proxy's error page): the status alone says what happened.
        }
        String message = body != null && body.hasNonNull("message") ? body.get("message").asText() : "HTTP " + status;
        if (status == 429) return new RateLimitedException(message, body, retryAfter);
        if (status == 410 && body != null && body.hasNonNull("person_id")) {
            try {
                return new PersonWithdrawnException(JSON.treeToValue(body, PersonWithdrawnResponse.class), body);
            } catch (IOException ignored) {
                // Falls through to the plain exception below.
            }
        }
        // 422 is FastAPI's validation answer, with `detail` rather than `error`.
        String code = body != null && body.hasNonNull("error") ? body.get("error").asText()
                : status == 422 ? "invalid_request" : "http_" + status;
        return new SonglinksException(status, code, message, body);
    }

    private static Duration parseRetryAfter(String value) {
        if (value == null) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Retry-After when the API gives one (capped at a minute), else 0.5 s, 1 s, 2 s ... with jitter. */
    static Duration backoff(int attempt, Duration retryAfter) {
        if (retryAfter != null) return retryAfter.compareTo(Duration.ofMinutes(1)) > 0 ? Duration.ofMinutes(1) : retryAfter;
        double ms = 500 * Math.pow(2, attempt) * (0.75 + ThreadLocalRandom.current().nextDouble() * 0.5);
        return Duration.ofMillis((long) ms);
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to retry", e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Builds a {@link Songlinks} client. Every option has a default, so set only the ones you need. */
    public static final class Builder {
        private String baseUrl = "https://songlinks.io";
        private HttpClient httpClient;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private String userAgent;

        private Builder() {
        }

        /**
         * Sets the API's address.
         *
         * @param baseUrl the address; the default is https://songlinks.io
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl);
            return this;
        }

        /**
         * Uses your own HTTP client, for example to share a connection pool or set a proxy.
         *
         * @param httpClient the client; build it with {@code Redirect.NORMAL} so merged person IDs are followed
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Sets how long one request may take before it is abandoned.
         *
         * @param timeout the limit; the default is 30 seconds
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        /**
         * Sets how many times a request is retried after a 429, a 503 or a network failure.
         *
         * @param maxRetries the number of retries; the default is 2, and 0 turns retrying off
         * @return this builder
         * @throws IllegalArgumentException when the number is negative
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be 0 or more");
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Names your app in each request's User-Agent, after the SDK's own name and version.
         *
         * @param userAgent your app's name and version, such as {@code my-app/1.2}
         * @return this builder
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Builds the client.
         *
         * @return a client with this builder's options; it is thread-safe, so build one and share it
         */
        public Songlinks build() {
            return new Songlinks(this);
        }
    }
}
