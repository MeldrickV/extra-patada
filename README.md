# Xtra for Kick

<img align="left" width="100" src="https://github.com/AndreyAsadchy/Xtra/blob/master/app/src/main/ic_launcher-web.png"/>

Xtra for Kick is an open-source Android player and browser for [Kick.com](https://kick.com).

Built from the architecture of the classic Xtra for Twitch app and retargeted to Kick: browse live
streams by category, watch broadcasts with low-latency playback, and follow the chat in real time —
all without ads.

</br>
</br>

## Features

- **Browse** live streams and categories (official Kick public API + internal endpoints)
- **Playback** with AndroidX Media3 (ExoPlayer), low-latency HLS sources and quality selection
- **Real-time chat** over Kick's Pusher WebSocket with badges, emotes and moderation
- **OAuth 2.1 (PKCE)** login with Kick
- Follow channels and content creators
- Video-on-demand (past broadcasts) playback
- Clips browsing
- Downloads of streams and VODs
- Picture-in-picture, background audio and sleep timer
- Theme engine (light/dark), multiple network stacks (OkHttp / Cronet / HttpEngine)

> Note: some features rely on Kick's **internal, undocumented** endpoints (`/api/v2`, Pusher
> WebSocket). They may change without notice; the app falls back gracefully when possible.

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

- [x] Repository + CI/CD (build, lint, tests on GitHub Actions)
- [x] Platform abstraction layer (pluggable `Twitch` / `Kick` providers)
- [x] Kick data layer (official public API + internal `/api/v2`)
- [x] Kick playback (HLS `playback_url`, no tokens needed)
- [x] Kick chat (Pusher WebSocket)
- [ ] Login / follows (OAuth 2.1 PKCE)
- [ ] VODs, clips, downloads (secondary phase)
- [ ] Combined Twitch + Kick mode

## License

Xtra for Kick is licensed under the [GNU Affero General Public License v3.0](LICENSE).