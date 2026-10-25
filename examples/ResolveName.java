// Who is this songwriter? Print each person a name could be, with the evidence.
//
//   scripts/run-example.sh ResolveName "mark hoppus"
import io.songlinks.Songlinks;
import io.songlinks.model.Cluster;
import io.songlinks.model.Evidence;
import io.songlinks.model.LookupRecord;
import io.songlinks.model.LookupResponse;
import java.util.TreeSet;

public class ResolveName {
    public static void main(String[] args) {
        Songlinks songlinks = Songlinks.builder().userAgent("songlinks-example/1.0").build();
        LookupResponse answer = songlinks.lookup(args.length > 0 ? args[0] : "mark hoppus");

        if ("rejected".equals(answer.answer())) {
            // Not an error: the API explains why it gave no candidates, such as a name too common to group.
            System.out.println("No answer: " + answer.rejection().code() + " - nearest: " + answer.rejection().nearest());
            return;
        }
        for (Cluster person : answer.candidates()) {
            // persistentId() is null when no ID has been issued yet, which is common; the records are still the answer.
            String id = person.persistentId() != null ? person.persistentId() : "(no person ID yet)";
            System.out.printf("%s  %s  %d works%n", id, String.join(", ", person.names()), person.works());
            for (LookupRecord r : person.records()) {
                String ipi = r.ipiNameNumber() != null ? r.ipiNameNumber() : "-";
                System.out.printf("    %-30s IPI %-12s %d works%n", r.name(), ipi, r.works());
            }
            TreeSet<String> rules = new TreeSet<>();
            for (Evidence e : person.evidence()) rules.add(e.rule());
            System.out.println("    joined by: " + (rules.isEmpty() ? "nothing to join: one record" : String.join(", ", rules)));
        }
        // Pairs the model would not join are shown apart, never as part of a person.
        if (!answer.review().isEmpty()) System.out.printf("%n%d pair(s) held apart for review.%n", answer.review().size());
    }
}
