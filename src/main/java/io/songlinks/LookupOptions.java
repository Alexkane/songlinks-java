package io.songlinks;

import java.util.List;

/**
 * Options for a lookup. Any option left null is decided by the API.
 *
 * <pre>{@code
 * songlinks.lookup("amy allen", LookupOptions.song("espresso"));
 * songlinks.lookup("mark hoppus", LookupOptions.defaults().withRoutes(List.of()));
 * }</pre>
 *
 * @param song      The title of a song the writer wrote. When given, the answer says which candidates hold a work with
 *                  that title.
 * @param threshold How sure the model must be before it joins two records, from 0.5 to 0.99.
 * @param routes    Extra routes to run. {@code List.of("catalogue")}, the API's default, also returns records
 *                  registered under another name; an empty list skips that and the lookup is faster.
 */
public record LookupOptions(String song, Double threshold, List<String> routes) {

    /** Makes a copy of {@code routes}, so a list changed after the call cannot change the options. */
    public LookupOptions {
        routes = routes == null ? null : List.copyOf(routes);
    }

    /**
     * Options that leave every choice to the API.
     *
     * @return options with no song, threshold or routes set
     */
    public static LookupOptions defaults() {
        return new LookupOptions(null, null, null);
    }

    /**
     * Options that rank the candidates by a song the writer wrote.
     *
     * @param song the song's title
     * @return options with only the song set
     */
    public static LookupOptions song(String song) {
        return new LookupOptions(song, null, null);
    }

    /**
     * A copy of these options with a different song.
     *
     * @param song the song's title, or null for none
     * @return the new options
     */
    public LookupOptions withSong(String song) {
        return new LookupOptions(song, threshold, routes);
    }

    /**
     * A copy of these options with a different threshold.
     *
     * @param threshold from 0.5 to 0.99
     * @return the new options
     */
    public LookupOptions withThreshold(double threshold) {
        return new LookupOptions(song, threshold, routes);
    }

    /**
     * A copy of these options with different routes.
     *
     * @param routes the routes to run; an empty list runs none
     * @return the new options
     */
    public LookupOptions withRoutes(List<String> routes) {
        return new LookupOptions(song, threshold, routes);
    }
}
