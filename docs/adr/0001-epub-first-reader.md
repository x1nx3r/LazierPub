# ADR 0001 — EPUB-first reader: scope, thumbnails, metadata, rendering

- **Status:** Accepted
- **Date:** 2026-10-19
- **Deciders:** project owner, implementation agent
- **Related:** `docs/adr/README.md`, server `adapter/outbound/epub/`, server `adapter/outbound/filesystem/`

## Context

LazierPub is a reader for a self-hosted light-novel library. Three parts exist:

- a Ktor backend with ports and adapters (`server/`),
- a Kotlin Multiplatform contract (`shared/`),
- a Compose Android app (`androidApp/`) that currently shows one demo screen.

The acquisition pipeline works. Prowlarr finds releases. qBittorrent downloads them
into the qBittorrent category `novels`. That category saves to the library folder, so
no importer exists. Jellyfin indexes the folder. The backend reads the same files
from `BOOKS_DIR`.

The owner set the scope: EPUB is the product. CBZ is secondary. The owner excluded
manga and audiobooks.

Constraints that shaped the decisions:

- Readium 3.4 supports EPUB 2, EPUB 3 and PDF. CBZ support is partial. CBR support is
  not planned. MOBI and AZW3 have no support.
- Readium's EPUB navigator is a Fragment, not a Compose composable.
- The library folder is also the download folder. A cover cache must not live in it.
- The backend currently scans the filesystem, not Jellyfin.

## Decision 1 — Scope and format taxonomy

EPUB is the primary format and receives the full feature set. PDF is readable with a
basic reader. CBZ is an image pager, delivered after EPUB. Manga and audiobooks are out
of scope.

The contract replaces the free-text `format` field with a closed enum:

```kotlin
enum class BookKind { EPUB, PDF, CBZ, CBR, MOBI, AZW3, UNKNOWN }
```

The server derives the kind from the file extension and then confirms it by content.
An EPUB and a CBZ file are both ZIP archives. The check reads the ZIP `mimetype` entry.
The value `application/epub+zip` means EPUB. Any other ZIP means CBZ. A renamed file
therefore still opens with the correct reader.

Unsupported kinds return `501` with a reason. The app shows "not supported yet". It
does not open a broken screen.

## Decision 2 — Thumbnails

A cover exists only after a file exists. Prowlarr search results carry no images. A
downloaded file has a cover only if its EPUB declares one.

**Extraction.** The server extracts the cover on the first request for
`GET /books/{id}/cover`. Extraction runs inside a ZIP pass and uses this order:

1. manifest item with `properties="cover-image"` (EPUB 3),
2. metadata `meta[@name="cover"]` mapped to its manifest item (EPUB 2),
3. first image item whose href contains "cover",
4. first image item.

**Resolution rules.** The extractor resolves the item href against the OPF directory.
It handles `../`, percent-encoding, `#fragment` and `?query`. It disables DTDs and
external entities in the XML parser. EPUB files are untrusted input, and that setting
prevents XML external entity attacks.

**Cache.** The cache key is `<book id>-<source mtime>.<ext>`. The modified time
invalidates the entry when a file is replaced. Older entries for the same id are
deleted on write. The cache directory comes from `COVERS_DIR`. It stays outside the
read-only books mount.

**HTTP.** The response includes `Content-Type` from the manifest, `ETag`, and
`Cache-Control: public, max-age=86400`. A request with a matching `If-None-Match`
returns `304`. A book without a cover returns `404`.

**Placeholders.** The client draws a generated placeholder in two cases: a search
result, and a PDF. The server has no PDF renderer and this decision accepts that.

**Cleanup.** A cache sweep removes entries whose book id no longer exists. A book deleted
from the library therefore stops occupying cache space.

## Decision 3 — Metadata extraction

Metadata comes from the OPF file, not from the filename. Filenames are guesswork.
Publishers author the OPF.

The extractor reads these fields in one pass, the same pass that finds the cover:

| Source | Field | Use |
| --- | --- | --- |
| `dc:title` | title | primary title |
| `dc:creator` | author | primary author |
| `calibre:series` | series | series name |
| `calibre:series_index` | volume | volume number |
| `belongs-to-collection` | series | EPUB 3 series name |
| `group-position` | volume | EPUB 3 collection position |
| `dc:language` | language | filtering and display |
| `dc:identifier` | identifier | stable identity across renames |
| `dc:date` | published | sorting |

Each value falls back independently to the path parser. A book with no
`calibre:series` still gets a series from its first directory. A book with no volume
still gets one from `v01`, `vol.1` or `Volume 3` in the filename.

A filename such as `Sword Art Online v01-28 [Yen Press] [Stick]` currently yields one
book. After this decision the library holds real volumes with real titles and authors.

## Decision 4 — EPUB rendering

Readium 3.4 renders EPUB. A separate `ReaderActivity` hosts it.

```
ReaderActivity
└── Compose shell
    ├── FragmentContainerView  ->  EpubNavigatorFragment
    ├── top bar      back, title, table of contents, search, settings
    ├── bottom bar   progress slider, chapter name, position
    └── settings     font, size, line height, margins, theme, layout mode
```

**Why a separate Activity.** Readium owns a Fragment with its own lifecycle, WebView and
back handling. A nav destination would push Fragment state through the main navigation
graph. An Activity gives the reader a clean lifecycle, immersive mode and keep-screen-on.

**Preferences.** Readium `EpubPreferences` covers font family, size, line height,
margins, text alignment, hyphenation, publisher styles, column count, scroll or
paginated mode, and image filters for dark mode. The app stores these as JSON in
DataStore. Defaults live in one place.

**Position.** The app stores Readium `Locator` objects as JSON. A Locator survives a
change in font size. A page number does not. The backend stores the locator with the
percentage and the chapter title:

```
GET  /progress          all positions, for a continue-reading list
GET  /progress/{id}     locator, percent, chapter
PUT  /progress/{id}     { locator, percent, chapter }
```

**Write policy.** The position changes on every page turn. The app debounces writes for
one to two seconds and flushes on `onPause`. It writes locally first, then to the
backend.

**Rotation.** The reader declares the configuration changes it handles, or it restores
the locator after recreation. A naive recreate loses the rendered page.

**Navigation.** Tap the centre to toggle the chrome. Tap the edges to turn pages. Swipe
scrolls in scroll mode and turns pages in paginated mode. The app defines these zones
before the overlays, because the overlays depend on them.

**Later phases.** In-book search uses `publication.search()`. Highlights and notes use
the Decoration API. They are deferred because the selection and note UI is substantial.

## Consequences

Positive:

- The reader behaves like a reader: typography control, real positions, real metadata.
- The library looks like a library: covers, series, volumes, authors.
- One extraction pass produces both the cover and the metadata.
- The cover cache invalidates itself through the modified time.
- Unsupported formats fail with a clear message instead of a broken screen.

Negative:

- Two XML parsing paths exist. The release pipeline depends on well-formed OPF files.
- PDFs have no cover until a renderer is added.
- Readium does not support MOBI or AZW3. Files of those kinds stay unreadable.
- The reader adds an Activity and Fragment interop to a Compose application.
- A Locator is larger than a page number, so the progress payload is bigger.

## Alternatives rejected

**Use Jellyfin as the catalog.** Jellyfin extracted an EPUB cover correctly in a test.
Jellyfin returns books as a flat list without series or volume, however. It would discard
metadata the path parser already produces.

**Render EPUB with `epub.js` in a plain WebView.** It removes the Fragment interop. It
also loses locators, in-book search, decorations and preference handling. The team would
rebuild what Readium already provides.

**Rely on filenames for metadata.** Cheap, and already in place. It fails on real
releases, and it cannot produce correct volume numbers for multi-volume packs.

**Use a second server for manga.** Out of scope by decision.

## Open questions

1. Should progress sync to the backend in phase 1, or stay local until phase 2? The
   current plan is local first.
2. Should the cover cache pre-extract during the library scan, or stay lazy? Lazy keeps
   the scan fast. Pre-extraction makes the first library view instant.
3. Which font families ship with the app? Embedded EPUB fonts work, but user-selected
   fonts need bundling or system fonts.
