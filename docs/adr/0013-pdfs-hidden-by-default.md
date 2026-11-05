# 0013 — PDFs hidden by default

- **Status:** Accepted
- **Date:** 2026-11-05

## Context

The library holds 221 files: 125 EPUB and 96 PDF. The reader renders EPUB only.
PDFs were still listed, so they crowded the cover wall, inflated series volume
counts, and offered a detail page whose main action cannot work.

The server indexes PDFs for good reasons: they are part of the download folder,
they carry series and volume metadata, and they may be readable later. Hiding
them from the catalog would remove data the client cannot recover.

## Decisions

1. **Hide PDFs in the client by default.** The rule lives in `LibraryStore`:
   `visibleBooks`, `visibleSeries` and `visibleContinueItems` filter the raw
   server lists. Library, Recently added, History and series detail read the
   visible lists. Browse results and the download queue keep every format.
2. **The setting is a display preference, not a server filter.** More → Library
   has a "Show PDF books" switch, off by default and stored in SharedPreferences.
   A change applies immediately, without a request.
3. **Series are recounted from visible books.** A PDF-only series disappears. A
   mixed series stays, and its volume count, kinds and cover come from the EPUBs
   that remain. The server's series order is kept.
4. **Direct lookup still reaches a hidden book.** `book(id)` reads the raw list,
   so a route or a saved position does not break when the filter changes. The
   screens decide what to list; they do not erase data.

## Consequences

- The library shows what the reader can actually open.
- Statistics stay server-wide and still report every PDF, including hidden ones.
  The settings screen says so.
- Turning the switch on restores PDFs in place, with no scan and no network call.
- A future PDF renderer can flip the default or remove the switch. No server or
  wire-contract change is needed.

## Alternatives rejected

- **Filter on the server.** Every client would inherit one display choice, the
  app would refetch on each toggle, and other consumers would lose the files.
- **Delete or move PDFs out of the library.** Destructive, and it breaks the
  rule that the library equals the download folder.
- **List PDFs but disable the open action.** Still clutter, and a dead end on
  every row.
