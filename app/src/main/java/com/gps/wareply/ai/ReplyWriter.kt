package com.gps.wareply.ai

import android.content.Context
import com.gps.wareply.data.ConversationStore
import com.gps.wareply.data.Prefs
import com.gps.wareply.service.IncomingMessage

/** Generates locally and returns a cleaned, sendable reply. */
object ReplyWriter {

    suspend fun write(ctx: Context, msg: IncomingMessage): String? {
        val timeout = Prefs.aiTimeoutSeconds * 1000L
        val memory = ConversationStore.memory(ctx, msg.chatKey)
        val raw = if (ModelStore.isReady(ctx))
            LlmEngine.generate(ctx, PromptBuilder.build(msg, memory), timeout)
        else null
        return PromptBuilder.clean(raw)
    }

    /** Why the last attempt produced nothing, for the UI and the bubble. */
    fun lastError(): String? = LlmEngine.lastError
}
