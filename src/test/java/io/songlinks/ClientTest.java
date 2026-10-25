package io.songlinks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.songlinks.model.PersonWork;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The client against a local server that answers from a script and records every request it was sent. */
class ClientTest {
    record Reply(int status, String body, Map<String, String> headers) {
        static Reply json(int status, String body) {
            return new Reply(status, body, Map.of());
        }
    }

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<URI> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> userAgents = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI());
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            Reply r = replies.poll();
            if (r == null) r = Reply.json(500, "{\"error\":\"internal\",\"message\":\"no scripted reply\"}");
            byte[] bytes = r.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            r.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(r.status(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Songlinks client(int maxRetries) {
        return Songlinks.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/")
                .maxRetries(maxRetries)
                .timeout(Duration.ofSeconds(5))
                .userAgent("test-app/1.0")
                .build();
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (uri.getRawQuery() == null) return out;
        for (String pair : uri.getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void lookupSendsTheNameAndOptionsAsQueryParameters() {
        replies.add(Reply.json(200, "{\"answer\":\"candidates\",\"candidates\":[]}"));
        client(0).lookup("mark hoppus", LookupOptions.song("all the small things").withThreshold(0.9)
                .withRoutes(List.of()));
        URI u = requests.get(0);
        assertEquals("/v1/lookup", u.getPath());
        assertEquals(Map.of("name", "mark hoppus", "song", "all the small things", "threshold", "0.9",
                "routes", "none"), query(u));
    }

    @Test
    void optionsNotGivenAreLeftToTheApi() {
        replies.add(Reply.json(200, "{}"));
        client(0).lookup("amy allen");
        assertEquals(List.of("name"), List.copyOf(query(requests.get(0)).keySet()));
    }

    @Test
    void personEncodesTheIdAndMapsPaging() {
        replies.add(Reply.json(200, "{\"works_list\":{\"items\":[],\"next_offset\":null}}"));
        client(0).person("SLP-ZYM7-GD3E-S", 100, 50);
        assertEquals("/v1/person/SLP-ZYM7-GD3E-S", requests.get(0).getPath());
        assertEquals(Map.of("works_offset", "100", "works_limit", "50"), query(requests.get(0)));
    }

    @Test
    void writersJoinsWithCommasAndRefusesMoreThanTheApiTakes() {
        replies.add(Reply.json(200, "{\"writers\":[]}"));
        Songlinks sl = client(0);
        sl.writers(List.of("00338980326", "00340247397"));
        assertEquals("00338980326,00340247397", query(requests.get(0)).get("ipi"));
        assertThrows(IllegalArgumentException.class, () -> sl.writers(Collections.nCopies(51, "1")));
        assertThrows(IllegalArgumentException.class, () -> sl.writers(List.of()));
    }

    @Test
    void identifiesItselfWithTheCallersTagAppended() {
        replies.add(Reply.json(200, "{}"));
        client(0).version();
        assertEquals("songlinks-java/" + Songlinks.VERSION + " test-app/1.0", userAgents.get(0));
    }

    @Test
    void personWorksFollowsNextOffsetLazily() {
        replies.add(Reply.json(200, "{\"works_list\":{\"items\":[{\"mlc_work_id\":\"A\",\"title\":\"A\"},"
                + "{\"mlc_work_id\":\"B\",\"title\":\"B\"}],\"next_offset\":2}}"));
        replies.add(Reply.json(200, "{\"works_list\":{\"items\":[{\"mlc_work_id\":\"C\",\"title\":\"C\"}],"
                + "\"next_offset\":null}}"));
        List<String> titles = client(0).personWorks("SLP-ZYM7-GD3E-S", 2).map(PersonWork::title).toList();
        assertEquals(List.of("A", "B", "C"), titles);
        assertEquals(List.of("0", "2"), requests.stream().map(u -> query(u).get("works_offset")).toList());
    }

    @Test
    void personWorksReadsNothingUntilConsumed() {
        client(0).personWorks("SLP-ZYM7-GD3E-S");
        assertTrue(requests.isEmpty());
    }

    @Test
    void anApiErrorCarriesStatusCodeAndMessage() {
        replies.add(Reply.json(404, "{\"error\":\"not_found\",\"message\":\"No person has this ID\"}"));
        SonglinksException e = assertThrows(SonglinksException.class, () -> client(0).person("SLP-0000-0000-0"));
        assertEquals(404, e.status());
        assertEquals("not_found", e.code());
        assertEquals("No person has this ID", e.getMessage());
    }

    @Test
    void aWithdrawnPersonThrowsWithTheApisAnswer() {
        replies.add(Reply.json(410, "{\"person_id\":\"SLP-AAAA-BBBB-C\",\"state\":\"withdrawn\",\"history\":[]}"));
        SonglinksException e = assertThrows(SonglinksException.class, () -> client(0).person("SLP-AAAA-BBBB-C"));
        PersonWithdrawnException w = assertInstanceOf(PersonWithdrawnException.class, e);
        assertEquals("SLP-AAAA-BBBB-C", w.withdrawn().personId());
    }

    @Test
    void a503IsRetriedAndTheAnswerThatFollowsReturned() {
        replies.add(Reply.json(503, "{\"error\":\"busy\",\"message\":\"busy\"}"));
        replies.add(Reply.json(200, "{\"version\":\"0.11.0\",\"model_md5\":\"m\",\"code_commit\":\"c\","
                + "\"threshold\":0.85,\"review_threshold\":0.5,\"max_candidates\":15,\"latency_ms\":0}"));
        assertEquals("0.11.0", client(2).version().version());
        assertEquals(2, requests.size());
    }

    @Test
    void a429HonoursRetryAfterAndThrowsWhenRetriesRunOut() {
        Reply limited = new Reply(429, "{\"message\":\"That's 10 lookups\"}", Map.of("Retry-After", "0"));
        replies.add(limited);
        replies.add(limited);
        RateLimitedException e = assertThrows(RateLimitedException.class, () -> client(1).lookup("x y"));
        assertEquals(Duration.ZERO, e.retryAfter().orElseThrow());
        assertEquals(2, requests.size());
    }

    @Test
    void anOther4xxIsNeverRetried() {
        replies.add(Reply.json(400, "{\"error\":\"too_many\",\"message\":\"at most 50\"}"));
        assertThrows(SonglinksException.class, () -> client(2).writers(List.of("1")));
        assertEquals(1, requests.size());
    }

    @Test
    void backoffHonoursRetryAfter() {
        assertEquals(Duration.ofSeconds(7), Songlinks.backoff(0, Duration.ofSeconds(7)));
        assertFalse(Songlinks.backoff(1, null).isNegative());
    }

    @Test
    void theEdgesLongWaitInTheBodyIsThrownAtOnce() {
        replies.add(Reply.json(429, "{\"error\":\"rate_limited\",\"message\":\"Too many\",\"retry_after_seconds\":300}"));
        RateLimitedException e = assertThrows(RateLimitedException.class, () -> client(2).lookup("x y"));
        assertEquals(Duration.ofSeconds(300), e.retryAfter().orElseThrow());
        assertEquals(1, requests.size());
    }

    @Test
    void aShortWaitInTheBodyIsWaitedOut() {
        replies.add(Reply.json(429, "{\"retry_after_seconds\":0}"));
        replies.add(Reply.json(200, "{\"version\":\"0.11.0\",\"model_md5\":\"m\",\"code_commit\":\"c\","
                + "\"threshold\":0.85,\"review_threshold\":0.5,\"max_candidates\":15,\"latency_ms\":0}"));
        assertEquals("0.11.0", client(2).version().version());
        assertEquals(2, requests.size());
    }

    @Test
    void a503AskingForMoreThanAMinuteIsThrownAtOnce() {
        replies.add(new Reply(503, "{\"error\":\"busy\",\"message\":\"Busy.\"}", Map.of("Retry-After", "120")));
        SonglinksException e = assertThrows(SonglinksException.class, () -> client(2).version());
        assertEquals(503, e.status());
        assertEquals(1, requests.size());
    }

    @Test
    void theHeaderWinsOverTheBody() {
        replies.add(new Reply(429, "{\"retry_after_seconds\":300}", Map.of("Retry-After", "0")));
        replies.add(Reply.json(200, "{\"version\":\"0.11.0\",\"model_md5\":\"m\",\"code_commit\":\"c\","
                + "\"threshold\":0.85,\"review_threshold\":0.5,\"max_candidates\":15,\"latency_ms\":0}"));
        assertEquals("0.11.0", client(2).version().version());
        assertEquals(2, requests.size());
    }

    @Test
    void a422SaysWhichParameterWasWrong() {
        replies.add(Reply.json(422, "{\"detail\":[{\"loc\":[\"query\",\"threshold\"],"
                + "\"msg\":\"Input should be less than or equal to 0.99\"}]}"));
        SonglinksException e = assertThrows(SonglinksException.class,
                () -> client(0).lookup("x y", LookupOptions.defaults().withThreshold(2)));
        assertEquals("invalid_request", e.code());
        assertEquals("threshold: Input should be less than or equal to 0.99", e.getMessage());
    }

    @Test
    void aBlankNameOrIdIsRefusedBeforeAnyRequest() {
        assertThrows(IllegalArgumentException.class, () -> client(0).lookup(" "));
        assertThrows(IllegalArgumentException.class, () -> client(0).person(""));
        assertThrows(IllegalArgumentException.class, () -> client(0).personWorks(" "));
        assertEquals(0, requests.size());
    }

    @Test
    void normalizeIpiWritesANumberTheWayTheAnswerDoes() {
        assertEquals("00338980326", Songlinks.normalizeIpi("338980326"));
        assertEquals("00338980326", Songlinks.normalizeIpi("003-389-803 26"));
        assertEquals("00338980326", Songlinks.normalizeIpi("\t003\u2013389803 26")); // a tab and an en dash
        assertEquals("abc", Songlinks.normalizeIpi(" abc ")); // invalid: echoed trimmed, as the API does
        assertEquals("15036818.0", Songlinks.normalizeIpi("15036818.0"));
        assertEquals("123456789012", Songlinks.normalizeIpi("123456789012"));
    }
}
