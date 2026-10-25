// Export every work of a person to CSV.
//
//   scripts/run-example.sh ExportCatalogue SLP-ZYM7-GD3E-S > works.csv
import io.songlinks.Songlinks;

public class ExportCatalogue {
    public static void main(String[] args) {
        Songlinks songlinks = Songlinks.builder().userAgent("songlinks-example/1.0").build();
        System.out.println("mlc_work_id,title,iswc");
        // personWorks() pages through the API 200 works at a time; nothing here needs to know about pages.
        songlinks.personWorks(args.length > 0 ? args[0] : "SLP-ZYM7-GD3E-S")
                .forEach(w -> System.out.printf("%s,%s,%s%n", w.mlcWorkId(), csv(w.title()), w.iswc() == null ? "" : w.iswc()));
    }

    private static String csv(String value) {
        return value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
