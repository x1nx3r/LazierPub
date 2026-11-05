# ADR 0003 — The backend is the only client-facing API

- **Status:** Accepted
- **Date:** 2026-10-19
- **Related:** `server/Config.kt`, `server/adapter/inbound/http/`, `docs/adr/0004-ports-and-adapters.md`

## Context

The client needs a library, search results, downloads and cover images. Three upstream
services provide those things:

| Service | Port | Role |
| --- | --- | --- |
| Jellyfin | 8096 | media server |
| Prowlarr | 9696 | indexer search |
| qBittorrent | 8081 | download client |

All three are reachable on the tailnet. The client could speak to each of them directly.

That option has costs:

- the qBittorrent password and the Prowlarr key would live on the phone;
- the client would need three base URLs and three authentication schemes;
- upstream quirks would leak into the app (magnet in `guid`, single-value category
  parameter, `metaDL` states);
- the phone would need Tailscale to use the app at all.

## Decisions

**1. One HTTP API.** The client talks to the backend only. The backend holds every
upstream credential.

**2. Configuration comes from the environment, with tailnet defaults.**

```
PROWLARR_URL=http://100.88.88.88:9696
QBITTORRENT_URL=http://100.88.88.88:8081
```

The same image runs on a laptop and on the server without a rebuild. Missing upstream
configuration is not fatal: the related routes return `503` and name the variables to
set.

**3. `POST /grabs` takes a target category.** Without it, every grab would land in the
qBittorrent category `novels`.

**4. Authentication is a bearer token.** One `LAZIER_TOKEN` today. Device tokens are
designed but not built, and `POST /auth/login` returns `501` with a reason. When the
token is unset the check is disabled, which is for local development only.

**5. The backend never proxies admin operations.** It exposes reader operations: list
books, fetch a file, fetch a cover, search releases, manage grabs, read and write
progress. It must not expose qBittorrent preferences, arbitrary torrent deletion, or
Jellyfin's admin API.

**6. One error model.**

| Status | Meaning |
| --- | --- |
| `400` | caller mistake, from a use case rule |
| `401` | missing or wrong token |
| `404` | no such book or cover |
| `502` | an upstream failed; the service is named |
| `503` | an upstream is not configured; the variables are named |

## Consequences

Positive:

- One base URL and one token on the device.
- Upstream credentials never leave the server.
- Upstream quirks are normalised once.
- The phone works without Tailscale when the backend is reachable.
- A failing upstream is visible as `502` with a reason, not a silent empty list.

Negative:

- The backend is an internet-facing service when exposed. Authentication stops being a
  detail and becomes a requirement.
- One extra network hop for every request.
- The backend must be deployed and kept alive; the client cannot fall back to Jellyfin
  directly.
- A backend outage stops the app even when Jellyfin is healthy.

## Alternatives rejected

**Direct client access over the tailnet.** Needs Tailscale on the phone and puts
credentials on the device. Rejected.

**Jellyfin as the facade.** Jellyfin cannot search indexers or manage torrents, and its
book model is flat.

**A per-upstream proxy with no domain logic.** Moves quirks into the client, which is
what this decision exists to prevent.
