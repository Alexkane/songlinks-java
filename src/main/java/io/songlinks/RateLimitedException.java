package io.songlinks;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Optional;

/**
 * Thrown when the API refuses a request because the caller is over a limit (HTTP 429). The public API allows about
 * 100 requests per 5 minutes from one address, across every endpoint, and 20 from VPNs, proxies and hosting providers
 * (which includes most cloud servers). When the API asks for a wait of a minute or less, the client waits and retries
 * by itself and throws this only when its retries run out; a longer wait is thrown at once, with {@link #retryAfter()}
 * the most you need to wait.
 */
public class RateLimitedException extends SonglinksException {
    private static final long serialVersionUID = 1L;

    /** How long the API asked the caller to wait, or null. */
    private final Duration retryAfter;

    /**
     * Creates the exception for a 429 answer.
     *
     * @param message    the API's message
     * @param body       the answer's body, or null when it had none
     * @param retryAfter how long the API asked the caller to wait, or null when it did not say
     */
    public RateLimitedException(String message, JsonNode body, Duration retryAfter) {
        super(429, "rate_limited", message, body);
        this.retryAfter = retryAfter;
    }

    /**
     * How long the API asked the caller to wait before trying again.
     *
     * @return the wait, or empty when the API did not say
     */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
