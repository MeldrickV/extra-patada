package com.xtra.kick.ui.chat

import androidx.fragment.app.Fragment
import com.xtra.kick.model.chat.ChatMessage
import com.xtra.kick.model.chat.CheerEmote
import com.xtra.kick.model.chat.Emote
import com.xtra.kick.model.chat.NamePaint
import com.xtra.kick.model.chat.STVBadge
import com.xtra.kick.model.chat.STVUser
import com.xtra.kick.model.chat.TwitchBadge
import com.xtra.kick.model.chat.TwitchEmote
import java.util.Random

class MessageClickedChatAdapter(
    source: List<ChatMessage>,
    localTwitchEmotes: List<TwitchEmote>,
    thirdPartyEmotes: List<Emote>,
    globalBadges: List<TwitchBadge>,
    channelBadges: List<TwitchBadge>,
    cheerEmotes: List<CheerEmote>,
    namePaints: List<NamePaint>,
    stvBadges: List<STVBadge>,
    personalEmoteSets: Map<String, List<Emote>>,
    stvUsers: List<STVUser>,
    enableTimestamps: Boolean,
    timestampFormat: String?,
    firstMsgVisibility: Int,
    firstChatMsg: String,
    redeemedChatMsg: String,
    redeemedNoMsg: String,
    rewardChatMsg: String,
    replyMessage: String,
    override val replyClick: (ChatMessage) -> Unit,
    imageClick: (String?, String?, String?, Boolean?, Int?, Boolean?, String?) -> Unit,
    useRandomColors: Boolean,
    useReadableColors: Boolean,
    isLightTheme: Boolean,
    nameDisplay: String?,
    useBoldNames: Boolean,
    showNamePaints: Boolean,
    showSTVBadges: Boolean,
    showPersonalEmotes: Boolean,
    showSystemMessageEmotes: Boolean,
    chatUrl: String?,
    getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?,
    fragment: Fragment,
    backgroundColor: Int,
    imageLibrary: String?,
    messageTextSize: Float,
    emoteSize: Int,
    badgeSize: Int,
    gifSize: Int,
    showGifMessages: Boolean,
    emoteQuality: String,
    animateGifs: Boolean,
    enableOverlayEmotes: Boolean,
    translateAllMessages: Boolean,
    translateMessage: (ChatMessage, String?) -> Unit,
    showLanguageDownloadDialog: (ChatMessage, String) -> Unit,
    random: Random,
    userColors: HashMap<String, Int>,
    savedColors: HashMap<String, Int>,
    savedLocalTwitchEmotes: MutableMap<String, ByteArray>,
    savedLocalBadges: MutableMap<String, ByteArray>,
    savedLocalCheerEmotes: MutableMap<String, ByteArray>,
    savedLocalEmotes: MutableMap<String, ByteArray>,
    loggedInUser: String?,
    selectedMessage: ChatMessage?,
) : BaseChatAdapter(
    localTwitchEmotes, thirdPartyEmotes, globalBadges, channelBadges, cheerEmotes, namePaints, stvBadges,
    personalEmoteSets, stvUsers, enableTimestamps, timestampFormat, firstMsgVisibility, firstChatMsg,
    redeemedChatMsg, redeemedNoMsg, rewardChatMsg, replyMessage, imageClick, useRandomColors, useReadableColors,
    isLightTheme, nameDisplay, useBoldNames, showNamePaints, showSTVBadges, showPersonalEmotes,
    showSystemMessageEmotes, chatUrl, getEmoteBytes, fragment, backgroundColor, imageLibrary, messageTextSize,
    emoteSize, badgeSize, gifSize, showGifMessages, emoteQuality, animateGifs, enableOverlayEmotes,
    translateAllMessages, translateMessage, showLanguageDownloadDialog, random, userColors, savedColors,
    savedLocalTwitchEmotes, savedLocalBadges, savedLocalCheerEmotes, savedLocalEmotes, loggedInUser, selectedMessage
) {

    private val type = selectedMessage?.type
    private val userId = selectedMessage?.userId
    private val userLogin = selectedMessage?.userLogin

    override val messages = if (type == ChatMessage.USER_MESSAGE) {
        if (!userId.isNullOrBlank() || !userLogin.isNullOrBlank()) {
            synchronized(source) {
                source.filter {
                    (!userId.isNullOrBlank() && (it.userId == userId || it.replyParent?.userId == userId)) ||
                            (!userLogin.isNullOrBlank() && (it.userLogin == userLogin || it.replyParent?.userLogin == userLogin))
                }.toMutableList()
            }
        } else null
    } else {
        synchronized(source) {
            source.filter { it.type == type }.toMutableList()
        }
    }?.ifEmpty { null } ?: selectedMessage?.let { mutableListOf(it) } ?: mutableListOf()
}
