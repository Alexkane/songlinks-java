package io.songlinks;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Thrown when the API answers with an error. Branch on {@link #code()}, the API's own error code; the message is a
 * sentence meant for people, and its wording may change.
 */
public class SonglinksException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The HTTP status of the answer. */
    private final int status;
    /** The API's error code. */
    private final String code;
    private final transient JsonNode body;

    /**
     * Creates the exception for one error answer.
     *
     * @param status  the HTTP status
     * @param code    the API's error code
     * @param message the API's message, or a description of the failure when it sent none
     * @param body    the answer's body, or null when it had none
     */
    public SonglinksException(int status, String code, String message, JsonNode body) {
        super(message);
        this.status = status;
        this.code = code;
        this.body = body;
    }

    /**
     * The HTTP status of the answer.
     *
     * @return the status, such as 404 or 503
     */
    public int status() {
        return status;
    }

    /**
     * The API's error code, the value to branch on.
     *
     * @return a code such as {@code not_found}, {@code busy} or {@code too_many}
     */
    public String code() {
        return code;
    }

    /**
     * The whole answer, as JSON.
     *
     * @return the body, or null when the answer had none
     */
    public JsonNode body() {
        return body;
    }
}
