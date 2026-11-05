# ADR 0007 — Damaged archives are repaired, not hidden

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** `tools/repair-epub.py`, `server/adapter/outbound/epub/EpubCoverProvider.kt`, ADR 0001 decision 2

## Context

The first real library held 125 EPUBs. Cover extraction succeeded on 117. The
eight failures were not spread across the library: they came from one pack, and
each reported the same class of error.

```
python zipfile : Bad magic number for central directory
unzip          : start of central directory not found; zipfile corrupt
bsdtar         : Damaged Zip archive
java ZipFile   : invalid CEN header (bad signature)
7z             : opens, with one warning
```

The download was not at fault. qBittorrent reported the torrent complete
(`progress=1`, `amount_left=0`) and the file hash on the server matched the copy
used for the test. The release itself is damaged.

The cause is visible in the last bytes. The file holds **two** end-of-central-
directory records. The earlier record is correct: its offset points at a real
central header and its size ends exactly at its own position. The final record
points 255 bytes past the real central directory, into the middle of file data.
Those 255 bytes are the size of an entry that was appended after the directory,
`META-INF/calibre_bookmarks.txt`. The other seven files show the same shape, with
deltas between 225 and 261 bytes.

The last record sits at the end of the file, so strict readers select it and
fail. `7z` scans backwards and finds the earlier one, which is why it opens the
files at all.

This matters beyond covers. Readium uses `java.util.zip.ZipFile`, so these eight
books would not open in the reader either. A lenient extractor that produced
covers for them would hide eight unreadable books.

## Decisions

**1. Repair the file at the source.** The tool truncates the file at the last
consistent EOCD record and discards the bytes after it. The discarded region
holds only the appended bookmarks entry, which no reader needs.

**2. Verify before replacing.** The candidate bytes must pass a full CRC test
(`zipfile.testzip()`) before the tool touches the original. The write goes to a
temporary file in the same directory, is flushed and synced, then replaces the
original with `os.replace`.

**3. Never rewrite content.** The tool removes trailing bytes. It does not
recompress, reorder or re-encode anything. A file with no consistent record is
reported and left alone.

**4. Default to a dry run.** `--apply` is required to write. The tool prints
`WOULD REPAIR` otherwise.

**5. The tool is deliberately outside the server.** The server keeps its
read-only view of the library. Repair is an operator action, because it changes
a file that a torrent is still seeding.

## Consequences

Positive:

- All 125 EPUBs now open with `java.util.zip.ZipFile`, which is what the reader
  uses, and all 125 produce a cover.
- The fix is 331 bytes per damaged file, on average. Nothing is re-downloaded.
- The tool is idempotent. A second run reports `125 ok, 0 repairable`.
- `unzip -t` reports no errors on a repaired file.

Negative:

- A repaired file no longer matches the torrent's piece hashes. A recheck would
  mark it as corrupt and re-download it. Repair and seeding are in tension.
- The tool uses Python. The rest of the project is Kotlin. It runs on the host,
  not in the server image, so the cost is one more language in `tools/`.
- The repair is specific to this failure shape. A file with a truncated middle
  cannot be fixed this way.

## Alternatives rejected

**Make the extractor lenient.** A fallback ZIP reader in the server would produce
covers for eight books that Readium cannot open. It would also add a second ZIP
implementation to maintain.

**Point covers at Jellyfin.** Jellyfin cannot index what it cannot open.

**Re-download the pack.** The torrent is complete and the damage is in the
release. A new download would produce the same bytes.

**Delete the eight books.** They are readable after a 331-byte cut.
