// Who wrote these recordings? Give ISRCs; get the songs each is registered to, and their writers.
//
//   scripts/run-example.sh WhoWrote USMC19959123 US-SM1-95-00008
import io.songlinks.Songlinks;
import io.songlinks.model.RecordingAnswer;
import io.songlinks.model.RecordingWork;
import io.songlinks.model.RecordingWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WhoWrote {
    public static void main(String[] args) {
        Songlinks songlinks = Songlinks.builder().userAgent("songlinks-example/1.0").build();
        List<String> isrcs = args.length > 0 ? List.of(args) : List.of("USMC19959123");

        Map<String, RecordingAnswer> answers = new HashMap<>();
        for (int i = 0; i < isrcs.size(); i += Songlinks.SONGWRITERS_MAX) {
            List<String> batch = isrcs.subList(i, Math.min(i + Songlinks.SONGWRITERS_MAX, isrcs.size()));
            for (RecordingAnswer a : songlinks.songwriters(batch).recordings()) answers.put(a.isrc(), a);
        }

        for (String isrc : isrcs) {
            // The answer writes each ISRC as 12 characters, upper case, with repeats merged, so look yours up that way.
            RecordingAnswer a = answers.get(Songlinks.normalizeIsrc(isrc));
            System.out.println(isrc + ": " + a.status());   // registered, unregistered or invalid
            for (RecordingWork w : a.works()) {
                System.out.printf("    %s  %s  ISWC %s%n", w.songCode(), w.title(), w.iswc() != null ? w.iswc() : "-");
                for (RecordingWriter x : w.writers()) {
                    // persistentId() is null when the record is not part of a person yet, often a writer with no IPI.
                    String id = x.persistentId() != null ? x.persistentId() : "(no person ID yet)";
                    String ipi = x.ipiNameNumber() != null ? x.ipiNameNumber() : "-";
                    System.out.printf("        %-28s IPI %-12s %s%n", x.name(), ipi, id);
                }
            }
        }
    }
}
