# Songlinks Java SDK

[![ci](https://github.com/Alexkane/songlinks-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Alexkane/songlinks-java/actions/workflows/ci.yml)
[![licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)

**Find the songwriter behind a name.** The official Java SDK for [Songlinks](https://songlinks.io).

One songwriter is rarely one record. They're registered under different spellings, under several IPI name numbers, or
with no number at all. Songlinks groups those registrations into one person, shows the evidence for every join, and
keeps apart the ones it isn't sure of.

- **Free, with no key and no sign-up.**
- **Typed.** Every answer is a Java class with named fields, checked against the API's own description of itself on
  every build.
- **Light.** Java 17+, the JDK's own `HttpClient`, and Jackson for JSON. Nothing else.
- **Built for real apps.** Clear errors, automatic retries when the API is busy, and long lists paged for you.

> **New to this?** Skip to [Your first program, step by step](#your-first-program-step-by-step): it starts from
> installing the language and ends with a working answer. The [words used here](#the-words-used-here) are explained too.

**Not on Maven Central yet.** Until it is, install it from a checkout, which puts it in your local Maven repository:

```sh
git clone https://github.com/Alexkane/songlinks-java && cd songlinks-java && ./mvnw -q install -DskipTests
```

Then depend on it as usual:

```xml
<dependency>
  <groupId>io.songlinks</groupId>
  <artifactId>songlinks</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle: `implementation("io.songlinks:songlinks:0.1.0")`

## Thirty seconds

These lines go inside a `main` method; [`examples/ResolveName.java`](examples/ResolveName.java) shows a whole program.

```java
import io.songlinks.Songlinks;
import io.songlinks.model.*;
import java.util.List;

Songlinks songlinks = Songlinks.builder().userAgent("my-app/1.0").build();

LookupResponse answer = songlinks.lookup("mark hoppus");
if ("rejected".equals(answer.answer())) {  // for example a one-word name, which matches too many people
    throw new IllegalStateException("no answer: " + answer.rejection().code());
}
Cluster candidate = answer.candidates().get(0);
System.out.println(candidate.persistentId() + " " + candidate.names() + " " + candidate.works());
// SLP-ZYM7-GD3E-S [MARK HOPPUS] 302

songlinks.person("SLP-ZYM7-GD3E-S", 0, 3).worksList().items().forEach(w -> System.out.println(w.title()));
// I MISS YOU / ALL THE SMALL THINGS / DOWN

WritersResponse check = songlinks.writers(List.of("00338980326", "00340247397"));
check.writers().forEach(w -> System.out.println(w.name() + " " + w.personId()));
// MARK HOPPUS SLP-ZYM7-GD3E-S
// TRAVIS L BARKER SLP-MG23-XM9B-Y
```

Every person has a page you can link to, such as [songlinks.io/person/SLP-ZYM7-GD3E-S](https://songlinks.io/person/SLP-ZYM7-GD3E-S).
It shows the registrations behind the person and what each join rests on.

> **Beta.** Unverified machine output, from a dated snapshot of registration data (a lookup's or a person's
> `provenance().mlcSnapshot()` gives its date, and `version()` gives the current one). Some answers are wrong and some are incomplete. Not for rights or royalty decisions.

### More

```java
// Which of several writers with the same name wrote a song? Pass one of their songs.
SongMatch match = songlinks.lookup("amy allen", LookupOptions.song("espresso")).songMatch();
// match.match() = "exact", match.candidates() = [c1]: the candidate whose ref() is "c1" is the Amy Allen credited
// on that song

// Every work of a person, read lazily, a page at a time.
songlinks.personWorks("SLP-ZYM7-GD3E-S").forEach(w -> System.out.println(w.title()));
```

## Your first program, step by step

Never called an API from code before? This takes about five minutes.

**Before you start**, you need two things:

- **A terminal**, where you type commands. On a Mac, open Terminal (in Applications, then Utilities). On Windows, open
  PowerShell from the Start menu. Type each command below on its own line and press Enter after it.
- **A plain-text editor** for code, such as the free [Visual Studio Code](https://code.visualstudio.com). A word
  processor (Word, or TextEdit in its usual mode) adds formatting that stops code from running.
1. **Install Java** 17 or later, for example the free [Temurin](https://adoptium.net) build, and **Git** from
   [git-scm.com](https://git-scm.com/downloads) (on a Mac, the first `git` command offers to install it: say yes).
   Check they worked by typing `java -version` and `git --version`.
2. **Get the SDK and build it.** The first build downloads Maven (the tool Java projects use to build and to fetch
   libraries), so it takes a minute or two:

   ```sh
   git clone https://github.com/Alexkane/songlinks-java
   cd songlinks-java
   ./mvnw -q install -DskipTests
   ```

   On Windows, in PowerShell, the last line is `.\mvnw.cmd -q install -DskipTests`. It prints nothing when it works.
   On Java 24 or later you may first see `WARNING` lines about a "restricted method" or `sun.misc.Unsafe`: they
   come from Maven itself and are harmless.
3. **Run the first example**, from the same folder, with any songwriter's full name:

   ```sh
   scripts/run-example.sh ResolveName "mark hoppus"
   ```

   On Windows, in PowerShell, run these two lines instead (the first one only once):

   ```powershell
   .\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=target/classpath.txt"
   java -cp "target/classes;$(Get-Content target/classpath.txt)" examples/ResolveName.java "mark hoppus"
   ```

   The first run builds once more and may print the same warnings. You should see
   `SLP-ZYM7-GD3E-S  MARK HOPPUS  302 works`, then the registrations behind that person and what joined them
   (`model` means Songlinks judged two registrations to be the same person).

   **Keep the quotes** around the name: without them, only the first word is sent.

   **What you might see instead:**
   - **Several lines.** The name could belong to more than one person, so each possible person gets a line.
   - **`(no person ID yet)`.** Songlinks found the registrations but hasn't given that person an ID yet. That is
     normal, and the rest of the line is still the answer.
   - **`No answer: single-token`.** The name is one word, such as `madonna`, which matches too many people.
     Use the full name.
   - **`No answer: no-match`.** Nothing registered is close to that name. Check the spelling.
4. **Change it.** The program is [`examples/ResolveName.java`](examples/ResolveName.java), about 40 lines. Edit it in
   your editor and run the same command again: changes to the examples need no separate build step.

To use the SDK in your own Maven project, add the dependency shown at the top of this page to its `pom.xml`; step 2
has already put the library where Maven will find it. (Gradle needs `mavenLocal()` in its `repositories` to look
there.) From here, the [examples](#examples) do real jobs, [Reading an answer](#reading-an-answer) explains what comes
back, and the [words used here](#the-words-used-here) are explained just below.

## The words used here

Music-publishing words, which you'll meet in the answers:

- **Songwriter registration**: when a song is written, its publisher registers it so royalties reach the people who
  wrote it. Each registration names the writers, as typed by whoever filed it. One songwriter can therefore appear
  under several spellings, and two different songwriters can share a name.
- **Record**: one of those registered writer entries: a name as it was typed, with its IPI name number if it has one,
  and the songs (works) registered under it.
- **IPI name number**: an 11-digit number that identifies a songwriter or publisher to collecting societies, such as
  `00338980326`. Many records have none, and one person can have several.
  [More about IPI name numbers](https://songlinks.io/guides/ipi-name-numbers).
- **Work**: a song as a composition (the melody and lyrics), as opposed to a particular recording of it.
- **ISWC**: the international code for a work, such as `T0719661581`. Not every registered work has one.
- **Person** and **person ID**: Songlinks groups the records it judges to be one human into a person and gives them a
  person ID, such as `SLP-ZYM7-GD3E-S`. Every person has a page, such as
  [songlinks.io/person/SLP-ZYM7-GD3E-S](https://songlinks.io/person/SLP-ZYM7-GD3E-S).
- **Candidate**: what a lookup by name returns: the records that name reaches, grouped into people for that one
  question. A candidate usually matches one person ID, but it is worked out fresh each time.
- **Evidence**: why two records were grouped: the same IPI name number, or a judgement that they are the same person
  (for example, the same co-writers and publishers).
- **Rejected**: a lookup that gave no candidates, with a reason, such as a one-word name (`madonna`) that matches too
  many people to group. It is an answer, not an error.

Programming words:

- **API**: a service that programs talk to over the internet. The Songlinks API answers questions such as "who is
  this songwriter?".
- **SDK**: this library. It does the talking to the API for you, so your code calls a function instead of building
  web requests by hand.
- **Rate limit**: the most requests the API accepts from one internet address in a stretch of time: about 100 every
  5 minutes, or 20 from VPNs and cloud servers. Over it, the SDK tells you how long to wait.
- **Maven** and **dependency**: Maven is the tool Java projects use to build and to fetch libraries; a dependency is a library your project uses, such as this one, listed in its `pom.xml`.

## Checking a whole roster?

The API answers one name at a time, which suits an app. If you administer a catalogue, send us the writers instead.
For each one we send back every registration we can group to them, across spellings, stage names and more than one IPI
name number, with the evidence behind each join and the records we deliberately left out. See
[bulk songwriter lookup](https://songlinks.io/bulk-songwriter-lookup), or write to alex@songlinks.to.

## Examples

Runnable programs for the jobs people use Songlinks for, each under 40 lines. Copy one and change it:

| Program | What it does |
| --- | --- |
| [`ResolveName.java`](examples/ResolveName.java) | Every person a name could be, with their registrations and what joined them. |
| [`WhichNamesake.java`](examples/WhichNamesake.java) | Two writers share a name: which one wrote this song? |
| [`CheckRoster.java`](examples/CheckRoster.java) | A list of IPI name numbers in, CSV out: who holds each and their works. |
| [`ExportCatalogue.java`](examples/ExportCatalogue.java) | Every work of a person, as CSV. |

From a checkout, `scripts/run-example.sh` builds the SDK once and runs any of them:

```sh
scripts/run-example.sh CheckRoster roster.txt > checked.csv
```

The examples are compiled in CI against the SDK, so one that misreads a field fails the build.

## What it covers

| Method | API | What it answers |
| --- | --- | --- |
| `lookup(name)`, `lookup(name, LookupOptions)` | `GET /v1/lookup` | The people a songwriter's name could be. A song the writer wrote ranks the candidates by it. |
| `person(id)`, `person(id, offset, limit)` | `GET /v1/person/{id}` | One person: their registrations, names, IPI name numbers and a page of works. |
| `personWorks(id)` | `GET /v1/person/{id}` | Every work of a person, as a lazy `Stream`. |
| `writers(ipiNameNumbers)` | `GET /v1/writers` | Up to 50 IPI name numbers checked at once: the stored person behind each. |
| `version()` | `GET /v1/version` | What is serving: the release, the model and the date of the registration data. |

### Reading an answer

A **person** and a **candidate** are different things: see [the words used here](#the-words-used-here).

- **A candidate can count more works than its person.** "mark hoppus" reaches two registrations, 302 works between
  them; the person `SLP-ZYM7-GD3E-S` holds one of them as theirs (294 works) and lists the other in `possible()`.
  `person()` is the authority on what an ID holds.
- **Match a writers check by number, not position.** Repeats and two forms of one number come back as one entry, as
  11 digits. `Songlinks.normalizeIpi()` turns your input into the same form. Its `works()` and `name()` are null
  unless `status()` is `"person"`.
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
// Continuing from the code above, where `songlinks` was created.
String personId = "SLP-0000-0000-0";  // an ID no one holds, to see the not-found branch
try {
    songlinks.person(personId);
} catch (PersonWithdrawnException e) {
    System.out.println("withdrawn " + e.withdrawn().withdrawnAt()); // a 410, and a valid answer to keep
} catch (RateLimitedException e) {
    System.out.println("retry in " + e.retryAfter().map(d -> d.toSeconds() + " s").orElse("a few minutes"));
} catch (SonglinksException e) {
    if ("not_found".equals(e.code())) {  // a 404, such as for SLP-0000-0000-0
        System.out.println("no person with that ID");
    } else {
        System.out.println(e.status() + " " + e.code() + ": " + e.getMessage());
    }
}
```

- **Retried automatically:** `429` and `503` answers and network failures, up to `maxRetries` times (default 2).
  When the API asks for a wait (in a `Retry-After` header or the body's `retry_after_seconds`) of a minute or less,
  the client waits that long; otherwise it backs off from half a second.
- **Thrown at once:** a requested wait longer than a minute, for you to schedule (`retryAfter()` on a 429).
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

The API is free and open. It allows about 100 requests per 5 minutes from one address, across every endpoint, and 20
from VPNs, proxies and hosting providers, which includes most cloud servers. Please send a `userAgent` that names your
app, keep to one request at a time per user action, and cache answers you reuse. Over the limit, the SDK throws a
`RateLimitedException` whose `retryAfter()` is the most you need to wait; for a whole roster, see
[bulk songwriter lookup](https://songlinks.io/bulk-songwriter-lookup).

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
