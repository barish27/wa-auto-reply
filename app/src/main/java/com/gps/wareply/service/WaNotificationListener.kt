package com.gps.wareply.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gps.wareply.ai.LlmEngine
import com.gps.wareply.ai.ModelStore
import com.gps.wareply.ai.PromptBuilder
import com.gps.wareply.bubble.BubbleManager
import com.gps.wareply.data.LogEntry
import com.gps.wareply.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** key+text of the last row we acted on, to swallow WhatsApp's re-posts. */
    private var lastHandled: Pair<String, String>? = null
    private var lastHandledAt = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
    }

    override fun onListenerDisconnected() {
        connected = false
        BubbleManager.hide()
        LlmEngine.release()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        LlmEngine.release()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in NotificationParser.WA_PACKAGES) return

        val action = ReplyAction.from(sbn.notification) ?: return
        val msg = NotificationParser.parse(sbn) ?: return

        // WhatsApp re-posts the same notification as a chat updates; ignore repeats.
        val sig = msg.chatKey to msg.text
        val now = System.currentTimeMillis()
        if (sig == lastHandled && now - lastHandledAt < 4_000L) return
        lastHandled = sig
        lastHandledAt = now

        val decision = AutoReplyEngine.decide(this, msg)

        // Show the bubble first so the message is on screen while the model thinks.
        if (Prefs.bubbleEnabled) {
            BubbleManager.show(
                this,
                BubbleManager.Pending(
                    chatKey = msg.chatKey,
                    sender = msg.sender,
                    groupName = msg.groupName,
                    text = msg.text,
                    action = action,
                    autoReplied = null,
                    pendingAi = decision?.useAi == true,
                    contentIntent = msg.contentIntent
                )
            )
        }

        if (decision == null) return

        // Claim the cooldown slot now, not after generation: a second message
        // arriving while the model is running must not start a second reply.
        AutoReplyEngine.markReplied(msg.chatKey)

        if (!decision.useAi) {
            deliver(action, msg, decision.canned, aiWritten = false)
            return
        }

        scope.launch {
            val written = if (ModelStore.isReady(this@WaNotificationListener)) {
                PromptBuilder.clean(
                    LlmEngine.generate(
                        this@WaNotificationListener,
                        PromptBuilder.build(msg),
                        Prefs.aiTimeoutSeconds * 1000L
                    )
                )
            } else null

            val text = when {
                written != null -> written + Prefs.aiMarker
                Prefs.aiFallbackToCanned && decision.canned.isNotBlank() -> decision.canned
                else -> null
            }

            if (text == null) {
                // Nothing sendable. Release the cooldown so the next message gets a turn.
                AutoReplyEngine.clearCooldown(msg.chatKey)
                BubbleManager.noteAiFailed(msg.chatKey, LlmEngine.lastError)
                return@launch
            }
            deliver(action, msg, text, aiWritten = written != null)
        }
    }

    private fun deliver(
        action: ReplyAction,
        msg: IncomingMessage,
        text: String,
        aiWritten: Boolean
    ) {
        val ok = action.send(this, text)
        Prefs.addLog(
            LogEntry(
                time = System.currentTimeMillis(),
                chat = msg.displayChat,
                incoming = msg.text,
                reply = text,
                auto = true,
                ok = ok,
                ai = aiWritten
            )
        )
        if (ok) BubbleManager.noteAutoReply(msg.chatKey, text) else BubbleManager.noteAiFailed(
            msg.chatKey, "WhatsApp refused the reply"
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in NotificationParser.WA_PACKAGES) {
            BubbleManager.onChatCleared(sbn.tag)
        }
    }

    companion object {
        @Volatile
        var connected = false
            private set

        /** True when the user has granted this app notification access. */
        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(
                ctx.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val me = ComponentName(ctx, WaNotificationListener::class.java)
            return flat.split(":").any {
                val c = ComponentName.unflattenFromString(it)
                c != null && c.packageName == me.packageName
            }
        }
    }
}
