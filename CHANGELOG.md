# Changelog

## 0.1.0 (unreleased)

The first release, against Songlinks API 0.11.0.

- `lookup`, `person`, `personWorks`, `writers` and `version`, with immutable records generated from the API's OpenAPI
  document and checked against it on every build.
- Typed exceptions: `SonglinksException`, `RateLimitedException` and `PersonWithdrawnException`.
- Retries on 429, 503 and network failures, honouring `Retry-After`.
- Java 17+, with Jackson as the only runtime dependency.
