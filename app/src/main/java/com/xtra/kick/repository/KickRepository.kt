package com.xtra.kick.repository

import com.xtra.kick.model.kick.KickCategoriesData
import com.xtra.kick.model.kick.KickCategoriesResponse
import com.xtra.kick.model.kick.KickCategory
import com.xtra.kick.model.kick.KickChannelResponse
import com.xtra.kick.model.kick.KickChannelVideo
import com.xtra.kick.model.kick.KickChannelsClipsResponse
import com.xtra.kick.model.kick.KickClip
import com.xtra.kick.model.kick.KickFollowedChannel
import com.xtra.kick.model.kick.KickLivestreamsData
import com.xtra.kick.model.kick.KickLivestreamsResponse
import com.xtra.kick.model.kick.KickOAuthIntrospection
import com.xtra.kick.model.kick.KickOAuthTokenResponse
import com.xtra.kick.model.kick.KickRealtimeConnectionInfo
import com.xtra.kick.model.kick.KickSearchChannel
import com.xtra.kick.model.kick.KickSelfUserResponse
import com.xtra.kick.model.kick.KickSingleClipResponse
import com.xtra.kick.model.kick.KickVideoResponse
import com.xtra.kick.util.KickApiHelper
import com.xtra.kick.util.NetworkUtils.executeAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class KickRepository(
    private val okHttpClient: Lazy<OkHttpClient>,
    private val json: Json,
) {

    private val headers = KickApiHelper.getBrowserHeaders()

    suspend fun getLivestreams(limit: Int, cursor: String?): KickLivestreamsData = withContext(Dispatchers.IO) {
        val url = KickApiHelper.PRIVATE_LIVESTREAMS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("page_size", limit.toString())
            .apply { cursor?.let { addQueryParameter("cursor", it) } }
            .build()
        val response = okHttpClient.value.newCall(Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick livestreams request failed: ${it.code}")
            }
            json.decodeFromString<KickLivestreamsResponse>(it.body.string()).data
        }
    }

    suspend fun getLivestreamsByCategory(categoryId: String, limit: Int, cursor: String?): KickLivestreamsData = withContext(Dispatchers.IO) {
        val url = KickApiHelper.PRIVATE_CATEGORY_LIVESTREAMS_URL.replace("{categoryId}", categoryId).toHttpUrl().newBuilder()
            .addQueryParameter("page_size", limit.toString())
            .apply { cursor?.let { addQueryParameter("cursor", it) } }
            .build()
        val response = okHttpClient.value.newCall(Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick category livestreams request failed: ${it.code}")
            }
            val body = it.body.string()
            if (body.trimStart().startsWith("[")) {
                KickLivestreamsData(livestreams = emptyList(), nextCursor = null)
            } else {
                json.decodeFromString<KickLivestreamsResponse>(body).data
            }
        }
    }

    suspend fun getCategories(limit: Int, cursor: String?): KickCategoriesData = withContext(Dispatchers.IO) {
        val url = KickApiHelper.PRIVATE_CATEGORIES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("page_size", limit.toString())
            .apply { cursor?.let { addQueryParameter("cursor", it) } }
            .build()
        val response = okHttpClient.value.newCall(Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick categories request failed: ${it.code}")
            }
            json.decodeFromString<KickCategoriesResponse>(it.body.string()).data
        }
    }

    suspend fun getChannel(slug: String): KickChannelResponse = withContext(Dispatchers.IO) {
        val response = okHttpClient.value.newCall(Request.Builder().url(KickApiHelper.channelUrl(slug)).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick channel request failed: ${it.code}")
            }
            json.decodeFromString<KickChannelResponse>(it.body.string())
        }
    }

    suspend fun getRealtimeConnectionInfo(clientId: String): KickRealtimeConnectionInfo = withContext(Dispatchers.IO) {
        val headers = KickApiHelper.getRealtimeHeaders()
        val wsUrl = postRealtime("/connection", headers, buildRealtimeClientBody(clientId))
        val token = postRealtime("/auth/connection", headers, JSONObject().put("client_id", clientId).toString())
        KickRealtimeConnectionInfo(wsUrl, token)
    }

    suspend fun getRealtimeAuthToken(clientId: String): String = withContext(Dispatchers.IO) {
        val headers = KickApiHelper.getRealtimeHeaders()
        postRealtime("/auth/connection", headers, JSONObject().put("client_id", clientId).toString())
    }

    private fun buildRealtimeClientBody(clientId: String): String {
        val providers = JSONArray()
            .put(JSONObject().put("provider", "pusher"))
            .put(JSONObject().put("provider", "centrifugo"))
        val client = JSONObject()
            .put("id", clientId)
            .put("type", "web")
        return JSONObject()
            .put("client", client)
            .put("capabilities", JSONObject().put("accepted_providers", providers))
            .toString()
    }

    private suspend fun postRealtime(path: String, headers: Map<String, String>, body: String): String = withContext(Dispatchers.IO) {
        val response = okHttpClient.value.newCall(
            Request.Builder()
                .url(KickApiHelper.REALTIME_BASE_URL + path)
                .apply {
                    headers.forEach { (key, value) -> header(key, value) }
                    post(body.toRequestBody())
                }
                .build()
        ).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick realtime request failed: ${it.code}")
            }
            val root = JSONObject(it.body.string())
            val data = root.optJSONObject("data") ?: root
            data.optString("token").takeIf { it.isNotBlank() } ?: run {
                data.optJSONArray("connections")
                    ?.optJSONObject(0)
                    ?.optJSONObject("credentials")
                    ?.optString("url")
                    ?.takeIf { url -> url.isNotBlank() }
                    ?: throw IllegalStateException("Kick realtime response missing token or connection url")
            }
        }
    }

    suspend fun getOAuthToken(clientId: String?, clientSecret: String?, redirectUri: String?, code: String, codeVerifier: String): KickOAuthTokenResponse = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", clientId ?: "")
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", redirectUri ?: "")
            .apply { clientSecret?.takeIf { it.isNotBlank() }?.let { add("client_secret", it) } }
            .build()
        postOAuthForm("/oauth/token", form)
    }

    suspend fun refreshOAuthToken(clientId: String?, clientSecret: String?, refreshToken: String): KickOAuthTokenResponse = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("client_id", clientId ?: "")
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .apply { clientSecret?.takeIf { it.isNotBlank() }?.let { add("client_secret", it) } }
            .build()
        postOAuthForm("/oauth/token", form)
    }

    suspend fun introspectOAuthToken(clientId: String?, clientSecret: String?, token: String): KickOAuthIntrospection = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .apply { clientId?.takeIf { it.isNotBlank() }?.let { add("client_id", it) } }
            .apply { clientSecret?.takeIf { it.isNotBlank() }?.let { add("client_secret", it) } }
            .add("token", token)
            .build()
        val response = okHttpClient.value.newCall(
            Request.Builder()
                .url("${KickApiHelper.OAUTH_BASE_URL}/oauth/token/introspect")
                .post(form)
                .build()
        ).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick token introspection failed: ${it.code}")
            }
            json.decodeFromString<KickOAuthIntrospection>(it.body.string())
        }
    }

    suspend fun getCurrentUser(token: String): KickSelfUserResponse = withContext(Dispatchers.IO) {
        val response = okHttpClient.value.newCall(Request.Builder().url("${KickApiHelper.PUBLIC_API_BASE_URL}/users").apply {
            KickApiHelper.getAuthHeaders(token).forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick current user request failed: ${it.code}")
            }
            json.decodeFromString<KickSelfUserResponse>(it.body.string())
        }
    }

    suspend fun getFollowedChannels(token: String, limit: Int, cursor: String?): List<KickFollowedChannel> = withContext(Dispatchers.IO) {
        val url = KickApiHelper.followedChannelsUrl().toHttpUrl().newBuilder()
            .addQueryParameter("page_size", limit.toString())
            .apply { cursor?.let { addQueryParameter("cursor", it) } }
            .build()
        val response = okHttpClient.value.newCall(Request.Builder().url(url).apply {
            KickApiHelper.getAuthHeaders(token).forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick followed channels request failed: ${it.code}")
            }
            val root = JSONObject(it.body.string())
            val array = root.optJSONArray("data") ?: root.optJSONArray("channels")
            val list = mutableListOf<KickFollowedChannel>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { item -> list.add(parseFollowedChannel(item)) }
                }
            }
            list
        }
    }

    suspend fun followChannel(token: String, channelId: Long): Boolean = withContext(Dispatchers.IO) {
        postFollow(token, channelId, "POST")
    }

    suspend fun unfollowChannel(token: String, channelId: Long): Boolean = withContext(Dispatchers.IO) {
        postFollow(token, channelId, "DELETE")
    }

    suspend fun getChannelVideos(slug: String): List<KickChannelVideo>? = withContext(Dispatchers.IO) {
        runCatching {
            val response = okHttpClient.value.newCall(Request.Builder().url(KickApiHelper.channelVideosUrl(slug)).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()).executeAsync()
            response.use {
                if (!it.isSuccessful) {
                    throw IllegalStateException("Kick channel videos request failed: ${it.code}")
                }
                val array = JSONArray(it.body.string())
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.toString()?.let { json.decodeFromString<KickChannelVideo>(it) }
                }
            }
        }.getOrNull()
    }

    suspend fun getVideo(uuid: String): KickVideoResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val response = okHttpClient.value.newCall(Request.Builder().url(KickApiHelper.videoUrl(uuid)).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()).executeAsync()
            response.use {
                if (!it.isSuccessful) {
                    throw IllegalStateException("Kick video request failed: ${it.code}")
                }
                json.decodeFromString<KickVideoResponse>(it.body.string())
            }
        }.getOrNull()
    }

    suspend fun getChannelClips(slug: String): List<KickClip> = withContext(Dispatchers.IO) {
        val response = okHttpClient.value.newCall(Request.Builder().url(KickApiHelper.channelClipsUrl(slug)).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick channel clips request failed: ${it.code}")
            }
            json.decodeFromString<KickChannelsClipsResponse>(it.body.string()).clips
        }
    }

    private suspend fun searchEnriched(query: String): JSONObject = withContext(Dispatchers.IO) {
        val url = KickApiHelper.SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .build()
        val response = okHttpClient.value.newCall(Request.Builder().url(url).apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick search request failed: ${it.code}")
            }
            JSONObject(it.body.string()).optJSONObject("data") ?: JSONObject()
        }
    }

    suspend fun searchChannels(query: String): List<KickSearchChannel> = searchEnriched(query).optJSONArray("channels")?.let { items ->
        buildList {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { obj ->
                    add(KickSearchChannel(
                        id = obj.optLong("id", 0),
                        username = obj.optString("username").takeIf { name -> name.isNotBlank() },
                        slug = obj.optString("slug").takeIf { name -> name.isNotBlank() },
                        profilePicture = obj.optString("profile_picture").takeIf { name -> name.isNotBlank() },
                        isLive = obj.optBoolean("is_live", false),
                    ))
                }
            }
        }
    } ?: emptyList()

    suspend fun searchStreams(query: String): List<KickSearchChannel> = searchEnriched(query).optJSONArray("livestreams")?.let { items ->
        buildList {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { obj ->
                    val channel = obj.optJSONObject("channel")
                    val category = obj.optJSONObject("category")
                    val thumbnail = obj.optJSONObject("thumbnail")
                    add(KickSearchChannel(
                        id = channel?.optLong("id", 0) ?: 0,
                        username = channel?.optString("username")?.takeIf { name -> name.isNotBlank() },
                        slug = channel?.optString("slug")?.takeIf { name -> name.isNotBlank() },
                        profilePicture = channel?.optString("profile_picture")?.takeIf { name -> name.isNotBlank() },
                        isLive = true,
                        title = obj.optString("title").takeIf { value -> value.isNotBlank() },
                        thumbnail = thumbnail?.optString("src")?.takeIf { value -> value.isNotBlank() },
                        startedAt = obj.optString("start_time").takeIf { value -> value.isNotBlank() },
                        viewerCount = obj.optInt("viewer_count", 0),
                        categoryId = category?.opt("id")?.toString()?.takeIf { id -> id != "null" },
                        categorySlug = category?.optString("slug")?.takeIf { name -> name.isNotBlank() },
                        categoryName = category?.optString("name")?.takeIf { name -> name.isNotBlank() },
                    ))
                }
            }
        }
    } ?: emptyList()

    suspend fun searchCategories(query: String): List<KickCategory> = searchEnriched(query).optJSONArray("categories")?.let { items ->
        buildList {
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { obj ->
                    val thumbnail = obj.optJSONObject("thumbnail")
                    add(KickCategory(
                        id = obj.opt("id")?.toString()?.takeIf { id -> id != "null" },
                        slug = obj.optString("slug").takeIf { name -> name.isNotBlank() },
                        name = obj.optString("name").takeIf { name -> name.isNotBlank() },
                        tags = obj.optJSONArray("tags")?.let { tags ->
                            buildList { for (j in 0 until tags.length()) { tags.optString(j).takeIf { value -> value.isNotBlank() }?.let { value -> add(value) } } }
                        } ?: emptyList(),
                        imageUrl = thumbnail?.optString("src")?.takeIf { value -> value.isNotBlank() },
                        viewersCount = obj.optInt("viewer_count", 0),
                    ))
                }
            }
        }
    } ?: emptyList()

    suspend fun getClip(id: String): KickClip? = withContext(Dispatchers.IO) {
        runCatching {
            val response = okHttpClient.value.newCall(Request.Builder().url(KickApiHelper.clipUrl(id)).apply {
                headers.forEach { (key, value) -> header(key, value) }
            }.build()).executeAsync()
            response.use {
                if (!it.isSuccessful) {
                    throw IllegalStateException("Kick clip request failed: ${it.code}")
                }
                json.decodeFromString<KickSingleClipResponse>(it.body.string()).clip
            }
        }.getOrNull()
    }

    suspend fun sendChatMessage(token: String, content: String, broadcasterUserId: Long, replyToMessageId: String? = null): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("content", content)
            .put("type", "user")
            .put("broadcaster_user_id", broadcasterUserId)
            .apply { replyToMessageId?.takeIf { it.isNotBlank() }?.let { put("reply_to_message_id", it) } }
            .toString()
        val response = okHttpClient.value.newCall(
            Request.Builder()
                .url("${KickApiHelper.PUBLIC_API_BASE_URL}/chat")
                .apply {
                    KickApiHelper.getAuthHeaders(token).forEach { (key, value) -> header(key, value) }
                    header("Content-Type", "application/json")
                }
                .post(body.toRequestBody())
                .build()
        ).executeAsync()
        response.use {
            if (it.isSuccessful) {
                null
            } else {
                runCatching {
                    val root = JSONObject(it.body.string())
                    root.optString("detail").takeIf { msg -> msg.isNotBlank() }
                        ?: root.optJSONObject("error")?.optString("message")?.takeIf { msg -> msg.isNotBlank() }
                        ?: root.optString("message").takeIf { msg -> msg.isNotBlank() }
                }.getOrNull() ?: "Kick chat send failed: ${it.code}"
            }
        }
    }

    private suspend fun postOAuthForm(path: String, form: FormBody): KickOAuthTokenResponse = withContext(Dispatchers.IO) {
        val response = okHttpClient.value.newCall(
            Request.Builder()
                .url(KickApiHelper.OAUTH_BASE_URL + path)
                .apply {
                    header("Accept", "application/json")
                    header("Content-Type", "application/x-www-form-urlencoded")
                }
                .post(form)
                .build()
        ).executeAsync()
        response.use {
            if (!it.isSuccessful) {
                throw IllegalStateException("Kick OAuth request failed: ${it.code}")
            }
            json.decodeFromString<KickOAuthTokenResponse>(it.body.string())
        }
    }

    private suspend fun postFollow(token: String, channelId: Long, method: String): Boolean = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(KickApiHelper.followChannelUrl(channelId))
            .apply {
                KickApiHelper.getAuthHeaders(token).forEach { (key, value) -> header(key, value) }
            }
        val request = when (method) {
            "DELETE" -> builder.delete().build()
            else -> builder.post("".toRequestBody()).build()
        }
        val response = okHttpClient.value.newCall(request).executeAsync()
        response.use {
            it.isSuccessful
        }
    }

    private fun parseFollowedChannel(obj: JSONObject): KickFollowedChannel {
        val channel = obj.optJSONObject("channel") ?: obj
        val user = obj.optJSONObject("user") ?: channel.optJSONObject("user")
        return KickFollowedChannel(
            id = channel.optLong("id", 0),
            username = user?.optString("username")?.takeIf { it.isNotBlank() } ?: channel.optString("username").takeIf { it.isNotBlank() },
            slug = (user ?: channel).optString("slug").takeIf { it.isNotBlank() },
            profilePicture = (user ?: channel).optString("profile_picture").takeIf { it.isNotBlank() },
        )
    }
}