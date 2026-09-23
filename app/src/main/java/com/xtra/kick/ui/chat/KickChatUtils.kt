package com.xtra.kick.ui.chat

import com.xtra.kick.model.chat.Badge
import com.xtra.kick.model.chat.ChatMessage
import com.xtra.kick.model.chat.TwitchEmote
import com.xtra.kick.util.KickApiHelper
import org.json.JSONObject
import kotlin.time.Instant

object KickChatUtils {

    fun parseKickMessage(event: JSONObject): ChatMessage? {
        val sender = event.optJSONObject("sender")
        val identity = sender?.optJSONObject("identity")
        val content = event.optString("content").takeIf { it.isNotBlank() } ?: return null
        val emotes = parseKickEmotes(content)
        return ChatMessage(
            type = ChatMessage.USER_MESSAGE,
            id = event.optString("id").takeIf { it.isNotBlank() },
            userId = sender?.opt("id")?.toString(),
            userLogin = sender?.optString("slug")?.takeIf { it.isNotBlank() } ?: sender?.optString("username"),
            userName = sender?.optString("username")?.takeIf { it.isNotBlank() } ?: sender?.optString("slug"),
            message = content,
            color = identity?.optString("color")?.takeIf { it.isNotBlank() },
            emotes = emotes,
            badges = identity?.optJSONArray("badges_v2")?.let { array ->
                buildList {
                    for (i in 0 until array.length()) {
                        val badge = array.optJSONObject(i) ?: continue
                        val name = badge.optString("badge_type").takeIf { it.isNotBlank() }
                            ?: badge.optString("name").takeIf { it.isNotBlank() }
                            ?: continue
                        add(
                            Badge(
                                setId = name,
                                version = "1",
                                url = badge.optString("image_url").takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }.takeIf { it.isNotEmpty() }
            },
            timestamp = event.optString("created_at").takeIf { it.isNotBlank() }?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } },
        )
    }

    fun parseKickEmotes(content: String): List<TwitchEmote> {
        val emotes = mutableListOf<TwitchEmote>()
        Regex("""\[emote:(\d+):([^\]]+)\]""").findAll(content).forEach { match ->
            val id = match.groupValues[1]
            val name = match.groupValues[2]
            val url = KickApiHelper.emoteUrl(id)
            val emote = TwitchEmote(
                id = id,
                name = name,
                url1x = url,
                url2x = url,
                url3x = url,
                url4x = url,
                format = "gif",
                isAnimated = true,
                begin = match.range.first,
                end = match.range.last + 1,
            )
            emotes.add(emote)
        }
        return emotes
    }
}