# ADR 0008 — Full covers for the book page, downscaled covers for the grid

- **Status:** Proposed
- **Date:** 2026-10-19
- **Related:** ADR 0001 decision 2, `server/adapter/outbound/epub/EpubCoverProvider.kt`

## Context

The cover endpoint returns the image from the EPUB, unchanged. On the first real
library that produced:

```
125 covers  ·  536 KB average  ·  992 B minimum  ·  1.4 MB maximum  ·  65 MB total
```

Those files are print-resolution JPEGs, around 1600 pixels wide. A grid cell on a
phone is 100 to 160 pixels wide. The app would download 65 MB to draw a wall of
thumbnails, and a single scroll would cross tens of megabytes. On a mobile
connection that is a visible wait and real data cost.

The reader's title page needs the large image. The grid does not.

## Decision

**1. Add an optional width parameter.**

```
GET /books/{id}/cover          full image (today's behaviour)
GET /books/{id}/cover?w=320    no wider than 320 px
```

**2. Never upscale.** If the source is narrower than the requested width, return
the source.

**3. Cache each variant separately.** The cache key gains the width:
`<id>-<mtime>-w320.jpg`. The full-size entry keeps its current key, so existing
caches stay valid.

**4. The ETag includes the width.** A conditional request for `w=320` must not be
answered with the `304` for the full image.

**5. Downscale on first request, then serve from cache.** The same lazy policy as
the full image. No work during the library scan.

**6. Convert opaque images to JPEG at quality 0.85.** A 1600-pixel JPEG becomes
roughly 20 to 40 KB at 320 pixels. Images with alpha stay PNG, because a
transparent cover flattened onto black looks broken.

## Consequences

Positive:

- A grid of 100 covers drops from about 54 MB to about 3 MB.
- The reader keeps the full-resolution image for its title page.
- No change to the contract: the parameter is optional and the default is
  unchanged.

Negative:

- The cache can hold two variants per book, so its size grows.
- Decoding adds CPU and memory on first request. A 1.4 MB JPEG decodes to about
  15 MB in bitmap form, briefly.
- The server needs image decoding libraries. `javax.imageio` covers JPEG and PNG
  without a dependency, but not WebP.
- A cache sweep must now remove both variants for a deleted book.

## Alternatives rejected

**Let the client downscale.** Coil already decodes to the target size, but the
full bytes still cross the network. That is the cost this decision removes.

**Store only thumbnails.** The reader's cover page and any future detail view
would show a blurry image.

**Pre-generate variants during the scan.** Faster first paint, slower scan, and
storage for books nobody opens.

**Serve WebP everywhere.** Smaller, and Android supports it. `javax.imageio` does
not encode WebP without a plugin, so this would add a dependency. Revisit if the
size matters after the JPEG measurement.
