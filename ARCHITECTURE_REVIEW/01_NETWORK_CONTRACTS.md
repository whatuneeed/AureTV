# Network contracts — inventory and gaps

Date: 18.11.2026 · baseline **0.5.60 / vc133**
Scope: `app/src/main/java`, static audit. No traffic capture, no proxy, no physical TV.
Count: **41** `HttpURLConnection` occurrences in **11** files.

This document is the contract that has to exist before any shared network helper is
introduced. A helper that hides a difference below would be worse than the current
duplication.

## 1. Call-site matrix

| File | Calls | Connect / read | Body cap | Redirects | Host check | Cancellation |
|---|---:|---|---:|---|---|---|
| `ambient/RedditFeed.kt` | 3 | 20 s / 20 s | 4 MiB | manual, `instanceFollowRedirects=false`, max 1 hop | feed host + image host allowlist | coroutine check in read loop |
| `ambient/RedditImageStore.kt` | 3 | 12 s / 12 s | 12 MiB | manual, max 3, every hop revalidated | `i.redd.it`, `preview.redd.it`, `external-preview.redd.it` | none inside loop; read timeout bounds |
| `data/AerialRefresh.kt` | 3 | 20 s / 20 s | 8 MiB | manual, max 3, scheme + host revalidated | `https` + `sylvan.apple.com` only | coroutine check in read loop |
| `data/CitySearch.kt` | 3 | 10 s / 10 s + 15 s total deadline | 512 KiB | **automatic, unvalidated** | endpoint hardcoded, final host not checked | coroutine check + deadline |
| `data/Weather.kt` | 5 | 8 s / 10 s (reverse 5 s / 5 s) | 2 MiB (geo 512 KiB) | **automatic, unvalidated** | seven hardcoded HTTPS hosts, final host not checked | coroutine check + deadline |
| `online/OnlineClient.kt` | 3 | 8 s / 8 s | 512 KiB | **automatic, unvalidated** | JustWatch / iTunes URL hardcoded, final host not checked | no coroutine check in read loop |
| `ui/components/OnlineInfoOverlay.kt` | 3 | 8 s / 8 s | 8 MiB | **automatic, unvalidated** | none at request time; input comes from `secureUrl()` | no coroutine check; read timeout bounds |
| `ui/components/SaverPreviewImage.kt` | 3 | 10 s / 10 s | 8 MiB | **automatic, unvalidated** | original URL checked by `isAerialMediaUrl`, final host not checked | no coroutine check; read timeout bounds |
| `ui/components/TopShelfRail.kt` | 3 | 10 s / 10 s | 8 MiB | **automatic, unvalidated** | `http`/`https`/`content:` branch; no final host check | `ensureActive()` before request; read timeout bounds |
| `update/UpdateDownloader.kt` | 8 | 15 s / 30 s | release asset size | manual, max 3, every hop allowlisted | metadata host allowlist | coroutine check in read loop |
| `update/UpdateNetwork.kt` | 4 | 15 s / 20 s | 256 KiB | manual, max 3, every hop allowlisted | metadata host allowlist | coroutine check in read loop |

## 2. Endpoints

| Purpose | Hosts | Scheme |
|---|---|---|
| Now/forecast | `api.open-meteo.com`, `api.met.no`, `wttr.in` | HTTPS |
| IP location | `ipapi.co`, `ipwho.is`, `get.geojs.io` | HTTPS |
| Reverse geocode | `nominatim.openstreetmap.org` | HTTPS |
| City search | `geocoding-api.open-meteo.com` | HTTPS |
| Online chart | `apis.justwatch.com` | HTTPS |
| Apple chart/preview | `itunes.apple.com` | HTTPS |
| Online images | `images.justwatch.com`, `*.mzstatic.com`, `*.apple.com` | HTTPS, validated by `secureUrl()` |
| Reddit feed | `www.reddit.com` | HTTPS |
| Reddit images | `i.redd.it`, `preview.redd.it`, `external-preview.redd.it` | HTTPS |
| Aerial catalogue | `sylvan.apple.com` | HTTPS for catalogue; clip/still HTTP allowed only by the scoped exception |
| Aerial clips/stills | `sylvan.apple.com` | HTTP or HTTPS (`isAerialMediaUrl`) |
| Update metadata/APK | host of `UPDATE_METADATA_URL` + metadata-declared asset hosts | HTTPS only, `UpdateUrlPolicy` |

`network_security_config.xml` is correct for this list: cleartext is permitted only
for `sylvan.apple.com`; every other host uses the system trust store.

## 3. Confirmed gaps

### G1. Redirects are trusted by half of the clients — P1 — RESOLVED 0.5.61–0.5.64

`CitySearch`, `Weather`, `OnlineClient`, `OnlineInfoOverlay`, `SaverPreviewImage` and
`TopShelfRail` use the platform default (`instanceFollowRedirects = true`).

Consequences:
- an endpoint or CDN can redirect to any HTTPS host; the launcher will follow it and
  send its User-Agent and the request path there;
- the final host is never checked against the original allowlist;
- a future HTTP downgrade is blocked by the network-security config, but an HTTPS
  redirect to an unrelated host is not.

Aerial, Reddit, Reddit images, update metadata and update APK already do the right
thing: manual redirects, a hop limit, and a host/scheme check on every hop.

Recommended contract: manual redirects for every request whose URL is not a fixed
compile-time asset endpoint; cap at 3–5; require `https`; require the same host or an
explicit suffix allowlist. Weather/geocoding fixed endpoints can keep automatic
redirects only if the final `connection.url` is checked.

### G2. User-Agent version drift — P2 — RESOLVED 0.5.65

Former values:
- `OVisionTV/0.5.7 (Android TV launcher)` — Reddit feed and Reddit images;
- `OVisionTVLauncher/6.2 (Android TV launcher)` — Weather;
- `OVisionTV (Android TV)` — online chart, online images, shelf images;
- `OVisionTV/<BuildConfig.VERSION_NAME> (Android TV update check)` — updates.

All HTTP clients, the ExoPlayer data source and the updater now use
`LauncherUserAgent.value = "OVisionTV/<BuildConfig.VERSION_NAME> (Android TV launcher)"`.
No client keeps its own version string. `LauncherUserAgentTest` fails if the value
drifts from the installed `VERSION_NAME` or carries the old `0.5.7`.

### G3. Missing early `Content-Length` rejection — P3 — RESOLVED 0.5.66

Every client that opens an `HttpURLConnection` now checks `contentLengthLong` before
reading the stream, and every body copy still enforces its streaming cap.
They still stop at the streaming cap, so this is not a memory hole; it only wastes a
connection and some bytes. Safe to add without changing a successful request.

### G4. Blocking reads are not coroutine-aware in three image loaders — P2 — RESOLVED 0.5.66

All four image loaders now read through `BoundedRead.readAtMost`, which checks
`ensureActive()` before every 64 KiB chunk. `BoundedReadTest` cancels a live copy
deterministically and asserts the `CancellationException`; it also covers the cap. Do not add a shared helper before deciding whether these loaders
should close the stream on cancellation.

### G5. Duplicated body-copy and redirect code — P3 — RESOLVED 0.5.67

One body copy (`ui/components/BoundedRead.kt`) and one `Location` resolver
(`data/RedirectUrl.kt`) now serve every client. Each loop still keeps its own
allowlist, hop limit and error mapping, which is intentional: those are policy,
not duplicated parsing. They differ in:
- cap accounting (`ByteArrayOutputStream` vs `contentLengthLong`);
- deadline handling (`Weather`, `CitySearch` use a wall-clock deadline; others only
  socket timeouts);
- log/error policy;
- partial-body cleanup.

A helper is justified only after G1 and G3 are settled. The helper must keep the
per-call cap and the per-call redirect policy explicit.

## 4. Non-goals / accepted exceptions

- **No DNS pinning** and no certificate pinning. The launcher talks to public,
  changing CDNs; pinning would break Aerial and update delivery.
- **No HTTP fallback** except `sylvan.apple.com`.
- **No cookies, no auth, no API keys.**
- **No background retry storm**: Weather, Reddit and update have bounded backoff;
  online chart and images intentionally have no retry loop.
- **No 720p+ promise** from this audit. It describes transport, not Apple's 640p
  source.
- **No claim of MITM resistance** beyond platform TLS.

## 5. Ordered remediation

1. **P1:** manual redirect + final-host validation for `CitySearch`, `Weather`,
   `OnlineClient`, `OnlineInfoOverlay`, `SaverPreviewImage`, `TopShelfRail`.
2. **P2:** one User-Agent source, built from `BuildConfig.VERSION_NAME`.
3. **P2:** cancellation-aware image reads, closing the stream on cancellation.
4. **P3:** early `contentLengthLong` reject in the clients that lack it.
5. **P3:** only then extract a shared request/body helper, with tests for caps,
   redirect downgrade, redirect count and cancellation.

## 6. Verification status

- Static audit only; no network capture on a physical TV.
- Emulator tests cover URL policy for updates (`UpdateMetadataTest`,
  `UpdateDownloaderTest`) and the Aerial/Reddit allowlists indirectly.
- **NOT TESTED:** hostile DNS, captive portal, MITM proxy, CDN redirect change,
  IPv6-only network, mobile hotspot, 1 GB RAM under image load.
