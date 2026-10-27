package io.songlinks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.songlinks.model.Cluster;
import io.songlinks.model.LookupResponse;
import io.songlinks.model.PersonResponse;
import io.songlinks.model.RecordingAnswer;
import io.songlinks.model.RecordingWork;
import io.songlinks.model.SongwritersResponse;
import io.songlinks.model.VersionResponse;
import io.songlinks.model.WritersResponse;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The generated records against the API's OpenAPI document, and against real answers captured from the API. */
class ModelTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    // scripts/gen_models.py's renames: the record class for each schema.
    private static final Map<String, String> CLASSES = Map.ofEntries(
            Map.entry("LookupResponse", "LookupResponse"), Map.entry("Query", "Query"), Map.entry("Cluster", "Cluster"),
            Map.entry("Record", "LookupRecord"), Map.entry("Evidence", "Evidence"), Map.entry("ReviewItem", "ReviewItem"),
            Map.entry("Link", "Link"), Map.entry("Rejection", "Rejection"), Map.entry("Provenance", "Provenance"),
            Map.entry("SongMatch", "SongMatch"), Map.entry("SongRecord", "SongRecord"),
            Map.entry("SongBeyond", "SongBeyond"), Map.entry("RateLimit", "RateLimit"),
            Map.entry("PersonResponse", "PersonResponse"), Map.entry("PersonRecord", "PersonRecord"),
            Map.entry("PersonWork", "PersonWork"), Map.entry("WorksPage", "WorksPage"),
            Map.entry("PersonEvent", "PersonEvent"), Map.entry("PersonProvenance", "PersonProvenance"),
            Map.entry("BuildRef", "BuildRef"), Map.entry("PersonWithdrawnResponse", "PersonWithdrawnResponse"),
            Map.entry("WritersResponse", "WritersResponse"), Map.entry("WriterAnswer", "WriterAnswer"),
            Map.entry("WritersSummary", "WritersSummary"), Map.entry("VersionResponse", "VersionResponse"),
            Map.entry("Error", "ApiError"), Map.entry("RateLimited", "RateLimited"),
            Map.entry("SongwritersResponse", "SongwritersResponse"), Map.entry("RecordingAnswer", "RecordingAnswer"),
            Map.entry("RecordingWork", "RecordingWork"), Map.entry("RecordingWriter", "RecordingWriter"));

    private static JsonNode resource(String name) throws IOException {
        try (InputStream in = ModelTest.class.getResourceAsStream("/" + name)) {
            return JSON.readTree(in);
        }
    }

    private static <T> T fixture(String name, Class<T> type) throws IOException {
        return JSON.treeToValue(resource("fixtures/" + name), type);
    }

    /** Every record holds exactly its schema's fields: a field the spec gained or lost means regenerate the models. */
    @Test
    void everyRecordMatchesItsSchema() throws Exception {
        JsonNode schemas = resource("openapi.json").get("components").get("schemas");
        for (Map.Entry<String, String> e : CLASSES.entrySet()) {
            Class<?> type = Class.forName("io.songlinks.model." + e.getValue());
            Set<String> fields = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getAccessor)
                    .map(m -> {
                        try {
                            return type.getDeclaredField(m.getName());
                        } catch (NoSuchFieldException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .map(f -> f.getAnnotation(JsonProperty.class))
                    .map(JsonProperty::value)
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<String> spec = new TreeSet<>();
            schemas.get(e.getKey()).get("properties").fieldNames().forEachRemaining(spec::add);
            assertEquals(spec, fields, e.getKey() + " -> " + e.getValue() + ": run scripts/gen_models.py");
        }
    }

    @Test
    void aRealLookupReads() throws IOException {
        LookupResponse r = fixture("lookup.json", LookupResponse.class);
        assertEquals("candidates", r.answer());
        Cluster c = r.candidates().get(0);
        assertEquals("SLP-ZYM7-GD3E-S", c.persistentId());
        assertTrue(c.works() > 300);
        assertFalse(c.records().isEmpty());
        assertEquals("mark hoppus", r.query().name());
        assertEquals(32, r.provenance().modelMd5().length());
    }

    @Test
    void aCandidateOfTwoStoredPersonsHasNoSingleId() throws IOException {
        LookupResponse r = fixture("lookup-two-persons.json", LookupResponse.class);
        Cluster both = r.candidates().stream().filter(c -> c.storedPersons().size() > 1).findFirst().orElseThrow();
        assertNull(both.persistentId());
        assertTrue(both.storedPersons().contains("SLP-MG23-XM9B-Y"));
    }

    @Test
    void aRealPersonReads() throws IOException {
        PersonResponse p = fixture("person.json", PersonResponse.class);
        assertEquals("SLP-ZYM7-GD3E-S", p.personId());
        assertEquals(3, p.worksList().items().size());
        assertFalse(p.records().isEmpty());
    }

    @Test
    void realWritersAndVersionAnswersRead() throws IOException {
        WritersResponse w = fixture("writers.json", WritersResponse.class);
        assertEquals(2, w.writers().size());
        assertEquals(2, w.summary().numbers());
        VersionResponse v = fixture("version.json", VersionResponse.class);
        assertTrue(v.mlcSnapshot().matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    /** A real answer for {@code isrc=USMC19959123,nope}: All the Small Things, and an entry that is not an ISRC. */
    @Test
    void aRealSongwritersAnswerReads() throws IOException {
        SongwritersResponse r = fixture("songwriters.json", SongwritersResponse.class);
        assertEquals(2, r.recordings().size());
        RecordingAnswer found = r.recordings().get(0);
        assertEquals("USMC19959123", found.isrc());
        assertEquals("registered", found.status());
        RecordingWork work = found.works().get(0);
        assertEquals("ALL THE SMALL THINGS", work.title());
        assertEquals("T0708835635", work.iswc());
        assertTrue(work.writers().stream().anyMatch(w -> "SLP-ZYM7-GD3E-S".equals(w.persistentId())
                && "00338980326".equals(w.ipiNameNumber()) && w.partyRecordId() > 0));
        RecordingAnswer invalid = r.recordings().get(1);
        assertEquals("nope", invalid.isrc());
        assertEquals("invalid", invalid.status());
        assertTrue(invalid.works().isEmpty());
        assertNull(r.rateLimit());
    }

    @Test
    void anUnknownFieldIsIgnoredAndAMissingListIsEmpty() throws IOException {
        Cluster c = JSON.readValue("{\"ref\":\"c1\",\"works\":1,\"a_field_from_the_future\":true}", Cluster.class);
        assertTrue(c.storedPersons().isEmpty());
        assertTrue(c.records().isEmpty());
    }
}
