package io.songlinks;

import com.fasterxml.jackson.databind.JsonNode;
import io.songlinks.model.PersonWithdrawnResponse;

/**
 * Thrown when a person ID has been withdrawn (HTTP 410). The API still answers in full: {@link #withdrawn()} says when
 * the ID was issued and withdrawn, and gives its history. It is a valid answer to keep.
 */
public class PersonWithdrawnException extends SonglinksException {
    private static final long serialVersionUID = 1L;

    private final transient PersonWithdrawnResponse withdrawn;

    /**
     * Creates the exception for a 410 answer.
     *
     * @param withdrawn the API's answer, read
     * @param body      the same answer as JSON
     */
    public PersonWithdrawnException(PersonWithdrawnResponse withdrawn, JsonNode body) {
        super(410, "withdrawn", "Person " + withdrawn.personId() + " was withdrawn", body);
        this.withdrawn = withdrawn;
    }

    /**
     * The API's answer about the withdrawn ID.
     *
     * @return when the ID was issued and withdrawn, and its history
     */
    public PersonWithdrawnResponse withdrawn() {
        return withdrawn;
    }
}
