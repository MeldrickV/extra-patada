package com.xtra.kick.ui.chat

import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.text.getSpans
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.xtra.kick.R
import com.xtra.kick.model.chat.ChatMessage
import com.xtra.kick.model.chat.CheerEmote
import com.xtra.kick.model.chat.Emote
import com.xtra.kick.model.chat.NamePaint
import com.xtra.kick.model.chat.STVBadge
import com.xtra.kick.model.chat.STVUser
import com.xtra.kick.model.chat.TwitchBadge
import com.xtra.kick.model.chat.TwitchEmote
import com.xtra.kick.ui.view.NamePaintImageSpan
import com.xtra.kick.util.chat.ChatAdapterUtils
import java.util.Random

abstract class BaseChatAdapter(
    protected val localTwitchEmotes: List<TwitchEmote>,
    protected val thirdPartyEmotes: List<Emote>,
    protected val globalBadges: List<TwitchBadge>,
    protected val channelBadges: List<TwitchBadge>,
    protected val cheerEmotes: List<CheerEmote>,
    protected val namePaints: List<NamePaint>,
    protected val stvBadges: List<STVBadge>,
    protected val personalEmoteSets: Map<String, List<Emote>>,
    protected val stvUsers: List<STVUser>,
    protected val enableTimestamps: Boolean,
    protected val timestampFormat: String?,
    protected val firstMsgVisibility: Int,
    protected val firstChatMsg: String,
    protected val redeemedChatMsg: String,
    protected val redeemedNoMsg: String,
    protected val rewardChatMsg: String,
    protected val replyMessage: String,
    protected val imageClick: (String?, String?, String?, Boolean?, Int?, Boolean?, String?) -> Unit,
    protected val useRandomColors: Boolean,
    protected val useReadableColors: Boolean,
    protected val isLightTheme: Boolean,
    protected val nameDisplay: String?,
    protected val useBoldNames: Boolean,
    protected val showNamePaints: Boolean,
    protected val showSTVBadges: Boolean,
    protected val showPersonalEmotes: Boolean,
    protected val showSystemMessageEmotes: Boolean,
    protected val chatUrl: String?,
    protected val getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?,
    protected val fragment: Fragment,
    protected val backgroundColor: Int,
    protected val imageLibrary: String?,
    protected val messageTextSize: Float,
    protected val emoteSize: Int,
    protected val badgeSize: Int,
    protected val gifSize: Int,
    protected val showGifMessages: Boolean,
    protected val emoteQuality: String,
    protected val animateGifs: Boolean,
    protected val enableOverlayEmotes: Boolean,
    protected val translateAllMessages: Boolean,
    protected val translateMessage: (ChatMessage, String?) -> Unit,
    protected val showLanguageDownloadDialog: (ChatMessage, String) -> Unit,
    protected val random: Random,
    protected val userColors: HashMap<String, Int>,
    protected val savedColors: HashMap<String, Int>,
    protected val savedLocalTwitchEmotes: MutableMap<String, ByteArray>,
    protected val savedLocalBadges: MutableMap<String, ByteArray>,
    protected val savedLocalCheerEmotes: MutableMap<String, ByteArray>,
    protected val savedLocalEmotes: MutableMap<String, ByteArray>,
    protected val loggedInUser: String?,
    var selectedMessage: ChatMessage?,
) : RecyclerView.Adapter<BaseChatAdapter.ViewHolder>() {

    abstract val messages: MutableList<ChatMessage>

    protected open val replyClick: ((ChatMessage) -> Unit)? = null

    var messageClickListener: ((ChatMessage, ChatMessage?) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatMessage = synchronized(messages) {
            messages.getOrNull(position)
        } ?: return
        val result = ChatAdapterUtils.prepareChatMessage(
            chatMessage, fragment.requireContext(), holder.textView, enableTimestamps, timestampFormat, firstMsgVisibility, firstChatMsg,
            redeemedChatMsg, redeemedNoMsg, rewardChatMsg, replyMessage, { url, name, format, isAnimated, source, thirdParty, emoteId -> imageClick(url, name, format, isAnimated, source, thirdParty, emoteId) },
            useRandomColors, random, useReadableColors, isLightTheme, nameDisplay, useBoldNames, showNamePaints, namePaints, showSTVBadges,
            stvBadges, showGifMessages, showPersonalEmotes, personalEmoteSets, stvUsers, showSystemMessageEmotes, enableOverlayEmotes, loggedInUser, chatUrl,
            getEmoteBytes, userColors, savedColors, translateAllMessages, translateMessage, showLanguageDownloadDialog, false, localTwitchEmotes,
            thirdPartyEmotes, globalBadges, channelBadges, cheerEmotes, savedLocalTwitchEmotes, savedLocalBadges, savedLocalCheerEmotes, savedLocalEmotes
        )
        if (chatMessage == selectedMessage) {
            holder.textView.setBackgroundResource(R.color.chatMessageSelected)
        }
        holder.bind(chatMessage, result.builder)
        ChatAdapterUtils.loadImages(
            fragment, holder.textView, { holder.bind(chatMessage, it) }, result.images, result.imagePaint, result.userName, result.userNameStartIndex,
            backgroundColor, imageLibrary, result.builder, result.translated, emoteSize, badgeSize, gifSize, emoteQuality, animateGifs, enableOverlayEmotes,
            chatMessage, savedColors, useReadableColors, isLightTheme, showLanguageDownloadDialog, false
        )
    }

    fun updateBackground(chatMessage: ChatMessage, item: TextView) {
        if (chatMessage.message.isNullOrBlank()) {
            item.setBackgroundResource(0)
        } else {
            when {
                chatMessage.isFirst && firstMsgVisibility < 2 -> item.setBackgroundResource(R.color.chatMessageFirst)
                chatMessage.reward?.id != null && firstMsgVisibility < 2 -> item.setBackgroundResource(R.color.chatMessageReward)
                chatMessage.systemMsg != null || chatMessage.msgId != null -> item.setBackgroundResource(R.color.chatMessageNotice)
                loggedInUser?.let { user ->
                    if (chatMessage.userId != null && chatMessage.userLogin != user) {
                        item.text.split(" ").find {
                            !Patterns.WEB_URL.matcher(it).matches() && it.contains(user, true)
                        } != null
                    } else false
                } == true -> item.setBackgroundResource(R.color.chatMessageMention)
                else -> item.setBackgroundResource(0)
            }
        }
        (item.text as? Spannable)?.let { view ->
            view.getSpans<NamePaintImageSpan>().forEach {
                it.backgroundColor = (item.background as? ColorDrawable)?.color
                view.setSpan(it, view.getSpanStart(it), view.getSpanEnd(it), SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun updateTranslation(chatMessage: ChatMessage, item: TextView, previousTranslation: String?) {
        (item.text as? SpannableString)?.let { text ->
            val builder = SpannableStringBuilder()
            builder.append(
                if (previousTranslation != null) {
                    text.dropLast(previousTranslation.length + 1)
                } else {
                    text
                }
            )
            ChatAdapterUtils.addTranslation(chatMessage, builder, builder.length, savedColors, useReadableColors, isLightTheme, showLanguageDownloadDialog, false)
            item.text = builder
        }
    }

    override fun getItemCount(): Int = synchronized(messages) {
        messages.size
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.let { view ->
                view.getSpans<ImageSpan>().forEach {
                    (it.drawable as? Animatable)?.start() ?:
                    (it.drawable as? LayerDrawable)?.let {
                        val lastIndex = it.numberOfLayers - 1
                        if (lastIndex > -1) {
                            for (i in 0..lastIndex) {
                                (it.getDrawable(i) as? Animatable)?.start()
                            }
                        }
                    }
                }
                view.getSpans<NamePaintImageSpan>().forEach {
                    (it.drawable as? Animatable)?.start()
                }
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.let { view ->
                view.getSpans<ImageSpan>().forEach {
                    (it.drawable as? Animatable)?.stop() ?:
                    (it.drawable as? LayerDrawable)?.let {
                        val lastIndex = it.numberOfLayers - 1
                        if (lastIndex > -1) {
                            for (i in 0..lastIndex) {
                                (it.getDrawable(i) as? Animatable)?.stop()
                            }
                        }
                    }
                }
                view.getSpans<NamePaintImageSpan>().forEach {
                    (it.drawable as? Animatable)?.stop()
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        val childCount = recyclerView.childCount
        if (animateGifs) {
            for (i in 0 until childCount) {
                ((recyclerView.getChildAt(i) as TextView).text as? Spannable)?.let { view ->
                    view.getSpans<ImageSpan>().forEach {
                        (it.drawable as? Animatable)?.stop() ?:
                        (it.drawable as? LayerDrawable)?.let {
                            val lastIndex = it.numberOfLayers - 1
                            if (lastIndex > -1) {
                                for (i in 0..lastIndex) {
                                    (it.getDrawable(i) as? Animatable)?.stop()
                                }
                            }
                        }
                    }
                    view.getSpans<NamePaintImageSpan>().forEach {
                        (it.drawable as? Animatable)?.stop()
                    }
                }
            }
        }
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView as TextView

        fun bind(chatMessage: ChatMessage, formattedMessage: SpannableStringBuilder) {
            textView.apply {
                text = formattedMessage
                textSize = messageTextSize
                if (chatMessage.type == ChatMessage.REPLY_MESSAGE && replyClick != null) {
                    movementMethod = null
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    TooltipCompat.setTooltipText(this, chatMessage.replyParent?.message ?: chatMessage.replyParent?.systemMsg)
                    setOnClickListener {
                        chatMessage.replyParent?.let { replyClick?.invoke(it) }
                    }
                } else {
                    movementMethod = LinkMovementMethod.getInstance()
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    TooltipCompat.setTooltipText(this, chatMessage.message ?: chatMessage.systemMsg)
                    setOnClickListener {
                        if (selectionStart == -1 && selectionEnd == -1 && chatMessage != selectedMessage) {
                            messageClickListener?.invoke(chatMessage, selectedMessage)
                            selectedMessage = chatMessage
                            setBackgroundResource(R.color.chatMessageSelected)
                            (text as? Spannable)?.let { view ->
                                view.getSpans<NamePaintImageSpan>().forEach {
                                    it.backgroundColor = (background as? ColorDrawable)?.color
                                    view.setSpan(it, view.getSpanStart(it), view.getSpanEnd(it), SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
