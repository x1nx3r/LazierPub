# LazierPub

A reader for a self-hosted light-novel library: an Android app, a Kotlin server,
and a shared contract between them.

## Layout

```
LazierPub/
├── gradle/libs.versions.toml        every version in one place
├── settings.gradle.kts              :androidApp  :shared  :server
│
├── shared/                          Kotlin Multiplatform (jvm target)
│   └── commonMain/…/Models.kt       the wire contract + BooksCodec
│
├── server/                          Ktor 3.6.0, ports and adapters
│   ├── Main.kt                      composition root
│   ├── Config.kt                    environment → typed config
│   ├── Errors.kt                    UpstreamException
│   ├── port/Ports.kt                BookCatalog · ReleaseSearch · GrabQueue · CoverProvider
│   ├── usecase/                     ListBooks · FindBook · SearchReleases
│   │                                ListGrabs · StartGrab · StopGrab
│   ├── adapter/inbound/http/        Ktor routes, status mapping, auth
│   └── adapter/outbound/            filesystem · prowlarr · qbittorrent · epub
│
├── tools/repair-epub.py             fix EPUBs with a broken trailing EOCD record
├── docs/adr/                        architecture decision records
│
└── androidApp/                      Jetpack Compose, Readium 3.4.0
    └── …/dev/lazier/pub/
        ├── MainActivity.kt          five tabs, hand-rolled route stack
        ├── data/                    Api · CoverStore · LibraryStore · Settings
        ├── reader/                  ReaderActivity · ReaderSettings · display sheet
        └── ui/                      library · series · book · updates · history · browse · more
```

## Architecture: why ports and adapters

Not the default choice, and worth justifying. Layered or by-feature is the usual
starting point. Ports and adapters earn their place here because:

1. There are already three I/O adapters — filesystem, Prowlarr, qBittorrent.
2. The catalog source will change. Filesystem today, Jellyfin possibly later.
3. Business rules should be testable without HTTP or upstream services.

The dependency rule is enforced by packages, not by good intentions:

```
adapter/inbound  ──▶  usecase  ──▶  port
adapter/outbound ──▶  port
```

`usecase` imports only `port` and `shared`. It has never heard of Ktor, Prowlarr,
qBittorrent, or `java.nio.file`.

Deliberate deviations from the textbook:

| Textbook | Here | Why |
| --- | --- | --- |
| separate domain entities and DTOs | `shared` models **are** the domain | duplicating `Book` adds mapping noise for no insight |
| ports return abstractions | `StoredBook` carries a `java.nio.file.Path` | Ktor's `respondFile` is what negotiates `Range`; a hand-written stream would be worse |
| DI framework | constructor injection in `Main.kt` | six objects do not need a container |

## Toolchain

| Piece | Version | Note |
| --- | --- | --- |
| JDK | 17 (`/usr/lib/jvm/java-17-openjdk`) | JDK 21 here is **JRE-only**, no `javac` |
| Android SDK | `/home/x1nx3r/Android/Sdk` | `local.properties` points here |
| Platform | `android-37.0` | `compileSdk = 37` |
| Gradle | 9.8.1 | via the wrapper |
| AGP | 9.4.1 | requires Gradle 9.1.0+; compiles Kotlin itself |
| Kotlin | 2.4.21 | |
| Compose BOM | 2026.09.00 | `activity-compose` is **not** covered; pinned 1.13.0 |
| Readium | 3.4.0 | needs `minSdk 24` and desugaring |
| Ktor | 3.6.0 | server and its upstream HTTP client |

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :server:test :server:build :androidApp:assembleDebug
./gradlew build                      # everything
```

APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

## Run the server

```bash
./gradlew :server:installDist
BOOKS_DIR=/path/to/novels LAZIER_TOKEN=devtoken \
  PROWLARR_URL=http://100.88.88.88:9696 PROWLARR_API_KEY=… \
  QBITTORRENT_URL=http://100.88.88.88:8081 QBITTORRENT_USERNAME=admin QBITTORRENT_PASSWORD=… \
  server/build/install/server/bin/server
```

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8787` | listen port |
| `BOOKS_DIR` | `/data/novels` | directory to scan |
| `COVERS_DIR` | `/data/covers` | writable cache for extracted covers |
| `DATA_DIR` | `/data` | writable directory for `progress.json` and `shelves.json` |
| `LAZIER_TOKEN` | *(unset)* | bearer token. **Unset disables auth** |
| `LAZIER_PASSWORD` | *(unset)* | password accepted by `POST /auth/login` |
| `PROWLARR_URL` | `http://100.88.88.88:9696` | indexer search |
| `PROWLARR_API_KEY` | *(unset)* | enables `/search` |
| `PROWLARR_BOOK_CATEGORIES` | `7000` | **one** category; a comma list returns 400 |
| `QBITTORRENT_URL` | `http://100.88.88.88:8081` | download client |
| `QBITTORRENT_USERNAME` / `PASSWORD` | *(unset)* | enables `/grabs` |
| `QBITTORRENT_CATEGORY` | `novels` | decides the download folder |

### Local `.env`

`server/.env` is read when present, so you can run the server without exporting
anything. Real environment variables **override** the file, which keeps
containers and CI in control. Point `LAZIER_ENV_FILE` at another file to use it
instead.

`.env` is gitignored. It holds the Prowlarr key and the qBittorrent password;
`.env.example` is the committed template.

## API

| Route | Purpose |
| --- | --- |
| `GET /health` | liveness, version, book count — no token |
| `GET /config` | non-secret summary so a client can self-configure — no token |
| `POST /auth/login` | `{"password": …}` → token; `503` when `LAZIER_PASSWORD` is unset |
| `GET /books` | `q`, `kind`, `series`, `author`, `shelf`, `sort`, `order`, `limit`, `offset` |
| `GET /books/{id}` | one book |
| `GET /books/{id}/file` | the file, **with `Range`** |
| `GET /books/{id}/cover` | extracted from the EPUB, `?w=` downscale, `ETag` + `304` |
| `GET /series` | series with volume counts, optional `?q=` |
| `GET /stats` | library totals |
| `GET /progress` | every saved position |
| `GET /continue` | reading list; `limit`, `includeFinished` |
| `GET /progress/{bookId}` | one position |
| `PUT /progress/{bookId}` | Readium locator, progression, chapter and finished flag |
| `DELETE /progress/{bookId}` | clear a position |
| `GET /shelves` | shelves with their book ids |
| `POST /shelves` | `{"name": …}` |
| `DELETE /shelves/{id}` | delete a shelf |
| `PUT /shelves/{id}/books/{bookId}` | add a book |
| `DELETE /shelves/{id}/books/{bookId}` | remove a book |
| `GET /indexers` | enabled Prowlarr indexers, the Browse sources |
| `GET /search?q=` | Prowlarr, **seeders-first**, `limit`, repeatable `indexerIds`; top 10 read Nyaa file lists |
| `GET /grabs` | qBittorrent queue |
| `POST /grabs` | `{"downloadUrl": …}`, answers `202` |
| `DELETE /grabs/{hash}` | cancel; always keeps files |

Errors: `400` bad input, `401` missing token, `502` upstream failure (service
named), `503` upstream not configured (variables named).

## Verified

- **81 tests pass**: use cases with fake ports, Prowlarr, Nyaa file-list and
  qBittorrent adapters with `MockEngine`, filesystem catalog, EPUB cover
  extraction, metadata resolution, progress and shelf stores.
- **Real services over the tailnet**: `/search?q=spice and wolf` returned 3
  results ordered 30 · 18 · 7 seeders; `/grabs` returned an empty queue attached
  to the `novels` category; a blank `POST /grabs` returned `400` without touching
  qBittorrent.
- **Real library**: two releases produced 228 files and 2.0 GB. `/books` reports
  221 books (125 EPUB, 96 PDF) and ignores the `.txt` notes.
- **Covers**: 125 of 125 EPUBs after repair, about 14 ms each on a warm cache,
  536 KB average, `ETag` and `304` confirmed. Before repair the figure was 117 of
  125, because eight files ship with a broken trailing EOCD record.
  `tools/repair-epub.py` fixed all eight and changed no content.
- **Range**: `206` with a correct `Content-Range` on a 649,948-byte EPUB and on a
  2,151,733-byte PDF.
- **Android app**: built and run on an emulator against the live test server.
  The library shows 221 books as 9 series; a volume downloads on first read.
  The reader renders chapters and images, follows the table of contents, saves
  progress through `PUT /progress/{id}`, and applies font family, size, line
  height, side and top/bottom margins, paragraph spacing, alignment, columns,
  paged/scroll mode, theme or custom colours, and the image filter. The chrome
  slides away after 3.5 seconds and never resizes the page; a centre tap toggles
  it, and taps on the left and right thirds turn the page. The app shell uses
  Material 3: a lavender light and dark theme, optional Android 12 wallpaper
  colors, custom tab icons, a persistent library search with a series/volume
  switch and pull-to-refresh, grouped recent additions, card-based results and
  downloads, and settings cards with a statistics grid. Browse lists the enabled
  Prowlarr indexers as sources, filters by source and by format, and shows size,
  seeders, grabs, age and category for each release. Format labels come from the
  release name, or from the Nyaa file list for the first ten results. Prowlarr
  has no release artwork, so a colour-coded format tile stands in for the cover.
  PDFs are hidden from the library, recent list, history and series by default;
  More → Library → "Show PDF books" restores them at once. The switch is
  client-side, so statistics still count every file.
- **API smoke**: More → Test reports `Connected · 221 books · server 0.3.0`
  over `10.0.2.2`; `/search` returns releases and `/grabs` shows the queue.
- Android APK: about 22 MB, `minSdk 24`, `targetSdk 37`, V2 signed,
  byte-reproducible.

## Next steps

1. **PDF rendering.** PDFs are indexed and downloadable, but hidden in the app
   by default; the reader refuses them. A pdfium `PdfDocumentFactory` is the
   missing piece.
2. **Annotations and highlights.** Neither the API nor the storage exists yet.
3. **Device tokens.** `POST /auth/login` returns the shared token today; per-device
   tokens and revocation are not built.
4. **Offline metadata.** Files and the last position survive without the server;
   the catalog does not.
5. **Progress conflicts.** Two devices on one book overwrite each other,
   last write wins.

## Notes

- The Docker image builds with `LAZIER_SKIP_ANDROID=true`, because the Android
  module needs the SDK. See `server/Dockerfile`.
- qBittorrent deletes always send `deleteFiles=false`: the library directory *is*
  the download directory, so deleting files would delete the book.
