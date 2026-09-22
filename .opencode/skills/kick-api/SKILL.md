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

## 3. Chat — PROTOCOLO REAL ACTUAL (Centrifugo self-hosted) — verificado 2026-09

**CONFIRMADO FUNCIONAL desde Node puro (TLS normal, sin cookies ni fingerprints)**: el chat de Kick
YA NO usa websockets.kick.com. Flujo (todo anónimo, sin login):

1. Client id: `uuid` (persistir por sesión; identity `{id, kind}` anónimo en la web es uuid).
2. `POST https://web.kick.com/api/v1/realtime/connection`
   body: `{"client":{"id":"<uuid>","type":"web"},"capabilities":{"accepted_providers":[{"provider":"pusher"},{"provider":"centrifugo"}]}}`
3. `POST https://web.kick.com/api/v1/realtime/auth/connection`  body: `{"client_id":"<uuid>"}`
   → `{"data":{"token":"<JWT>"},"message":"success"}` (JWT para el frame `connect`; TTL ~45min)
4. `POST https://web.kick.com/api/v1/realtime/channels/{channelId}/chat/connection`
   (mismo body que 2) → `{"data":{"connections":[{"credentials":{"url":"wss://realtime.<reg>.platform.kick.com/connection/websocket"},"provider":"centrifugo"}],"mode":"websocket"}}`
   - `channelId` = el **channel id** del streamer (e.j. 669746), NO el chatroom id.
5. Abrir el WebSocket a esa URL y enviar (protocolo Centrifugo con ids correlativos):
   - `{"connect":{"token":"<JWT>","name":"js"},"id":1}`
   - `{"subscribe":{"channel":"chatrooms.<chatroomId>.v2","flag":1},"id":2}`
   - Response: `{"id":1,"connect":{"client":"...","version":"6.9.2 PRO","ttl":...,"ping":25}}`, `{"id":2,"subscribe":{}}`
6. Mensajes: `{"push":{"channel":"chatrooms.<chatroomId>.v2","pub":{"data":{"event":"App\\Events\\ChatMessageEvent","data":"<json-as-string>","tags":{...}}}}`
   → `data.data` es string JSON con `{id, chatroom_id, content, type, created_at, sender{id,username,slug,identity{color,badges,badges_v2}}, metadata{message_ref}}`.
   - Emotes en content: `[emote:<id>:<name>]`.
7. Ping: el servidor manda `{"push":{"channel":"$centrifugo.push.default","pub":{...}}}` ping/ttl; reenviar connect si expira (ttl). Frames: JSON, algunos agrupados con `\n` (split por newline al recibir).

Extras: `POST /realtime/auth/channel` (body `{"channel":...}`) para auth por canal. Otros canales que
suscribe la web: `channel_{id}`, `channel.{id}`, `chatroom_{id}`, `chatrooms.{id}`, `drops_category_*`.

**Importante**: el `wss://websockets.kick.com/viewer/v1/connect?token=...` (viewer token, doble-uso
`X-CLIENT-TOKEN`) sigue VIVO pero SOLO para eventos del canal/live (tracking, `channel_handshake`,
`user_event`), NO para mensajes del chat. Ese dominio está bloqueado por TLS fingerprint para
websocket (aun con cookies); el REST `/viewer/v1/token` sí responde por curl/OkHttp.

**Bloqueo Cloudflare "security policy" (403)**: NO aplica a `web.kick.com/api/v1/realtime/*` ni a
`realtime.*.platform.kick.com` — ambos accesibles con TLS normal (Node/OkHttp), solo con headers
básicos (`x-app-platform: web`, UA navegador, `Referer: https://kick.com/`, `Content-Type: application/json`).
**DoH no interviene aquí**: el bloqueo residual (viewer WS) es WAF de prueba de cliente, no DNS.
No se necesitan cookies de navegador Android: el chat anónimo no usa cookies.

## 3b. (Histórico) Chat — viejo gateway viewer (NO usar para mensajes)

- Gateway: `wss://websockets.kick.com/viewer/v1/connect?token=...` (eventos, no mensajes).
- **Token (un solo uso)**: `GET https://websockets.kick.com/viewer/v1/token` con header `X-CLIENT-TOKEN`
  (constante horneada en bundles, puede rotar): `e1393935a959b4020a4491574f6490129f678acdaa92760471263db43487f823`.
- Chat histórico era Pusher `chatrooms.{id}.v2` con `App\Events\ChatMessageEvent` — hoy reemplazado por Centrifugo.
- Protección: WS bloqueado por TLS fingerprint fuera de navegador/extensiones; la nube Pusher vieja (4001).

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