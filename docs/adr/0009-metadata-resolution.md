# ADR 0009 — Metadata resolution order

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** ADR 0001 decision 3, `adapter/outbound/epub/OpfCache.kt`, `adapter/outbound/filesystem/SeriesParser.kt`

## Context

ADR 0001 said metadata must come from the OPF, not the filename. The first real
library changed how strongly that holds, and in which direction.

Of 125 EPUBs:

```
dc:title                     125
dc:creator                   125
calibre:series                 0
calibre:series_index           0
belongs-to-collection          0
group-position                 0
```

Not one file declared a series. So the OPF supplies the title and the author, and
nothing else. Series and volume live in the **title**:

```
Sword Art Online, Vol. 03: Fairy Dance
A Certain Magical Index - Volume 21
A Certain Magical Index: New Testament - Volume 01
A Certain Magical Index - Volume SS1
```

Under the old path-only parser, 192 of 221 books shared one series name, taken
from a release folder. Series and volume were guesses.

## Decisions

**1. Each field resolves on its own.** A book can take its title from the OPF and
its series from the title, without either source having to supply everything.

| Field | Order |
| --- | --- |
| title | OPF `dc:title` → filename |
| author | OPF `dc:creator` → none |
| series | OPF `calibre:series` → OPF `belongs-to-collection` → title → path |
| volume | OPF `calibre:series_index` → OPF `group-position` → title → filename |
| language | OPF `dc:language`, region removed |
| publisher | OPF `dc:publisher` |
| publishedAt | OPF `dc:date` |
| description | OPF `dc:description`, tags stripped, 1200 characters |

**2. The author prefers creators marked `aut`.** EPUB 3 refines a creator with
`meta property="role"`. When no creator carries a role, every creator is joined
with a comma. The Yen Press files mark both the writer and the illustrator as
`aut`, so the value reads "Reki Kawahara and abec" — that is the file's claim,
not ours to rewrite.

**3. The title parser only reports a series when the title names a volume.** The
shapes above are matched explicitly. Splitting on any dash or colon would turn
`A Certain Magical Index - Love Letter SS` into a false series, and 39 titles in
this library have no volume at all.

**4. Series names are canonicalised.** The same series arrived as
`A Certain Magical Index: New Testament` from EPUB titles and as
`A Certain Magical Index New Testament` from PDF directories. Colons become
spaces and whitespace collapses, so one series stays one series.

**5. The path fallback uses the deepest meaningful directory.** Archive releases
nest books as `Pack/EPUB/Series/Volume.epub`. Format folders are skipped, and the
last remaining directory wins. The first directory is the pack name and is
usually wrong: `A Certain Magical Index - Volume 1-GT6+SS+Railgun LN`.

**6. The OPF is parsed once and cached.** Every `/books` call needs metadata for
every EPUB; every cover request needs the OPF too. One cache keyed by path, size
and modification time serves both. A replaced file changes the key.

## Consequences

Positive:

- 221 books, 125 with an author, 221 with a series, 143 with a volume.
- Nine series instead of one pack name.
- A cold `/books` takes 206 ms for 221 files. A warm call takes 85 ms.
- The cache also removes the second OPF parse from the cover path.

Negative:

- PDFs have no embedded metadata reader, so 96 PDFs have no author. The twin
  EPUB of the same volume does.
- The title parser is a heuristic. A release whose title says "Volume" without a
  number falls back to the path.
- Canonicalisation drops the colon: "A Certain Magical Index New Testament"
  reads slightly worse than the publisher's form.
- A cache entry is kept for a path that no longer exists until the process ends.

## Alternatives rejected

**Path only.** Produced one series name for 192 books.

**Trust the title verbatim as the series.** Would create one series per volume.

**Embed a metadata database.** The filesystem is the source of truth and the
library is small. Revisit if a scan exceeds a second.

**Parse PDF metadata.** A second format parser for an author string. PDFs are
secondary; deferred.
