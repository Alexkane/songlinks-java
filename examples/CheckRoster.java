// Check a roster of IPI name numbers: who holds each, and how many works they have.
//
//   scripts/run-example.sh CheckRoster roster.txt > checked.csv
//
// The input is one IPI name number per line. Numbers are sent 50 at a time, the most the API takes in one call.
import io.songlinks.Songlinks;
import io.songlinks.model.WriterAnswer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CheckRoster {
    public static void main(String[] args) throws IOException {
        Songlinks songlinks = Songlinks.builder().userAgent("songlinks-example/1.0").build();
        List<String> ipis = Files.readAllLines(Path.of(args[0])).stream().map(String::strip).filter(s -> !s.isEmpty()).toList();

        Map<String, WriterAnswer> answers = new HashMap<>();
        for (int i = 0; i < ipis.size(); i += Songlinks.WRITERS_MAX) {
            List<String> batch = ipis.subList(i, Math.min(i + Songlinks.WRITERS_MAX, ipis.size()));
            for (WriterAnswer w : songlinks.writers(batch).writers()) answers.put(w.ipiNameNumber(), w);
        }

        System.out.println("ipi,status,person_id,name,works");
        for (String ipi : ipis) {
            // The answer writes each number as 11 digits, with repeats merged, so look yours up in the same form.
            WriterAnswer w = answers.get(Songlinks.normalizeIpi(ipi));
            if (w == null) {
                System.out.println(ipi + ",,,,");
                continue;
            }
            String name = w.name() == null ? "" : "\"" + w.name().replace("\"", "\"\"") + "\"";
            System.out.printf("%s,%s,%s,%s,%s%n", ipi, w.status(), orEmpty(w.personId()), name, orEmpty(w.works()));
        }
    }

    private static String orEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
