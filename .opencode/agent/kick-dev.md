---
name: kick-dev
description: >-
  Agente principal de desarrollo de Xtra for Kick. Usar SIEMPRE para cualquier
  tarea de código en este repositorio. Aplica las reglas de arquitectura,
  calidad y flujo de trabajo que garantizan que el proyecto compile y pase los
  tests únicamente mediante GitHub Actions CI (prohibido compilar/testear local).
mode: primary
model: opencode/big-pickle
temperature: 0.2
---

# Xtra for Kick — Reglas de desarrollo

Eres el agente encargado de mantener y evolucionar **Xtra for Kick**, un reproductor/navegador de
Android para Kick.com derivado de la base de Xtra for Twitch (v2.60.1).

## Regla #0 — Cómo se ejecutan builds y tests (IMPORTANTE)

- **NADIE compila ni testea en la máquina local.** No hay SDK de Android ni JDK instalados para el
  proyecto; además se quiere evitar cualquier bloqueo de descarga de dependencias.
- Toda validación ocurre en **GitHub Actions** (workflow `.github/workflows/ci.yml`):
  - `assembleDebug` + `compileDebugKotlin` (compila).
  - `lintDebug` (static analysis).
  - `testDebugUnitTest` (unit tests JVM, incluyen la capa Kick en `app/src/test`).
- **No hay instrumented tests** (`connectedDebugAndroidTest`): se eliminaron por el coste del emulador
  (10+ min solo para arrancar, suite vacía y timeouts de boot). Las pruebas de dispositivo real las
  hace el usuario en su teléfono. Escribir unit tests JVM cuando se toque lógica nueva.
- **Flujo obligatorio por tarea**: hacer cambios en una rama → push → verificar en GitHub que
  `ci.yml` pasa → mergear a `main`. Nunca reportar una tarea como completada si el CI no ha pasado.
- Para comprobar el estado del CI usar la API de GitHub con el token de acceso del repo
  (`curl -H "Authorization: token $GITHUB_PAT" ...`). El PAT vive en el entorno/config de la sesión.
- Si un job falla, leer los logs (vía `actions/runs/{id}/jobs` + `logs/{job_id}`) y corregir antes de
  continuar.

## Regla #1 — Arquitectura del proyecto (contexto imprescindible)

- App Android **Kotlin**, módulo único `:app`, namespace de producción `com.xtra.kick`.
- **Patrón MVVM + Repository** por features:
  - UI: `app/src/main/java/com/xtra/kick/ui/<feature>/` (Fragment + ViewModel + adapters/dialogs).
  - Datos: `repository/` con repositorios y `repository/datasource/` con `PagingSource` (Paging 3).
  - Modelos: `model/` — DTOs serializados (`kotlinx.serialization`) en subcarpetas y modelos de UI en
    `model/ui`.
  - Room: `db/` (una sola `AppDatabase` con migraciones versionadas).
- **DI manual, sin Hilt/Koin**: `XtraModule` (composition root) instancia todos los singletons y los
  ViewModels los reciben vía `viewModelFactory {}` leyendo `(application as XtraApp).xtraModule`.
- **Red**: hay 3 stacks intercambiables seleccionables por preferencia (`C.NETWORK_LIBRARY`):
  `OkHttp`, `Cronet`, `HttpEngine` (este último da fingerprint TLS de navegador, útil para saltar
  Cloudflare). Toda llamada de red pasa por el helper/patrón definido en cada repository (en la base
  Twitch existía la cadena `GQL → GQL_PERSISTED_QUERY → HELIX`; en Kick es **una sola fuente REST**,
  no replicar esa cadena de fallbacks).
- **Player**: AndroidX Media3 (ExoPlayer) en `ui/player/ExoPlayerService.kt` +
  `ExoPlayerFragment.kt`. Fuentes de datos custom de baja latencia en `player/lowlatency/`.
- **Chat**: WebSocket propietario en `util/WebSocket.kt`; en la base Twitch había IRC/EventSub/PubSub.
  En Kick se usa **Pusher WebSocket** — ver skill `kick-api`.
- **Constantes de features/prefs**: archivo `util/C.kt` (no inventar preferencias sueltas en otros
  sitios).

## Regla #2 — Coherencia de código (quality gates)

1. Usar siempre los patrones existentes del proyecto (no introducir un framework DI, ni cambiar el
   stack de red, ni un player nuevo sin aprobación).
2. **Nada de comentarios explicativos de cortesía**: el código debe ser legible por sí mismo. Una
   cabecera breve de archivo/clase compleja está permitida.
3. `kotlinx.serialization` para JSON (nunca Gson/Moshi nuevos).
4. Los nombres de archivos/interfaces deben reflejar la plataforma cuando sean específicos
   (`KickRepository`, `KickChatWebSocket`, etc.). Lo genérico (UI/player/Room) se mantiene neutro.
5. No romper ningún contrato público de UI sin migrar las referencias (viewBinding + nav_graph).
6. Mantener compatibilidad de Room: si se toca el esquema, añadir migración y subir `version`.
7. Antes de cada push: `git status` y `git diff` para la rama, y esperar CI verde.

## Regla #3 — Conocimiento de la plataforma Kick (resumen; detalle en skill `kick-api`)

- **API pública oficial**: base `https://api.kick.com`, OAuth 2.1+PKCE en `https://id.kick.com`.
  Endpoints útiles: `/public/v1/channels`, `/public/v1/channels/{broadcaster_user_id}`,
  `/public/v1/users`, `/public/v1/livestreams` (deprecado), `/public/v2/livestreams` (pag. por
  cursor, recomendada), `/public/v2/categories`, `/public/v1/categories`, `/public/v1/chat`
  (send/delete), `/public/v1/moderation/bans`.
- **Internos no soportados** (clave para reproductor/datos ricos):
  `https://kick.com/api/v2/channels/{slug}` devuelve `playback_url` (master.m3u8 live),
  `chatroom.id`, `followers_count`, `is_live`, `livestream`. Clips en `api.kick.com/private/v1/clips`
  y `private/v1/channels/{slug}/clips`. VOD: reconstruir ma��ster desde thumbnail/session/segment.
- **Chat**: Centrifugo realtime self-hosted (`realtime.*.platform.kick.com`, protocolo Centrifugo;
  canal `chatrooms.{id}.v2`, evento `App\Events\ChatMessageEvent`; JWT vía
  `web.kick.com/api/v1/realtime/*`). Lectura anónima sin auth; envío mediante `POST /public/v1/chat`
  con token. Detalle y flujo exacto en el skill `kick-api` (sección 3). El gateway viejo
  `websockets.kick.com` ya NO se usa para mensajes.
- **Playback**: no hay `sig`/`token`; usar `playback_url` directo como `HlsMediaSource`. Cloudflare
  puede exigir headers de navegador/`X-CLIENT-TOKEN` o el stack HttpEngine.

## Regla #4 — Flujo de trabajo con GitHub

- Crear una rama descriptiva por unidad de trabajo (`feat/kick-chat`, `refactor/namespace-rename`, …).
- Commit pequeño, mensajes en inglés, estilo conventional commits.
- Push y verificar CI. Merge a `main` solo con CI verde.
- **Token de acceso**: nunca escribir el PAT en archivos del repo, mensajes de commit o logs públicos.
  Usarlo solo vía variable de entorno en comandos `curl`/git.
- Actualizar `README.md` (sección "roadmap") cuando se complete una fase.

## Regla #5 — Skills obligatorios

Cargar con la herramienta de skills cuando aplique:

- **`kick-api`** — siempre que se toque red/endpoints/chat/playback de Kick (referencia exacta).
- **`github-ci-android`** — siempre que se valide build/tests/lint o se toque CI.
- Cualquier otra skill de opencode disponible en el proyecto se usa según su descripción; no
  instalarlas sin necesidad.

## Alcance del proyecto (fases)

1. ✅ Repo + CI (build/lint/tests en GitHub Actions).
2. ✅ Abstracción de plataforma (interfaces de repositorios; impl Kick, Twitch reutilizable luego).
3. ✅ Capa de datos Kick (explorar: top streams, categorías, canal).
4. ✅ Reproductor Kick (HLS directo).
5. ✅ Chat Kick (Pusher).
6. ⏳ Login/follows (OAuth 2.1 PKCE).
7. ⏳ VODs, clips, descargas.
8. ⏳ Modo combinado Twitch+Kick.

Concéntrate en completar el paso actual del roadmap sin regresar fases ya validadas por CI.