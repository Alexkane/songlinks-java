package io.songlinks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.songlinks.model.LookupResponse;
import io.songlinks.model.RecordingAnswer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Against the real API, only when asked: SONGLINKS_LIVE=1 ./mvnw test. Shapes, not counts: counts move with reloads. */
@EnabledIfEnvironmentVariable(named = "SONGLINKS_LIVE", matches = "1")
class LiveTest {
    private final Songlinks sl = Songlinks.builder().userAgent("songlinks-java-tests").build();

    @Test
    void versionNamesTheModelAndTheDataDate() {
        assertTrue(sl.version().modelMd5().matches("[0-9a-f]{32}"));
    }

    @Test
    void aLookupAndAPersonRead() {
        LookupResponse r = sl.lookup("mark hoppus", LookupOptions.defaults().withRoutes(List.of()));
        assertEquals("candidates", r.answer());
        String id = r.candidates().stream().map(c -> c.persistentId()).filter(x -> x != null).findFirst().orElseThrow();
        assertFalse(sl.person(id, 0, 5).worksList().items().isEmpty());
        assertEquals(7, sl.personWorks(id, 5).limit(7).count());
    }

    @Test
    void aSongwritersCheckAnswersEachIsrcOnce() {
        List<RecordingAnswer> r = sl.songwriters(List.of("US-MC1-99-59123", "usmc19959123", "nope")).recordings();
        assertEquals(List.of("USMC19959123", "nope"), r.stream().map(RecordingAnswer::isrc).toList());
        assertEquals("registered", r.get(0).status());
        assertFalse(r.get(0).works().get(0).writers().isEmpty());
        assertEquals("invalid", r.get(1).status());
    }
}
