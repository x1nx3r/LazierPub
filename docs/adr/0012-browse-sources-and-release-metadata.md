# 0012 — Browse sources and release metadata

- **Status:** Accepted
- **Date:** 2026-10-20

## Context

Browse used Prowlarr as one opaque source. Prowlarr can front several indexers,
and a reader needs to know which indexer a release came from, how healthy it is,
and what file formats it contains before spending a download on it.

Prowlarr's search response has no cover art and no list of the files inside a
torrent or NZB. It does have the indexer, protocol, size, seeders, leechers,
grabs, publish date, category tree, an info URL and a magnet or download link.

## Decisions

1. **Sources are Prowlarr indexers.** `GET /indexers` returns the enabled ones.
   The client shows "All sources" plus one chip per indexer. Choosing one adds
   `indexerIds` to the same `/search` request; Prowlarr still does the fan-out.
2. **The search result carries its metadata.** `SearchResult` gains the indexer
   id, protocol, grabs and formats. The client groups nothing by hand; one
   Prowlarr request answers every source.
3. **Formats come from the release name, then from the indexer page.** The
   server reads known extensions (`epub`, `pdf`, `cbz`, `cbr`, `mobi`, `azw3`,
   `fb2`, `zip`, `rar`, `7z`, `mp3`, `m4b`, `flac`) from the title and the
   torrent file name. When that names nothing, it reads the file list on the
   first ten results' indexer page. Nyaa publishes that list; other indexers do
   not share the HTML, so the adapter refuses every host except Nyaa's. The
   fetch is capped to ten results, four at a time, 2.5 seconds per page and five
   seconds overall, and it is cached by URL. A batch that finishes before the
   deadline keeps its formats; the rest stay "Unspecified", and the server does
   not guess from the category.
4. **No invented covers.** The result row uses a colour-coded format tile as the
   visual anchor. Prowlarr has no artwork for a release; a stock cover would
   imply data that does not exist.
5. **Prefer a magnet over Prowlarr's download endpoint.** Prowlarr's download
   URL can carry the API key in its query string, and that URL travels to the
   phone. A magnet from the result is used first; the download URL remains the
   fallback for releases without one.
6. **Filters have two shapes.** A source change runs a new search, because the
   upstream must narrow. A format filter is applied on the client over the
   results already in hand.
7. **The queue lives on its own screen.** Browse carries a downloads icon in its
   top bar, with a badge for active grabs. The queue, its progress and its
   remove action are not mixed into the search results. Five bottom tabs stay
   as they are; downloads open over Browse and the back gesture returns.

## Consequences

- Adding an indexer in Prowlarr adds a Browse source with no client change.
- The user can see seeders, size, age and grabs before a grab, and can filter
  to EPUB or PDF when the release names it.
- A release that names no format still gets one when it is in the top ten and
  Nyaa lists its files. Deeper results can remain "Unspecified" because the page
  fetch is deliberately capped.
- `indexerIds` must be sent as repeated keys. Prowlarr rejects `1,2` with 400,
  while the app's own API accepts both forms.

## Alternatives rejected

- **Scrape each indexer's page for the file list.** Slow, rate-limited, and
  different for every indexer; a browse search would fan out again outside
  Prowlarr.
- **Add the torrent to qBittorrent just to read its file list.** A browse action
  must not create a download.
- **Infer EPUB from the "Books/EBook" category.** Wrong for PDF and comic
  releases, which share the category.
- **Fetch covers from a metadata service.** Prowlarr results have titles, not
  stable book identifiers.
