package com.github.andreyasadchy.xtra.ui.common

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.getSpans
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.Image
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.ui.view.chat.CenteredImageSpan
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import java.util.Random
import kotlin.collections.set
import kotlin.math.floor
import kotlin.math.pow


private const val RED_HUE_DEGREES = 0f
private const val GREEN_HUE_DEGREES = 120f
private const val BLUE_HUE_DEGREES = 240f
private const val PI_DEGREES = 180f
private const val TWO_PI_DEGREES = 360f

class ChatAdapter(
    private val fragment: Fragment,
    private val emoteSize: Int,
    private val badgeSize: Int,
    private val randomColor: Boolean,
    private val isLightTheme: Boolean,
    private val useThemeAdaptedUsernameColor: Boolean,
    private val boldNames: Boolean,
    private val emoteQuality: String,
    private val animateGifs: Boolean,
    private val enableZeroWidth: Boolean,
    private val enableTimestamps: Boolean,
    private val timestampFormat: String?,
    private val firstMsgVisibility: Int,
    private val firstChatMsg: String,
    private val rewardChatMsg: String,
    private val redeemedChatMsg: String,
    private val redeemedNoMsg: String,
    private val imageLibrary: String?,
    private val channelId: String?,
    private val getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?,
    private val chatUrl: String?) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    var messages: MutableList<ChatMessage>? = null
        set(value) {
            val oldSize = field?.size ?: 0
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize)
            }
            field = value
        }
    private val twitchColors = intArrayOf(-65536, -16776961, -16744448, -5103070, -32944, -6632142, -47872, -13726889, -2448096, -2987746, -10510688, -14774017, -38476, -7722014, -16711809)
    private val noColor = -10066329
    private val random = Random()
    private val userColors = HashMap<String, Int>()
    private val savedColors = HashMap<String, Int>()
    private val savedLocalTwitchEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalBadges = mutableMapOf<String, ByteArray>()
    private val savedLocalCheerEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalEmotes = mutableMapOf<String, ByteArray>()
    private var localTwitchEmotes: List<TwitchEmote>? = null
    private var globalStvEmotes: List<Emote>? = null
    private var channelStvEmotes: List<Emote>? = null
    private var globalBttvEmotes: List<Emote>? = null
    private var channelBttvEmotes: List<Emote>? = null
    private var globalFfzEmotes: List<Emote>? = null
    private var channelFfzEmotes: List<Emote>? = null
    private var globalBadges: List<TwitchBadge>? = null
    private var channelBadges: List<TwitchBadge>? = null
    private var cheerEmotes: List<CheerEmote>? = null
    private var loggedInUser: String? = null
    private val scaledEmoteSize = (emoteSize * 0.78f).toInt()

    private var messageClickListener: ((CharSequence, String?, String?, String?, String?, String?) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatMessage = messages?.get(position) ?: return
        val builder = SpannableStringBuilder()
        val images = ArrayList<Image>()
        var builderIndex = 0
        var badgesCount = 0
        if (chatMessage.message.isNullOrBlank()) {
            if (chatMessage.timestamp != null && enableTimestamps) {
                val timestamp = TwitchApiHelper.getTimestamp(chatMessage.timestamp, timestampFormat)
                if (timestamp != null) {
                    builder.append("$timestamp ")
                    builder.setSpan(ForegroundColorSpan(getSavedColor("#999999")), 0, timestamp.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                    builderIndex += timestamp.length + 1
                }
            }
            if (chatMessage.systemMsg != null) {
                builder.append("${chatMessage.systemMsg}")
                builder.setSpan(ForegroundColorSpan(getSavedColor("#999999")), builderIndex, chatMessage.systemMsg.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                builderIndex += chatMessage.systemMsg.length
            } else {
                if (chatMessage.reward?.title != null) {
                    val startIndex = builderIndex
                    val string = redeemedNoMsg.format(chatMessage.userName, chatMessage.reward.title)
                    builder.append("$string ")
                    builderIndex += string.length + 1
                    builder.append("  ")
                    images.add(Image(
                        url1x = chatMessage.reward.url1x,
                        url2x = chatMessage.reward.url2x,
                        url3x = chatMessage.reward.url4x,
                        url4x = chatMessage.reward.url4x,
                        start = builderIndex++,
                        end = builderIndex++
                    ))
                    badgesCount++
                    builder.append("${chatMessage.reward.cost}")
                    builderIndex += chatMessage.reward.cost.toString().length
                    builder.setSpan(ForegroundColorSpan(getSavedColor("#999999")), startIndex, builderIndex, SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } else {
            if (chatMessage.systemMsg != null) {
                builder.append("${chatMessage.systemMsg}\n")
                builderIndex += chatMessage.systemMsg.length + 1
            } else {
                if (chatMessage.msgId != null) {
                    val msgId = TwitchApiHelper.getMessageIdString(chatMessage.msgId) ?: chatMessage.msgId
                    builder.append("$msgId\n")
                    builderIndex += msgId.length + 1
                }
            }
            if (chatMessage.isFirst && firstMsgVisibility == 0) {
                builder.append("$firstChatMsg\n")
                builderIndex += firstChatMsg.length + 1
            }
            if (chatMessage.reward?.title != null) {
                val string = redeemedChatMsg.format(chatMessage.reward.title)
                builder.append("$string ")
                builderIndex += string.length + 1
                builder.append("  ")
                images.add(Image(
                    url1x = chatMessage.reward.url1x,
                    url2x = chatMessage.reward.url2x,
                    url3x = chatMessage.reward.url4x,
                    url4x = chatMessage.reward.url4x,
                    start = builderIndex++,
                    end = builderIndex++
                ))
                badgesCount++
                builder.append("${chatMessage.reward.cost}\n")
                builderIndex += chatMessage.reward.cost.toString().length + 1
            } else {
                if (chatMessage.reward?.id != null && firstMsgVisibility == 0) {
                    builder.append("$rewardChatMsg\n")
                    builderIndex += rewardChatMsg.length + 1
                }
            }
            if (chatMessage.timestamp != null && enableTimestamps) {
                val timestamp = TwitchApiHelper.getTimestamp(chatMessage.timestamp, timestampFormat)
                if (timestamp != null) {
                    builder.append("$timestamp ")
                    builder.setSpan(ForegroundColorSpan(getSavedColor("#999999")), builderIndex, builderIndex + timestamp.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                    builderIndex += timestamp.length + 1
                }
            }
            chatMessage.badges?.forEach { chatBadge ->
                val badge = channelBadges?.find { it.setId == chatBadge.setId && it.version == chatBadge.version } ?: globalBadges?.find { it.setId == chatBadge.setId && it.version == chatBadge.version }
                if (badge != null) {
                    builder.append("  ")
                    images.add(Image(
                        localData = badge.localData?.let { getLocalBadgeData(badge.setId + badge.version, it) },
                        url1x = badge.url1x,
                        url2x = badge.url2x,
                        url3x = badge.url3x,
                        url4x = badge.url4x,
                        start = builderIndex++,
                        end = builderIndex++
                    ))
                    badgesCount++
                }
            }
            val color = if (chatMessage.color == null) {
                userColors[chatMessage.userName] ?: getRandomColor().let { newColor ->
                    if (useThemeAdaptedUsernameColor) {
                        adaptUsernameColor(newColor)
                    } else {
                        newColor
                    }.also { if (chatMessage.userName != null) userColors[chatMessage.userName] = it }
                }
            } else {
                getSavedColor(chatMessage.color)
            }
            val userNameEndIndex = builderIndex + (chatMessage.userName?.length ?: 0)
            if (!chatMessage.userName.isNullOrBlank()) {
                builder.append(chatMessage.userName)
                builder.setSpan(ForegroundColorSpan(color), builderIndex, builderIndex + chatMessage.userName.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(if (boldNames) Typeface.BOLD else Typeface.NORMAL), builderIndex, builderIndex + chatMessage.userName.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                builderIndex += if (!chatMessage.isAction) {
                    builder.append(": ")
                    chatMessage.userName.length + 2
                } else {
                    builder.append(" ")
                    chatMessage.userName.length + 1
                }
            }
            builder.append(chatMessage.message)
            try {
                chatMessage.emotes?.let { emotes ->
                    val copy = emotes.map {
                        val realBegin = chatMessage.message.offsetByCodePoints(0, it.begin)
                        val realEnd = if (it.begin == realBegin) {
                            it.end
                        } else {
                            it.end + realBegin - it.begin
                        }
                        localTwitchEmotes?.let { localEmotes ->
                            localEmotes.find { emote -> emote.id == it.id }?.let { emote ->
                                TwitchEmote(
                                    id = emote.id,
                                    name = emote.name,
                                    localData = emote.localData,
                                    format = emote.format,
                                    isAnimated = emote.isAnimated,
                                    begin = realBegin,
                                    end = realEnd,
                                    setId = emote.setId,
                                    ownerId = emote.ownerId
                                )
                            }
                        } ?: TwitchEmote(id = it.id, begin = realBegin, end = realEnd)
                    }
                    for (e in copy) {
                        val begin = builderIndex + e.begin
                        builder.replace(begin, builderIndex + e.end + 1, ".")
                        builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), begin, begin + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                        val length = e.end - e.begin
                        for (e1 in copy) {
                            if (e.begin < e1.begin) {
                                e1.begin -= length
                                e1.end -= length
                            }
                        }
                        e.end -= length
                    }
                    copy.forEach { emote ->
                        images.add(Image(
                            localData = emote.localData?.let { getLocalTwitchEmoteData(emote.id!!, it) },
                            url1x = emote.url1x,
                            url2x = emote.url2x,
                            url3x = emote.url3x,
                            url4x = emote.url4x,
                            format = emote.format,
                            isAnimated = emote.isAnimated,
                            isEmote = true,
                            start = builderIndex + emote.begin,
                            end = builderIndex + emote.end + 1
                        ))
                    }
                }
                val split = builder.substring(builderIndex).split(" ")
                var emotesFound = 0
                var wasMentioned = false
                for (value in split) {
                    val cheerEmote = if (chatMessage.bits != null) {
                        val bitsCount = value.takeLastWhile { it.isDigit() }
                        val bitsName = value.substringBeforeLast(bitsCount)
                        if (bitsCount.isNotEmpty()) {
                            val emote = cheerEmotes?.findLast { it.name.equals(bitsName, true) && it.minBits <= bitsCount.toInt() }
                            if (emote != null) {
                                for (j in images.lastIndex - emotesFound downTo badgesCount) {
                                    val e = images[j]
                                    if (e.start > builderIndex) {
                                        val remove = bitsName.length - 1
                                        e.start -= remove
                                        e.end -= remove
                                    }
                                }
                                builder.replace(builderIndex, builderIndex + bitsName.length, ".")
                                builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                                images.add(Image(
                                    localData = emote.localData?.let { getLocalCheerEmoteData(emote.name + emote.minBits, it) },
                                    url1x = emote.url1x,
                                    url2x = emote.url2x,
                                    url3x = emote.url3x,
                                    url4x = emote.url4x,
                                    format = emote.format,
                                    isAnimated = emote.isAnimated,
                                    isZeroWidth = false,
                                    isEmote = true,
                                    start = builderIndex,
                                    end = builderIndex + 1
                                ))
                                emotesFound++
                                builderIndex += 1
                                if (!emote.color.isNullOrBlank()) {
                                    builder.setSpan(ForegroundColorSpan(getSavedColor(emote.color)), builderIndex, builderIndex + bitsCount.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                                builderIndex += bitsCount.length + 1
                            }
                            emote
                        } else null
                    } else null
                    if (cheerEmote == null) {
                        val emote = channelStvEmotes?.find { it.name == value } ?:
                        channelBttvEmotes?.find { it.name == value } ?:
                        channelFfzEmotes?.find { it.name == value } ?:
                        globalStvEmotes?.find { it.name == value } ?:
                        globalBttvEmotes?.find { it.name == value } ?:
                        globalFfzEmotes?.find { it.name == value }
                        if (emote != null) {
                            for (j in images.lastIndex - emotesFound downTo badgesCount) {
                                val e = images[j]
                                if (e.start > builderIndex) {
                                    val remove = value.length - 1
                                    e.start -= remove
                                    e.end -= remove
                                }
                            }
                            builder.replace(builderIndex, builderIndex + value.length, ".")
                            builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                            images.add(Image(
                                localData = emote.localData?.let { getLocalEmoteData(emote.name!!, it) },
                                url1x = emote.url1x,
                                url2x = emote.url2x,
                                url3x = emote.url3x,
                                url4x = emote.url4x,
                                format = emote.format,
                                isAnimated = emote.isAnimated,
                                isZeroWidth = emote.isZeroWidth,
                                isEmote = true,
                                start = builderIndex,
                                end = builderIndex + 1
                            ))
                            emotesFound++
                            builderIndex += 2
                        } else {
                            if (Patterns.WEB_URL.matcher(value).matches()) {
                                val url = if (value.startsWith("http")) value else "https://$value"
                                builder.setSpan(URLSpan(url), builderIndex, builderIndex + value.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                            } else {
                                if (value.startsWith('@')) {
                                    builder.setSpan(StyleSpan(if (boldNames) Typeface.BOLD else Typeface.NORMAL), builderIndex, builderIndex + value.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                                loggedInUser?.let {
                                    if (!wasMentioned && value.contains(it, true) && chatMessage.userId != null && chatMessage.userLogin != it) {
                                        wasMentioned = true
                                    }
                                }
                            }
                            builderIndex += value.length + 1
                        }
                    }
                }
                if (chatMessage.isAction) {
                    builder.setSpan(ForegroundColorSpan(color), userNameEndIndex, builder.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                when {
                    chatMessage.isFirst && firstMsgVisibility < 2 -> holder.textView.setBackgroundResource(R.color.chatMessageFirst)
                    chatMessage.reward?.id != null && firstMsgVisibility < 2 -> holder.textView.setBackgroundResource(R.color.chatMessageReward)
                    chatMessage.systemMsg != null || chatMessage.msgId != null -> holder.textView.setBackgroundResource(R.color.chatMessageNotice)
                    wasMentioned -> holder.textView.setBackgroundResource(R.color.chatMessageMention)
                    else -> holder.textView.background = null
                }
            } catch (e: Exception) {

            }
        }
        holder.bind(builder, chatMessage.message, chatMessage.userId, chatMessage.userName, channelId, chatMessage.fullMsg)
        loadImages(holder, images, builder, chatMessage.message, chatMessage.userId, chatMessage.userName, channelId, chatMessage.fullMsg)
    }

    override fun getItemCount(): Int = messages?.size ?: 0

    private fun loadImages(holder: ViewHolder, images: List<Image>, builder: SpannableStringBuilder, message: String?, userId: String?, userName: String?, channelId: String?, fullMsg: String?) {
        images.forEach {
            loadGlide(holder, it, builder, message, userId, userName, channelId, fullMsg)
        }
    }

    private fun loadGlide(holder: ViewHolder, image: Image, builder: SpannableStringBuilder, message: String?, userId: String?, userName: String?, channelId: String?, fullMsg: String?) {
        Glide.with(fragment)
            .load(image.localData ?: when (emoteQuality) {
                "4" -> image.url4x ?: image.url3x ?: image.url2x ?: image.url1x
                "3" -> image.url3x ?: image.url2x ?: image.url1x
                "2" -> image.url2x ?: image.url1x
                else -> image.url1x
            })
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    val size = if (image.isEmote) {
                        calculateEmoteSize(resource)
                    } else {
                        Pair(badgeSize, badgeSize)
                    }
                    if (image.isZeroWidth && enableZeroWidth) {
                        resource.setBounds(-90, 0, size.first - 90, size.second)
                    } else {
                        resource.setBounds(0, 0, size.first, size.second)
                    }
                    if (resource is Animatable && image.isAnimated && animateGifs) {
                        resource.callback = object : Drawable.Callback {
                            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                                holder.textView.removeCallbacks(what)
                            }

                            override fun invalidateDrawable(who: Drawable) {
                                holder.textView.invalidate()
                            }

                            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                                holder.textView.postDelayed(what, `when`)
                            }
                        }
                        (resource as Animatable).start()
                    }
                    try {
                        builder.setSpan(CenteredImageSpan(resource), image.start, image.end, SPAN_EXCLUSIVE_EXCLUSIVE)
                    } catch (e: IndexOutOfBoundsException) {
                    }
                    holder.bind(builder, message, userId, userName, channelId, fullMsg)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

    private fun getLocalTwitchEmoteData(name: String, data: Pair<Long, Int>): ByteArray? {
        return savedLocalTwitchEmotes[name] ?: chatUrl?.let{ url ->
            getEmoteBytes?.let { get ->
                get(url, data)?.also {
                    if (savedLocalTwitchEmotes.size >= 100) {
                        savedLocalTwitchEmotes.remove(savedLocalTwitchEmotes.keys.first())
                    }
                    savedLocalTwitchEmotes[name] = it
                }
            }
        }
    }

    private fun getLocalBadgeData(name: String, data: Pair<Long, Int>): ByteArray? {
        return savedLocalBadges[name] ?: chatUrl?.let{ url ->
            getEmoteBytes?.let { get ->
                get(url, data)?.also {
                    if (savedLocalBadges.size >= 100) {
                        savedLocalBadges.remove(savedLocalBadges.keys.first())
                    }
                    savedLocalBadges[name] = it
                }
            }
        }
    }

    private fun getLocalCheerEmoteData(name: String, data: Pair<Long, Int>): ByteArray? {
        return savedLocalCheerEmotes[name] ?: chatUrl?.let{ url ->
            getEmoteBytes?.let { get ->
                get(url, data)?.also {
                    if (savedLocalCheerEmotes.size >= 100) {
                        savedLocalCheerEmotes.remove(savedLocalCheerEmotes.keys.first())
                    }
                    savedLocalCheerEmotes[name] = it
                }
            }
        }
    }

    private fun getLocalEmoteData(name: String, data: Pair<Long, Int>): ByteArray? {
        return savedLocalEmotes[name] ?: chatUrl?.let{ url ->
            getEmoteBytes?.let { get ->
                get(url, data)?.also {
                    if (savedLocalEmotes.size >= 100) {
                        savedLocalEmotes.remove(savedLocalEmotes.keys.first())
                    }
                    savedLocalEmotes[name] = it
                }
            }
        }
    }

    fun addLocalTwitchEmotes(list: List<TwitchEmote>?) {
        localTwitchEmotes = list
    }

    fun addGlobalStvEmotes(list: List<Emote>?) {
        globalStvEmotes = list
    }

    fun addChannelStvEmotes(list: List<Emote>?) {
        channelStvEmotes = list
    }

    fun addGlobalBttvEmotes(list: List<Emote>?) {
        globalBttvEmotes = list
    }

    fun addChannelBttvEmotes(list: List<Emote>?) {
        channelBttvEmotes = list
    }

    fun addGlobalFfzEmotes(list: List<Emote>?) {
        globalFfzEmotes = list
    }

    fun addChannelFfzEmotes(list: List<Emote>?) {
        channelFfzEmotes = list
    }

    fun addGlobalBadges(list: List<TwitchBadge>?) {
        globalBadges = list
    }

    fun addChannelBadges(list: List<TwitchBadge>?) {
        channelBadges = list
    }

    fun addCheerEmotes(list: List<CheerEmote>?) {
        cheerEmotes = list
    }

    fun setUsername(username: String?) {
        loggedInUser = username
    }

    fun setOnClickListener(listener: (CharSequence, String?, String?, String?, String?, String?) -> Unit) {
        messageClickListener = listener
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
                (it.drawable as? Animatable)?.start()
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
                (it.drawable as? Animatable)?.stop()
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        val childCount = recyclerView.childCount
        if (animateGifs) {
            for (i in 0 until childCount) {
                ((recyclerView.getChildAt(i) as TextView).text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
                    (it.drawable as? Animatable)?.stop()
                }
            }
        }
        super.onDetachedFromRecyclerView(recyclerView)
    }

    private fun getSavedColor(color: String): Int =
        savedColors[color] ?: Color.parseColor(color).let { newColor ->
            if (useThemeAdaptedUsernameColor) {
                adaptUsernameColor(newColor)
            } else {
                newColor
            }.also { savedColors[color] = it }
        }

    private fun getRandomColor(): Int =
        if (randomColor) {
            twitchColors[random.nextInt(twitchColors.size)]
        } else {
            noColor
        }

    private fun calculateEmoteSize(resource: Drawable): Pair<Int, Int> {
        val widthRatio = resource.intrinsicWidth.toFloat() / resource.intrinsicHeight.toFloat()
        val width: Int
        val height: Int
        when {
            widthRatio == 1f -> {
                width = emoteSize
                height = emoteSize
            }
            widthRatio <= 1.2f -> {
                width = (emoteSize * widthRatio).toInt()
                height = emoteSize
            }
            else -> {
                width = (scaledEmoteSize * widthRatio).toInt()
                height = scaledEmoteSize
            }
        }
        return width to height
    }

    private fun adaptUsernameColor(color: Int): Int {
        val colorArray = FloatArray(3)
        ColorUtils.colorToHSL(color, colorArray)
        if (isLightTheme) {
            val luminanceMax = 0.75f -
                    maxOf(1f - ((colorArray[0] - GREEN_HUE_DEGREES) / 100f).pow(2f), RED_HUE_DEGREES) * 0.4f
            colorArray[2] = minOf(colorArray[2], luminanceMax)
        } else {
            val distToRed = RED_HUE_DEGREES - colorArray[0]
            val distToBlue = BLUE_HUE_DEGREES - colorArray[0]
            val normDistanceToRed = distToRed - TWO_PI_DEGREES * floor((distToRed + PI_DEGREES) / TWO_PI_DEGREES)
            val normDistanceToBlue = distToBlue - TWO_PI_DEGREES * floor((distToBlue + PI_DEGREES) / TWO_PI_DEGREES)

            val luminanceMin = 0.3f +
                    maxOf((1f - (normDistanceToBlue / 40f).pow(2f)) * 0.35f, RED_HUE_DEGREES) +
                    maxOf((1f - (normDistanceToRed / 40f).pow(2f)) * 0.1f, RED_HUE_DEGREES)
            colorArray[2] = maxOf(colorArray[2], luminanceMin)
        }

        return ColorUtils.HSLToColor(colorArray)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView as TextView

        fun bind(formattedMessage: SpannableStringBuilder, message: String?, userId: String?, userName: String?, channelId: String?, fullMsg: String?) {
            textView.apply {
                text = formattedMessage
                movementMethod = LinkMovementMethod.getInstance()
                TooltipCompat.setTooltipText(this, message)
                setOnClickListener { messageClickListener?.invoke(formattedMessage, message, userId, userName, channelId, fullMsg) }
            }
        }
    }
}
