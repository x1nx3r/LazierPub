# ADR 0010 — The backend API surface

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** ADR 0003 (backend as the only API), ADR 0008 (thumbnails), ADR 0009 (metadata), `adapter/inbound/http/Routes.kt`

## Context

The backend started as a thin library listing. A reader needs more than a file
list, and every extra call the client cannot make becomes a screen it cannot
draw.

The screens the app needs, and what each one asks for:

| Screen | Needs |
| --- | --- |
| Library grid | filter by format, series, search text; paging; sort |
| Series list | series names, counts, a cover per series |
| Book detail | full metadata, cover at two sizes, download |
| Reader | the file with `Range`, a place to store the position |
| Continue reading | recent positions joined with book metadata |
| Shelves | create, rename-free manual collections, add and remove books |
| Discover | release search, grab, queue, cancel |
| Settings | server facts, auth |

## Decisions

### 1. Filtering, sorting and paging happen on the server

```
GET /books?q=&kind=epub,pdf&series=&author=&shelf=&sort=&order=&limit=&offset=
```

- `q` matches title, author or series, case-insensitively.
- `kind` accepts a comma-separated list.
- `series` is an exact, case-insensitive match.
- `author` is a contains match.
- `shelf` filters by shelf membership.
- `sort` is `title` (default), `author`, `series`, `added` or `size`.
- `order` is `asc` (default) or `desc`.
- `limit` defaults to 100 and is capped at 500. `offset` defaults to 0.

`total` is the filtered count before paging, so the client can draw a scrollbar
without a second call.

### 2. Series and statistics are derived, not stored

```
GET /series            every series with count, kinds and a cover book id
GET /stats             counts by kind, series, authors, bytes, covers
```

Derived answers cannot go stale. A series is the canonical name from ADR 0009.
The cover book is the lowest-volume member that has a cover.

### 3. Reading position is a Readium `Locator` in a JSON file

```
GET    /progress              every position, most recent first
GET    /progress/{bookId}     200 or 404
PUT    /progress/{bookId}     store a position, return it
DELETE /progress/{bookId}     204
```

The body carries `locator` (opaque JSON), `percent`, `chapter` and `finished`.
The server clamps `percent` to 0..1 and stamps `updatedAt` itself. It rejects a
book id that is not in the catalog, so the store cannot fill with ghosts.

A Locator is stored verbatim because it survives a font-size change. Storing a
page number would not.

### 4. Continue reading joins positions with books

```
GET /continue?limit=20&includeFinished=false
```

Returns `{book, progress}` pairs, most recently read first. Positions whose book
has disappeared are skipped, not returned as holes.

### 5. Shelves are manual collections, persisted as JSON

```
GET    /shelves
POST   /shelves                    { name } -> 201
DELETE /shelves/{id}               204
PUT    /shelves/{id}/books/{bookId}    200 with the updated shelf
DELETE /shelves/{id}/books/{bookId}    200 with the updated shelf
```

Names are trimmed, capped at 80 characters and unique ignoring case. Both add
and remove are idempotent. A shelf id that does not exist is `404`, and so is a
book id that is not in the catalog.

Shelves are deliberately simple: one ordered set of book ids, no nesting, no
sharing, no per-user scoping. The library is single-user.

### 6. Login exchanges a password for the existing token

```
POST /auth/login   { password } -> { token }
```

Compared against `LAZIER_PASSWORD`, then `LAZIER_TOKEN`, with a constant-time
comparison. The response is the static token: there is one user and one device
class. Device tokens, revocation and expiry are deferred (see below), so the
endpoint stops returning `501` without pretending to a security model the server
does not have.

### 7. Covers take an optional width

```
GET /books/{id}/cover         original bytes
GET /books/{id}/cover?w=320   downscaled, cached per width
```

Implements ADR 0008. The ETag includes the width, and the cache keeps one file
per width at the current modification time.

### 8. A grab names its category

```
POST /grabs { downloadUrl, title?, category? }
```

Without the field, every grab lands in the configured category. The response is
`202` with the created torrent. Queue listing and cancellation are unchanged.

### 9. Files are served with a name

`GET /books/{id}/file` sets `Content-Disposition: attachment` with the book's
filename, sanitized. `Range` already works through Ktor's `PartialContent`.

### 10. Conventions

- Every route except `/health`, `/config` and `/auth/login` requires the bearer
  token when one is configured.
- `400` is a caller mistake, `401` missing token, `404` unknown id, `409` a
  conflict (duplicate shelf name), `502` an upstream failure, `503` unconfigured
  upstream.
- JSON errors are `{ "error": "...", "detail": "..." }`.

## Non-goals for this round

| Item | Why not now |
| --- | --- |
| Device tokens, expiry, revocation | One user. The static token is the model; adding tokens without a UI to manage them is ceremony. |
| Highlights and annotations | Needs selection UI and a conflict story. Deferred with the reader. |
| Progress conflict resolution | Last write wins. Two devices editing the same book at once is not a real case here. |
| PDF covers | No renderer. The grid draws a placeholder. |
| Deleting books | The library is the download directory and the file is still seeding. Deletion belongs in the download client. |
| Multi-user shelves | Single-user library. |
| Library rescan endpoint | The catalog scans the filesystem per request, so there is nothing to invalidate. |

## Consequences

Positive:

- The client can draw all screens without a second source of truth.
- Derived series and stats cannot drift from the files.
- Reading position survives typography changes and app restarts.
- Shelves need no database: one JSON file, written atomically.
- Covers cost 20 to 40 KB in a grid instead of 536 KB.

Negative:

- JSON files are single-process state. Two server replicas would need a shared
  store.
- Last write wins on progress.
- The shelf list is returned in full; it is fine for hundreds of shelves and
  would need paging for thousands.
- Login returns a long-lived token, so a leaked token has no expiry.
