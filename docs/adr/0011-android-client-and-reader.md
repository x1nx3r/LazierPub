# ADR 0011 — The Android client and the reader

- **Status:** Accepted
- **Date:** 2026-10-20
- **Related:** ADR 0001 (EPUB-first), ADR 0003 (backend as the only API), ADR 0008 (thumbnails), ADR 0010 (API surface), `androidApp/`

## Context

The app has to do three things at once:

1. Show a large library in a way that is quick to scan.
2. Read an EPUB for a long time without getting in the way.
3. Stay useful when the server is far away or briefly unreachable.

Mihon is the reference for the first point. Its shape is familiar: a cover
library, a detail page with a chapter list, a reader with thin chrome, and a
bottom bar that separates library, updates, history, discovery and settings.
That shape fits light novels with one change: a series has volumes, and one
series can hold dozens of them.

The backend already holds metadata, covers, files, progress and shelves
(ADR 0010). The client should not duplicate that state.

## Decisions

### 1. Five tabs, in Mihon's order

| Tab | Content | Server route |
| --- | --- | --- |
| Library | series grid by default, volume grid on demand | `/series`, `/books` |
| Recent | newest files first | `/books?sort=added` |
| History | books with a saved position, latest first | `/continue` |
| Browse | release search, grab, download queue | `/search`, `/grabs` |
| More | server, appearance, statistics, shelves | `/config`, `/stats`, `/shelves` |

Detail screens (series, book) push over the current tab and hide the bottom
bar. Back returns to the tab, which kept its scroll position.

Series is the default library view because 221 volumes hide the fact that they
are nine stories. A menu item switches to all volumes.

### 2. The server is the only source of truth

There is no database on the device. One `LibraryStore` holds the last response
in Compose state. It refreshes books, series, statistics, progress and the
continue list in parallel, each on `Dispatchers.IO`.

The consequence is honest: with no server and no previous load, the library is
empty. Caching metadata locally would need an invalidation story that the
backend already owns.

### 3. Blocking HTTP on an IO dispatcher

`Api` uses `HttpURLConnection`. Every call is blocking and every caller wraps it
in `withContext(Dispatchers.IO)` or calls it from a store that does.

Ktor client was rejected: it brings OkHttp and a second HTTP stack for six
endpoints. The shared module already supplies the JSON codecs, so the client is
about 250 lines with no new dependency.

This rule is not optional. A network call on the main thread throws
`NetworkOnMainThreadException`, whose message is `null`, so the screen shows
nothing at all. That failure mode is why every screen wraps its calls.

### 4. Covers: memory, then disk, then the server

`CoverStore` keeps decoded bitmaps in a 48 MB LRU, raw bytes in `cacheDir`, and
asks the server for `?w=320` thumbnails. `BitmapFactory` downsamples again to
the requested width.

Coil was rejected. It would need an OkHttp interceptor to add the bearer token
to every image request, plus a dependency whose version must track Compose. The
hand-written loader is about 80 lines and the token handling stays in one place.

Covers are evicted from More → Maintenance.

### 5. The reader is an Activity with a Fragment and two Compose views

Readium 3.4 exposes `EpubNavigatorFragment`, so the reader needs a Fragment
host. The layout is built in code, not XML:

```
LinearLayout (vertical)
├── ComposeView (top)       back, title, contents, display options
├── FragmentContainerView   the Readium WebView, weight 1
└── ComposeView (bottom)    progress bar and percentage
```

The page keeps one size for the whole session. The bars sit in bands reserved
above and below it, and show and hide by sliding over those bands, so the text
never reflows when the chrome appears. The bands are measured once from the bars
and stay fixed. The system bars stay visible; the reader only flips their icon
colour to match the page.

Side and top/bottom margins come from insetting the WebView, not from Readium's
`pageMargins`. The app needs a vertical margin, and the CSS gutter is small and
hard to predict.

A centre tap toggles the chrome; the left and right thirds turn the page, both
through Readium's `InputListener` and through a touch listener on the bands that
sit outside the WebView. The bars hide on their own after 3.5 seconds, and stay
while a sheet is open.

`EpubPreferences` carries the theme, font family and size, line height,
paragraph spacing, alignment, column count, scroll mode, publisher styles and
image filter. The display sheet applies changes live through
`submitPreferences`. The chrome paints itself from the same `ReaderConfig`, so
the bars change colour with the page.

Two Readium details are easy to get wrong:

- A custom palette still passes a base `Theme` (dark or light, chosen from the
  background luminance). Readium takes the image filter and link colours from
  the theme, so `theme = null` silently disabled the image filter. The explicit
  background and text colours win over the theme colours.
- Changing paged/scroll needs a new navigator. `submitPreferences` alone leaves
  the old view in place, so the reader rebuilds the fragment at the last
  locator.

### 6. Progress is a Readium Locator, debounced

The navigator publishes `currentLocator`. The reader debounces 1.5 s and calls
`PUT /progress/{id}` with the locator JSON, the total progression, the chapter
title from the table of contents, and a finished flag. It saves again in
`onStop`, so leaving the app never loses the page.

The reader always opens fresh (`super.onCreate(null)`). A `Publication` cannot
be rebuilt by the FragmentManager after process death, and restoring the
fragment without it crashes. The server locator is the restore point instead,
which also survives a reinstall of the app.

### 7. One row per volume in a series

The library contains EPUB and PDF twins of the same volume. The series screen
groups by title and prefers the EPUB, because this version cannot render PDF.
The volume grid still lists both files.

### 8. Files are kept for offline reading

A book downloads once to `filesDir/books/<id>.epub` through a `.part` file and a
rename. Later reads skip the network. There is no eviction yet; the library is
small enough that this is a feature.

### 9. Settings are SharedPreferences, exposed as Compose state

Server URL, token, app theme and grid columns live in `Settings`. Reader
display choices live in `ReaderSettings` as one `ReaderConfig`, because the
reader needs all of them together to build `EpubPreferences` and to paint its
chrome. Both read once at startup and write through on change. The mutable
state makes a theme change redraw the app immediately.

## Consequences

- A fresh install needs the server URL and token before it can show anything.
  More → Server has both fields and a test button.
- Metadata is not available offline. The files and the last position are.
- PDF files are listed and downloadable but the reader refuses them with a
  clear message.
- The reader cannot be restored mid-session by Android after process death; it
  resumes from the last saved locator instead.
- The reserved top and bottom bands cost about a sixth of the screen, even when
  the bars are hidden. That is the price of never reflowing the page.
- The cover cache grows until the user clears it. The LRU bounds memory, not
  disk.

## Alternatives rejected

| Alternative | Why it lost |
| --- | --- |
| `navigation-compose` | two detail routes and five tabs fit in a state list; the dependency adds a version to track |
| Coil | auth headers would need a custom OkHttp client anyway |
| Ktor client | a second HTTP stack for six calls |
| ViewModel per screen | the library is shared by four screens; one store is simpler |
| XML layouts | the only view that needs a stable id is the fragment container |
| A local Room cache | the server owns the catalog; a cache would need sync rules for no offline metadata need |

## Open questions

- Annotations and highlights need a place in the API; they are not in P1.
- Progress sync between devices is last-write-wins. Two devices reading the
  same book will overwrite each other.
- The reader has no pagination slider; it shows the percentage only.
