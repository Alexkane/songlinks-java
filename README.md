# Songlinks Java SDK

[![ci](https://github.com/Alexkane/songlinks-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Alexkane/songlinks-java/actions/workflows/ci.yml)
[![licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)

**Find the songwriter behind a name.** The official Java SDK for [Songlinks](https://songlinks.io).

One songwriter is rarely one record. They're registered under different spellings, under several IPI name numbers, or
with no number at all. Songlinks groups those registrations into one person, shows the evidence for every join, and
keeps apart the ones it isn't sure of.

- **Free, with no key and no sign-up.**
- **Typed.** Immutable records for every answer, checked against the API's OpenAPI document on every build.
- **Light.** Java 17+, the JDK's own `HttpClient`, and Jackson for JSON. Nothing else.
- **Built to be called from real services.** Typed exceptions, retries that honour `Retry-After`, lazy paging, and
  thread-safe: build one client and share it.

```xml
<dependency>
  <groupId>io.songlinks</groupId>
  <artifactId>songlinks</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle: `implementation("io.songlinks:songlinks:0.1.0")`

## Thirty seconds

```java
import io.songlinks.Songlinks;
import io.songlinks.model.*;
import java.util.List;

Songlinks songlinks = Songlinks.builder().userAgent("my-app/1.0").build();

LookupResponse answer = songlinks.lookup("mark hoppus");
Cluster person = answer.candidates().get(0);
// person.persistentId() = "SLP-ZYM7-GD3E-S", person.names() = [MARK HOPPUS],
// person.ipiNameNumbers() = [00338980326], person.works() = 302, person.records(): 2 registrations

songlinks.person("SLP-ZYM7-GD3E-S", 0, 3).worksList().items().forEach(w -> System.out.println(w.title()));
// I MISS YOU / ALL THE SMALL THINGS / DOWN

WritersResponse check = songlinks.writers(List.of("00338980326", "00340247397"));
// one answer per IPI name number: MARK HOPPUS (SLP-ZYM7-GD3E-S), TRAVIS L BARKER (SLP-MG23-XM9B-Y)
```

Every person has a page you can link to, such as [songlinks.io/person/SLP-ZYM7-GD3E-S](https://songlinks.io/person/SLP-ZYM7-GD3E-S).
It shows the registrations behind the person and what each join rests on.

> **Beta.** Unverified machine output, from a dated snapshot of registration data (every answer's `mlcSnapshot` gives
> its date). Some answers are wrong and some are incomplete. Not for rights or royalty decisions.

### More

```java
// Which of several writers with the same name wrote a song? Pass one of their songs.
SongMatch match = songlinks.lookup("amy allen", LookupOptions.song("espresso")).songMatch();
// match.match() = "exact", match.candidates() = [c1]: the Amy Allen credited on that song

// Every work of a person, read lazily, a page at a time.
songlinks.personWorks("SLP-ZYM7-GD3E-S").forEach(w -> System.out.println(w.title()));
```

## What it covers

| Method | API | What it answers |
| --- | --- | --- |
| `lookup(name)`, `lookup(name, LookupOptions)` | `GET /v1/lookup` | The people a songwriter's name could be. A song the writer wrote ranks the candidates by it. |
| `person(id)`, `person(id, offset, limit)` | `GET /v1/person/{id}` | One person: their registrations, names, IPI name numbers and a page of works. |
| `personWorks(id)` | `GET /v1/person/{id}` | Every work of a person, as a lazy `Stream`. |
| `writers(ipiNameNumbers)` | `GET /v1/writers` | Up to 50 IPI name numbers checked at once: the stored person behind each. |
| `version()` | `GET /v1/version` | What is serving: the release, the model and the date of the registration data. |

### Reading an answer

- **A lookup candidate is not a stored person.** It is the lookup's own grouping, made for the name you asked.
  `persistentId()` is the stored person when there is exactly one. A candidate whose records belong to more than one
  stored person has no `persistentId()` and lists them in `storedPersons()`. Read each record's own `persistentId()`.
- **Null is ordinary.** Many records have no person ID yet. Lists are never null; an absent list is empty.
- **Person IDs can be prerelease.** A prerelease ID may stop resolving, or point at a different person, without notice.
  Check `prerelease()` before you store one.

## Errors and retries

Every non-success answer throws an unchecked `SonglinksException` with the HTTP `status()`, the API's `code()` (its
`error` field, the one to branch on), a message for people, and the answer's `body()` as JSON.

```java
try {
    songlinks.person("SLP-0000-0000-0");
} catch (PersonWithdrawnException e) {
    System.out.println("withdrawn " + e.withdrawn().withdrawnAt()); // a 410, and a valid answer to keep
} catch (RateLimitedException e) {
    System.out.println("retry in " + e.retryAfter());
} catch (SonglinksException e) {
    System.out.println(e.status() + " " + e.code() + ": " + e.getMessage());
}
```

- **Retried automatically:** `429` and `503` answers and network failures, up to `maxRetries` times (default 2). A
  `Retry-After` header is honoured, up to a minute; otherwise it backs off from half a second.
- **Never retried:** any other error.
- **Merged person IDs** are followed to the person they now point at, and `redirectedFrom()` names the ID you asked for.

## Options

```java
Songlinks songlinks = Songlinks.builder()
        .baseUrl("https://songlinks.io")      // the default
        .timeout(Duration.ofSeconds(30))      // per request
        .maxRetries(2)                        // 0 turns retrying off
        .userAgent("my-app/1.2")              // appended to songlinks-java/<version>
        .httpClient(myHttpClient)             // your own; give it Redirect.NORMAL
        .build();
```

## Fair use

The API is free and open. Please send a `userAgent` that names your app, keep to one request at a time per user
action, and cache answers you reuse. A burst of lookups may be rate-limited.

## Links

- [API reference](https://songlinks.io/v1/docs), with every endpoint runnable from the page.
- [How Songlinks decides](https://songlinks.io/about): what a person ID is, and how registrations are grouped.
- [MCP server](https://songlinks.io/mcp-server): the same answers inside Claude or any MCP client.
- [TypeScript SDK](https://www.npmjs.com/package/songlinks): `npm install songlinks`.
- Something wrong in an answer? [Open an issue](https://github.com/Alexkane/songlinks-java/issues), or write to
  alex@songlinks.to.

## Development

```sh
./mvnw verify                              # compile and test
SONGLINKS_LIVE=1 ./mvnw test               # also against the live API
curl -sf https://songlinks.io/v1/openapi.json -o src/test/resources/openapi.json
python3 scripts/gen_models.py              # regenerate the records from the API's document
```

`ModelTest` fails when a record and the API's document disagree, so a new API field is a regeneration, not a surprise.

## Licence

MIT, © 2026 Kane Technology Group, LLC. The API's answers are subject to the
[Songlinks terms](https://songlinks.io/terms).
