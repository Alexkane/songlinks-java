// Two writers share a name: which one wrote this song?
//
//   scripts/run-example.sh WhichNamesake "amy allen" "espresso"
import io.songlinks.LookupOptions;
import io.songlinks.Songlinks;
import io.songlinks.model.Cluster;
import io.songlinks.model.LookupResponse;
import io.songlinks.model.SongMatch;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WhichNamesake {
    public static void main(String[] args) {
        String name = args.length > 1 ? args[0] : "amy allen";
        String song = args.length > 1 ? args[1] : "espresso";
        Songlinks songlinks = Songlinks.builder().userAgent("songlinks-example/1.0").build();
        LookupResponse answer = songlinks.lookup(name, LookupOptions.song(song));

        SongMatch match = answer.songMatch();
        if (match == null || match.candidates().isEmpty()) {
            System.out.printf("No one called \"%s\" in this answer has a work titled \"%s\".%n", name, song);
            return;
        }
        // songMatch() names candidates by their ref() (c1, c2 ...), which is only meaningful inside this one answer.
        Map<String, Cluster> byRef = answer.candidates().stream().collect(Collectors.toMap(Cluster::ref, Function.identity()));
        System.out.printf("\"%s\": %s title match%n", song, match.match());
        for (String ref : match.candidates()) {
            Cluster p = byRef.get(ref);
            System.out.printf("  %s  %s  IPI %s%n", p.persistentId() != null ? p.persistentId() : ref,
                    String.join(", ", p.names()), String.join(", ", p.ipiNameNumbers()));
        }
    }
}
