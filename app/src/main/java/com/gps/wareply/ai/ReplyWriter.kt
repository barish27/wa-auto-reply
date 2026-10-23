package com.gps.wareply.ai

import android.content.Context
import com.gps.wareply.data.AiBackend
import com.gps.wareply.data.Prefs
import com.gps.wareply.service.IncomingMessage

/** Picks whichever backend is configured and returns a cleaned, sendable reply. */
object ReplyWriter {

    suspend fun write(ctx: Context, msg: IncomingMessage): String? {
        val timeout = Prefs.aiTimeoutSeconds * 1000L
        val raw = when (Prefs.aiBackend) {
            AiBackend.CLOUD -> CloudEngine.generate(
                PromptBuilder.systemPrompt(),
                PromptBuilder.userPrompt(msg),
                timeout
            )
            AiBackend.ON_DEVICE ->
                if (ModelStore.isReady(ctx))
                    LlmEngine.generate(ctx, PromptBuilder.build(msg), timeout)
                else null
        }
        return PromptBuilder.clean(raw)
    }

    /** Why the last attempt produced nothing, for the UI and the bubble. */
    fun lastError(): String? = when (Prefs.aiBackend) {
        AiBackend.CLOUD -> CloudEngine.lastError
        AiBackend.ON_DEVICE -> LlmEngine.lastError
    }
}
