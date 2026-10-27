# Changelog

## 0.2.0 (unreleased)

- `songwriters(isrcs)`, for `GET /v1/songwriters`: up to 50 recordings by ISRC, each with the works it is registered
  to, their writers and the person behind each writer. It refuses an empty list, more than `SONGWRITERS_MAX` (50)
  entries and an entry the API would split, before calling, as `writers()` does. New records `SongwritersResponse`,
  `RecordingAnswer`, `RecordingWork` and `RecordingWriter`, generated from the API's document.
- `Songlinks.normalizeIsrc()` writes an ISRC the way a songwriters answer does, to match answers to your list.
- `examples/WhoWrote.java`: ISRCs in, the songs and writers behind each recording out.
- A 429's wait is read from its body when it has no Retry-After header (the edge's rate limit sends none), and a
  wait longer than a minute is thrown at once instead of being retried early.
- A 422 says which parameter was wrong and why, instead of "HTTP 422".
- `lookup()`, `person()` and `personWorks()` refuse a blank argument before calling the API.
- `Songlinks.normalizeIpi()` writes a number the way a writers answer does, to match answers to your list.
- Record-ID lists (`Evidence.records()`, `ReviewItem.records()`, `PersonWork.records()`) are `List<Long>`, matching
  `LookupRecord.partyRecordId()`, so `contains()` finds them.

## 0.1.0 (unreleased)

The first release, against Songlinks API 0.11.0.

- `lookup`, `person`, `personWorks`, `writers` and `version`, with immutable records generated from the API's OpenAPI
  document and checked against it on every build.
- Typed exceptions: `SonglinksException`, `RateLimitedException` and `PersonWithdrawnException`.
- Retries on 429, 503 and network failures, honouring `Retry-After`.
- Java 17+, with Jackson as the only runtime dependency.
