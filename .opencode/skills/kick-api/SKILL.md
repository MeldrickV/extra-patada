---
name: kick-api
description: >-
  Referencia exacta de la superficie de APIs de Kick.com (pública oficial, interna no soportada,
  chat Pusher WebSocket y playback HLS). USAR SIEMPRE que se toque red, endpoints, chat o playback
  de Kick en Xtra for Kick. También útil para mapear datos Twitch→Kick.
---

# Kick.com API Reference (para Xtra for Kick)

Resumen consolidado de las APIs de Kick para construir el reproductor/navegador.

## 1. API pública oficial — `https://api.kick.com`

OAuth 2.1 **Authorization Code + PKCE** (endpoints de OAuth en `https://id.kick.com`):
`/oauth/authorize`, `/oauth/token`, `/oauth/token/introspect`. App Access Token (client
credentials) para server-to-server.

Scopes relevantes: `channel:read`, `channel:write`, `user:read`, `chat:write`,
`moderation:ban`, `moderation:chat_message:manage`, `channel:rewards:*`, `events:subscribe`,
`kicks:read`, `streamkey:read`.

### Endpoints útiles para la app

| Método y ruta | Descripción |
|---|---|
| `GET /public/v2/livestreams` | Directos activos, **paginación por cursor** (`next_cursor`), filtros por `category_id` y `language_code`. Recomendado sobre v1. |
| `GET /public/v1/livestreams` | Deprecado. Filtros por `broadcaster_user_id`, `category_id`, `language`, `sort=viewer_count\|started_at`. |
| `GET /public/v1/livestreams/stats` | Nº total de directos activos. |
| `GET /public/v2/categories` | Categorías paginadas (recomendado). |
| `GET /public/v1/categories?q=` | Búsqueda de categorías (deprecado pero útil). |
| `GET /public/v1/categories/{id}` | Categoría por id (+ `tags`, `viewer_count` en v2). |
| `GET /public/v1/channels?broadcaster_user_id=` | Info del canal. |
| `GET /public/v1/channels?slug=` | Canal por slug. |
| `PATCH /public/v1/channels` | Actualizar metadata del stream (scope `channel:write`). |
| `GET /public/v1/users?id=` | Usuarios; sin ids devuelve el usuario autenticado. |
| `POST /public/v1/chat` | Enviar mensaje (body: `{content, broadcaster_user_id, type:"user", reply_to_message_id?}`). |
| `DELETE /public/v1/chat/{message_id}` | Borrar mensaje (moderación). |
| `POST/DELETE /public/v1/moderation/bans` | Ban/timeout y unban. |

### Lagunas importantes de la API pública (por eso usamos internos v2)
No hay: followers propios del usuario, clips, VODs, URL de reproducción HLS, ni lista de canales
seguidos. Estas funcionalidades requieren los endpoints internos.

## 2. Endpoints internos NO soportados — pueden cambiar sin aviso

Base: `https://kick.com/api/v2/...` (también `https://api.kick.com/private/v1/...`).

| Endpoint | Datos útiles |
|---|---|
| `GET /api/v2/channels/{slug}` | Todo lo rico: `playback_url` (master.m3u8 live), `chatroom.id`, `livestream{...}`, `is_live`, `followers_count`, `bio`, `verified`, redes sociales, badges de suscriptores. |
| `GET /private/v1/channels/{slug}/clips` | Clips del canal. |
| `GET /private/v1/clips` | Clips (recientes/tendencias). |
| `GET /api/v2/users/{username}` | Perfil del usuario. |
| Follows del usuario | Endpoint paginado interno; requiere session token (no OAuth). En la app lo modelamos como fase posterior. |

Cloudflare: los endpoints internos pueden exigir headers de navegador (User-Agent, `X-CLIENT-TOKEN`
público de la web de Kick) o TLS browser-grade. En Xtra for Kick usamos el stack `HttpEngine`/Cronet
cuando sea necesario.

## 3. Chat — WebSocket self-hosted tipo Pusher (lectura pública, sin auth)

- Gateway actual: `wss://websockets.kick.com/viewer/v1/connect?token=...`.
- **Token (un solo uso)**: `GET https://websockets.kick.com/viewer/v1/token` con header
  `X-CLIENT-TOKEN` (constante pública horneada en el frontend — ver más abajo). Responde
  `{"data":{"token":"01K...","message":"OK"}}`. Refetch en CADA reconnect, el anterior queda gastado.
- `X-CLIENT-TOKEN` actual (extraído de los bundles `_next/static/chunks` de kick.com, puede rotar;
  si da 401/403 re-extraer grepeando `NEXT_PUBLIC_WEBSOCKET_CLIENT_TOKEN`):
  `e1393935a959b4020a4491574f6490129f678acdaa92760471263db43487f823`.
- Suscripción: frame `pusher:subscribe` → canal `chatrooms.{id}.v2`. `chatroom.id` de
  `GET /api/v2/channels/{slug}`. Confirmación: `pusher_internal:subscription_succeeded`.
- Evento de mensaje: `App\Events\ChatMessageEvent` → campos: `id`, `content`, `sender`
  (`id`, `username`, `slug`, `identity{color, badges}`, `isSubscribed`), `chatroom.id`,
  `created_at`, `type`. Otros eventos: `MessageDeleted`, `PinnedMessageCreated`, `UserBanned`,
  `SubscriptionEvent`, `FollowEvent`, `GiftEvent`.
- **PROTECCIÓN CLOUDFLARE (BLOQUEO CONOCIDO)**: la antigua nube Pusher
  (`wss://ws-us2.pusher.com/app/32cbd69e4b950bf97679`) está MUERTA (4001 desde la migración).
  El gateway nuevo exige fingerprint TLS de navegador: el GET del token sí responde vía curl/OkHttp,
  pero el upgrade WS se bloquea a nivel TLS/Cloudflare — sin cookies `__cf_bm`/`_cfuvid` da 403 con
  `{"message":"Forbidden"}`; incluso con esas cookies + `Origin: https://kick.com` + UA de navegador
  y `X-CLIENT-TOKEN` en el handshake, la conexión sube 101 pero el servidor NO envía
  `pusher:connection_established` (blackhole silencioso). Solo funciona desde navegador/extensiones
  (lo confirma el módulo kick-chat, que requiere `websockets.kick.com` en `host_permissions`).
  En Xtra por ahora: **chat BLOQUEADO en fase 4**; probar como fallback el stack `HttpEngine`
  (Codename Kernel) que sí trae fingerprint de Chrome antes de descartarlo.
- Envío: NO por el socket; usar `POST /public/v1/chat` con token OAuth (`chat:write`).
- Reutilizar `util/WebSocket.kt` (cliente WS propietario de la app).

## 4. Playback (live y VOD) — HLS directo, sin tokens

- **Live**: `playback_url` del `GET /api/v2/channels/{slug}` →
  `HlsMediaSource` directo (AWS IVS / live-video.net master.m3u8). No hay `sig`/`token` como Twitch.
- **Calidades**: parsear el master.m3u8 (ya existe `util/m3u8/PlaylistUtils.kt`).
- **VOD** (fase posterior): reconstruir la master URL a partir de la thumbnail y fechas de sesión/segmento:
  `https://stream.kick.com/ivs/v1/{aws_id}/{session_id}/{yyyy}/{MM}/{dd}/{HH}/{mm}/{segment_id}/media/hls/master.m3u8`.

## 5. Mapeo de conceptos Twitch → Kick

| Twitch | Kick |
|---|---|
| `gql.twitch.tv/gql` (Apollo) | REST `api.kick.com` + `kick.com/api/v2` (kotlinx-serialization) |
| Helix `/helix/streams` | `/public/v2/livestreams` |
| Helix `/games/top` | `/public/v2/categories` |
| `usher.ttvnw.net` m3u8 + token | `playback_url` directo |
| IRC/EventSub/PubSub chat | Pusher WebSocket `chatrooms.{id}.v2` |
| OAuth device flow | OAuth 2.1 + PKCE |
| 7TV/BTTV/FFZ emotes | Se mantienen + badges nativas de Kick |

## Fuentes de referencia externa
- Docs oficiales: https://docs.kick.com · Swagger: https://api.kick.com/swagger/index.html
- KickDevDocs (roadmap oficial): https://github.com/KickEngineering/KickDevDocs
- Listado de endpoints internos: https://github.com/fb-sean/kick-website-endpoints