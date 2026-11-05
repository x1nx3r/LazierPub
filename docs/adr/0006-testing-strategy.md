# ADR 0006 — Testing strategy

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** `server/src/test/`, `docs/adr/0004-ports-and-adapters.md`

## Context

The backend talks to two live services over a tailnet and reads a real filesystem. Tests
must run without either. At the time of this record there are 21 tests and no instrumented
Android tests.

The code has three kinds of logic, and each needs a different test:

| Layer | Depends on | Needs |
| --- | --- | --- |
| Use cases | ports only | fake ports |
| Outbound adapters | HTTP shape of an upstream | a fake HTTP engine |
| Filesystem and archive adapters | real files | real temporary files |

## Decisions

**1. Use cases are tested with fake ports.** No HTTP, no mock engine, no temporary
directories. A fake `GrabQueue` records calls. A fake `ReleaseSearch` counts invocations,
which is how "a blank query must not reach the upstream" is proven.

**2. Outbound HTTP adapters are tested with Ktor's `MockEngine`.** The test asserts
mapping rules, not transport:

- results sort by seeders, not size,
- the magnet comes from `magnetUrl`, then `downloadUrl`, then `guid`,
- a release with no download link is dropped,
- `freeleech` is read from indexer flags.

**3. Archive and filesystem adapters are tested against real files.** Cover tests build a
real ZIP with a real OPF and assert the extracted bytes. Catalogue tests use a temporary
directory tree. Mocking a zip is more work than zipping.

**4. The HTTP routes themselves are not unit tested.** They are thin: parse a parameter,
call a use case, map a status. They are verified by running the server and calling it,
which has already caught two real defects that a unit test would not have.

**5. Integration checks are manual and recorded.** Real Prowlarr, real qBittorrent and real
EPUB releases are used deliberately, and the results are written into the README. Example:
a real search returned 3 results ordered 30 · 18 · 7 seeders.

**6. JUnit rules that cost time.**

- A `@Test` method must return `Unit`. `assertFailsWith` returns the exception, so a test
  written as `fun x() = runBlocking { assertFailsWith<...> { } }` returns a value and JUnit
  rejects the entire class. One bad signature hid six tests. Use a block body.
- `MockEngine` proves parsing, not transport. It cannot catch a wrong header name.

## Consequences

Positive:

- The suite runs in seconds and needs no network.
- Business rules are protected without an upstream.
- Archive handling is tested against real ZIP structure, which is what breaks in practice.

Negative:

- The HTTP surface has no automated coverage. A broken route is found by running it.
- MockEngine payloads are hand-written and can drift from the real API.
- No Android instrumented tests. The UI is verified manually on a device and an emulator.

## Alternatives rejected

**Testcontainers for Prowlarr and qBittorrent.** Accurate, slow, and awkward for services
that need indexers or an account.

**End-to-end tests over the tailnet.** Non-deterministic and dependent on a live host.

**Instrumented Compose tests now.** The UI is one screen. Revisit when the reader exists,
because the reader is where UI tests would pay.
