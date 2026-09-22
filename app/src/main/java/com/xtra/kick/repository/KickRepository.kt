package com.xtra.kick.repository

import com.xtra.kick.model.kick.KickCategoriesData
import com.xtra.kick.model.kick.KickCategoriesResponse
import com.xtra.kick.model.kick.KickChannelLivestream
import com.xtra.kick.model.kick.KickChannelResponse
import com.xtra.kick.model.kick.KickLivestreamsData
import com.xtra.kick.model.kick.KickLivestreamsResponse
import com.xtra.kick.model.kick.KickRealtimeConnectionInfo
import com.xtra.kick.util.KickApiHelper
import com.xtra.kick.util.NetworkUtils.executeAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

    suspend fun getChannelLivestream(slug: String): KickChannelLivestream? {
        return getChannel(slug).livestream
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
}