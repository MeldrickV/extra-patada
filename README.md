# Xtra for Kick

<img align="left" width="100" src="https://github.com/MeldrickV/extra-patada/blob/main/app/src/main/ic_launcher-web.png"/>

Xtra for Kick is an open-source Android player and browser for [Kick.com](https://kick.com).

Built from the architecture of the classic Xtra for Twitch app and retargeted to Kick: browse live
streams by category, watch broadcasts with low-latency playback, and follow the chat in real time —
all without ads.

</br>
</br>

## Features

- **Browse** live streams and categories (official Kick public API + internal endpoints)
- **Playback** with AndroidX Media3 (ExoPlayer), low-latency HLS sources and quality selection
- **Real-time chat** over Kick's Centrifugo realtime WebSocket with badges, emotes and moderation (readable anonymously, no login)
- **OAuth 2.1 (PKCE)** login with Kick
- Follow channels and content creators
- Video-on-demand (past broadcasts) playback
- Clips browsing
- Downloads of streams and VODs
- Picture-in-picture, background audio and sleep timer
- Theme engine (light/dark), multiple network stacks (OkHttp / Cronet / HttpEngine)

> Note: some features rely on Kick's **internal, undocumented** endpoints (`/api/v2`, Centrifugo
> realtime). They may change without notice; the app falls back gracefully when possible.

## Download

Find released APKs under the [Releases](https://github.com/MeldrickV/extra-patada/releases) page.
Every push to `main` also produces a fresh debug APK as a CI artifact.

## Building from source

This project builds **entirely through GitHub Actions** — you don't need to install the Android
toolchain locally.

| Job | Command | Output |
|---|---|---|
| `assembleDebug` | `./gradlew assembleDebug` | debug APK artifact |
| `lintDebug` | `./gradlew lintDebug` | lint report |
| `testDebugUnitTest` | `./gradlew testDebugUnitTest` | JVM unit tests |
| `connectedDebugAndroidTest` | `./gradlew connectedDebugAndroidTest` | instrumented tests (emulator) |

### Local requirements (if you do build locally)

- JDK 21
- Android SDK with `compileSdk 37`
- Bash environment (Linux/macOS/WSL)

```bash
./gradlew assembleDebug
```

## Current roadmap status

- [x] Repository + CI/CD (build, lint, unit tests on GitHub Actions)
- [x] Platform abstraction layer (pluggable `Twitch` / `Kick` providers)
- [x] Kick data layer (official public API + internal `/api/v2`)
- [x] Kick playback (HLS `playback_url`, no tokens needed)
- [x] Kick chat read (Centrifugo realtime WebSocket, anonymous)
- [x] Login (OAuth 2.1 PKCE) + read follows
- [x] VODs / clips playback + clip & VOD downloads
- [x] Follow/unfollow from UI + token refresh + Kick live notifications (phase 6)
- [x] Live stream downloads + pagination in channel videos/clips (phase 7)
- [x] Kick chat write: send/reply, Kick emotes & badges, reconnect/TTL (phase 8)
- [x] Kick search & catalog: channels/streams/games/videos, game pages (phase 9)
- [x] Combined Twitch + Kick mode (phase 10): settings toggle "Platform" (`twitch` = existing kick-first feed with Twitch fallback, default; `both` = Kick+Twitch interleaved, de-duplicated by `platform|channelId` to avoid numeric-id collisions) applied to Popular, Following streams and Browse games.
- [x] Debt: lint baseline cleanup, more JVM tests, dead-code removal (phase 11): dead code removed, combined-paging + mapper JVM tests; lint baseline kept (warningsAsErrors rejected: exposes pre-existing false positives)

## Maintenance pass (2026-10, PRs #37–#58)

- Anti-crash: no more `runBlocking` on main thread (receiver `goAsync`, services persist with `NonCancellable`, progress via `tryEmit`); swallowed Kick errors now log under `KickRepository`/`XtraChat`.
- Kick VOD chat replay: tolerant timestamp parser (ISO + `yyyy-MM-dd HH:mm:ss`), lazy service anchor kept across resume, Kick manager fallback.
- Clip → full VOD with offset via shared `livestream_id → uuid` resolver (lists, deep links and player all agree).
- Faster Kick lists (explicit OkHttp timeouts, parallel channel lookups) and richer Following (thumbnail/game fallbacks, avatar backfill, followed pagination).
- Smaller core: `BaseChatAdapter` shared by clicked-message adapters, position persistence helpers in `BasePlaybackService`, player `start()` no longer stacks collectors.
- Dual login: separate Kick identity keys, per-platform logout, per-platform chat send, encrypted token store (`secure_tokens`, excluded from backups) with one-time migration and locked refresh.
- Downloads: shared Kick URL resolver, master playlists without `STABLE-VARIANT-ID` parse to real qualities, chat download disabled for Kick until replay export exists.
- Playback robustness: live-edge resync on both players, pinned source steps down, chat token refreshes before TTL expiry.
- Official-app alignment (reverse engineered from Kick TV/mobile APKs): channel info from `/info`, followed livestreams in one call, VODs from the v1 endpoint with ISO anchors, chat send via `v2/messages/send` with public fallback, unique request ids, VOD playback tracking.
- Replay that works: pages keyed by numeric channel id (not chatroom), smooth catch-up pacing instead of bursts, Kick emotes with browser user agent.
- Kick-correct sharing: `kick.com` channel/video/clip links with canonical web video ids.
- Android TV detection (leanback + ui mode) with larger chat text; realtime calls carry the proper platform headers and JSON content type.

## VOD push (2026-10, PRs #60–#64)

- Forward chat replay: first page anchored with `?start_time=` at the playhead, cursor forward, tick emission (75 max) and full invalidation on seek.
- Signed playback: full player payload mints `playback_url.vod` (cached per VOD), used by both players and downloads with cache-busting.
- IVS probing fallback for unmintable and subscriber-only VODs (3 bases x minute offsets, HEAD-validated).
- Kick-compatible downloader: absolute-URL resolution, browser headers, `VIDEO=` names, relative variants, `BYTERANGE` ranges, AES rejection, re-mint resume on 401/403, per-segment retry with backoff, throttled progress with ETA.
- Download UX: estimated sizes per quality, 720p default for Kick, subscriber-only notice, copy-URL handoff for external players.

## Sub-only + download hardening (2026-10, PRs #66–#75)

- Sub-only VODs play: legacy thumbnail kept for IVS probing (v1 signed thumbs blind it), mint retried with v1 ids (old uuids 404), listing fallback probe, `sub_only` status detection.
- Kick share URLs in video/clip lists (canonical v1 ids), clip downloads no longer crash on null ranges, resume offset snapshotted.
- Direct mp4 fast path in the download dialog (no full-file probe), Kick qualities with browser headers, live fetched-bytes progress.
- Watch while downloading: sibling `partial.m3u8` (no ENDLIST) opened for incomplete per-file downloads.
- Resume reconciles stored progress with files on disk (rewinds stale counters, verified partials); stale-file purge per download plus one-time orphan sweep of unreferenced dirs.

## License

Xtra for Kick is licensed under the [GNU Affero General Public License v3.0](LICENSE).