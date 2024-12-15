package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.NamePaint

interface STVEventApiListener {
    fun onPaintUpdate(paint: NamePaint)
    fun onUserUpdate(userId: String, paintId: String)
    fun onUpdatePresence(sessionId: String?, self: Boolean)
}
