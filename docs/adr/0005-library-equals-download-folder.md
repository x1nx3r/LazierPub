# ADR 0005 — The library folder is the download folder

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** `server/adapter/outbound/filesystem/`, `server/Config.kt`, qBittorrent category `novels`

## Context

Books have no importer. Sonarr and Radarr read a finished download, rename the file and
move it into the library. Books have no equivalent, so something must bridge the gap
between "a torrent finished" and "a book is in the library".

Two approaches exist:

- **staging plus a move**, which needs a component that watches for completion;
- **download straight into the library**, which removes the bridge.

A second constraint: qBittorrent can be told to keep the final filename while a file is
still downloading. If it does, the library contains partially written books.

## Decisions

**1. The qBittorrent category saves directly into the library root.**

```
category novels -> /data/novels   (host: /data/isyrr/novels)
```

No importer. No second copy. A finished book is already in place.

**2. `incomplete_files_ext` is enabled.**

```json
{ "incomplete_files_ext": true }
```

Incomplete files receive a `.!qB` suffix. The scanner ignores unknown extensions, so a
half-downloaded book is invisible. This setting was found disabled on a live install. It
is not optional for this design.

**3. Torrent removal always sends `deleteFiles=false`.**

Deleting files would delete the book, because the library is the download folder.

**4. The catalog reads the filesystem, not Jellyfin.**

`/books` scans the directory on each request. Deleted files disappear from the catalogue
immediately, so the backend is always consistent with the disk.

**5. Category relocation is expected, not surprising.** qBittorrent relocated an existing
torrent when the category path changed, because "relocate on Category Save Path changed"
is enabled. That is useful during setup and must be understood before it is triggered on
a large library.

## Consequences

Positive:

- No importer, no duplication and no move step.
- Hardlinks are unnecessary because there is only one copy.
- Seeding continues from the library, at no extra disk cost.
- The catalogue is always consistent with the filesystem.

Negative:

- A torrent removed with `deleteFiles=true` deletes the book. Every client must use
  `false`.
- Anything else inside a release also lands in the library. Cover images, `.txt` notes and
  `.nfo` files appear next to books. The scanner ignores them, but a human sees them.
- Release folder names become library paths, including awkward characters. A real release
  produced a directory containing a full-width slash (`U+FF0F`).
- Deep archive structures become library paths. One downloaded pack produced
  `A Certain Magical Index - Volume 1-GT6+SS+Railgun LN/EPUB/A Certain Magical Index/...`,
  so the first directory is a pack name, not a series name. This is the strongest argument
  for OPF metadata (ADR 0001, decision 3).
- Jellyfin, if pointed at the same folder, keeps database rows for deleted files. Scans do
  not prune them. They must be removed with `DELETE /Items/{id}`, one per item. The
  backend does not share this problem because it scans the disk.

## Alternatives rejected

**Staging folder plus a move service.** Correct, and it needs a watcher, retries, partial
detection and conflict handling. Not worth a component for a single-user library.

**Let Jellyfin own the download folder.** Jellyfin cannot search or grab, so the download
client would still write there. The folder would have two owners.

**Keep the partial-file suffix disabled and filter by size.** Heuristics. The suffix is
exact.
