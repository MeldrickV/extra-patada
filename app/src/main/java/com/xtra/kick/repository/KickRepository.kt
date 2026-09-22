package com.xtra.kick.repository

import com.xtra.kick.model.kick.KickCategoriesData
import com.xtra.kick.model.kick.KickCategoriesResponse
import com.xtra.kick.model.kick.KickChannelLivestream
import com.xtra.kick.model.kick.KickChannelResponse
import com.xtra.kick.model.kick.KickLivestreamsData
import com.xtra.kick.model.kick.KickLivestreamsResponse
import com.xtra.kick.util.KickApiHelper
import com.xtra.kick.util.NetworkUtils.executeAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

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
}