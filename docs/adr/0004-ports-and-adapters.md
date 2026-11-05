# ADR 0004 — Ports and adapters, kept light

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** `server/port/Ports.kt`, `server/usecase/`, `server/adapter/`, `server/Main.kt`

## Context

The backend has three I/O boundaries and will gain more:

| Boundary | Adapter today | Likely alternative |
| --- | --- | --- |
| Book catalog | filesystem scan | Jellyfin |
| Release search | Prowlarr | another indexer API |
| Grab queue | qBittorrent | another download client |
| Cover images | EPUB extraction | Jellyfin image API |

Business rules are small. HTTP is a detail. Tests should not need a running upstream.

Layered or by-feature architecture is the common default and would work. It was not
chosen because the boundaries above are real and will change.

## Decisions

**1. Four ports, one interface each.**

```kotlin
interface BookCatalog     { fun all(); fun find(id) }
interface ReleaseSearch   { suspend fun search(query, limit) }
interface GrabQueue       { suspend fun list(); suspend fun add(url); suspend fun remove(hash) }
interface CoverProvider   { suspend fun cover(id): Cover? }
```

A port describes what the application needs, not what an adapter offers. There is no
`FilesystemPort` and no `ProwlarrPort`.

**2. Use cases hold the rules.** `ListBooks`, `FindBook`, `SearchReleases`, `ListGrabs`,
`StartGrab`, `StopGrab`, `GetCover`-by-port. Validation lives in the use case, not the
route. A blank `downloadUrl` becomes `400` because `StartGrab` rejects it, not because a
route checked a string.

**3. The dependency rule is enforced by packages.**

```
adapter/inbound  ->  usecase  ->  port
adapter/outbound ->  port
```

`usecase` imports only `port` and `shared`. It does not import Ktor, Prowlarr,
qBittorrent or `java.nio.file`.

**4. `Main.kt` is the composition root.** It reads configuration, picks adapters and wires
use cases into the HTTP API. No dependency injection framework. Six objects do not need a
container.

**5. Two deliberate deviations from the textbook.**

- **The shared DTOs are the domain model.** Duplicating `Book` into a domain entity and a
  wire type would add mapping noise and buy nothing.
- **`StoredBook` exposes a `java.nio.file.Path`.** The HTTP layer must hand the file to
  Ktor's `respondFile` to negotiate `Range` for PDF readers. A hand-written stream
  abstraction would move that logic into the application and be worse.

**6. Split further when a feature earns it.** Packages stay packages until a feature
passes roughly one to two thousand lines.

## Consequences

Positive:

- Use cases are tested with fake ports, in milliseconds, without HTTP or a network.
- A new catalog or cover source is one adapter plus one line in the composition root.
- The route layer holds no business rules.
- Failures are classified by type: `UpstreamException` becomes `502`, `IllegalArgumentException`
  becomes `400`.

Negative:

- More files and one more hop of indirection than a direct route-to-repository call.
- Two adapters can depend on each other through a port (the cover provider uses the
  catalog), which is legal but worth watching.
- The `Path` in a port leaks a platform type into the application boundary.

## Alternatives rejected

**Route calls the client directly.** Fast to write. Untestable without a network and it
puts rules in the HTTP layer.

**Full clean architecture with separate entities.** Mapping between `Book` and `BookEntity`
for four fields is ceremony.

**A DI framework (Hilt/Koin).** Real value arrives with dozens of dependencies. Revisit if
the wiring grows past a screen or two.
