# ADR 0002 — Monorepo layout and the shared contract

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** `settings.gradle.kts`, `gradle/libs.versions.toml`, `shared/`, `server/`, `androidApp/`

## Context

The project needs three things at once: an Android client, a backend, and an agreement
between them about the shape of the data.

Two failure modes were possible:

- two repositories, where the client and server drift apart silently;
- duplicated data classes, where a field rename breaks one side at runtime.

The backend also must build inside a Docker image, and that image must not need the
Android SDK.

## Decisions

**1. One Gradle build, three modules.**

| Module | Kind | Purpose |
| --- | --- | --- |
| `:shared` | Kotlin Multiplatform, `jvm()` target | the contract: DTOs, JSON configuration, codecs |
| `:server` | Kotlin JVM + Ktor | backend |
| `:androidApp` | Android application, Compose | client |

**2. The contract lives in `:shared` and both sides compile against it.** A rename on
the server fails the Android build. That is the point.

The module uses explicit serializers in `BooksCodec`. Consumers therefore do not need
the serialization compiler plugin just to read a response.

**3. One version catalog.** Every dependency version is in
`gradle/libs.versions.toml`. No module declares a version inline.

**4. The Android module is included conditionally.**

```kotlin
if (System.getenv("LAZIER_SKIP_ANDROID") != "true") {
    include(":androidApp")
}
```

The Docker image sets that variable. Without it, configuring the Android module would
require the SDK and the server image build would fail for an unrelated reason.

**5. Features stay packages, not modules.** `androidApp` holds `feature/*` packages
until one passes roughly one to two thousand lines. Gradle modules cost configuration
time and wiring. The package boundary gives most of the benefit and splitting later is
mechanical.

**6. Reference checkouts are ignored.** Cloned projects used for study live in `ref/`,
which `.gitignore` excludes.

## Consequences

Positive:

- One command builds everything. One place holds versions.
- The contract cannot drift.
- The server image builds without Android tooling.
- The KMP module can gain iOS or web targets later without moving the models.

Negative:

- An Android build now depends on `:shared`, so contract changes ripple into the app.
- A KMP module with only a `jvm()` target is consumed by Android through Kotlin's
  platform compatibility rule. It works and is verified, but it is a less common shape.
- The version catalog is a single point of conflict in parallel work.

## Alternatives rejected

**Separate repositories.** Contract drift, and two CI configurations.

**One module.** Gradle cannot build an Android application and a JVM server from the same
module. The plugin sets are incompatible.

**Duplicated DTOs, generated from a schema.** More tooling than a 60-line contract
deserves.

**A feature module per screen now.** Ceremony at this size. Revisit when the reader
lands and the code grows.
