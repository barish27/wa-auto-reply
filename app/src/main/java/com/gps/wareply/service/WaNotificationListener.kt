package com.gps.wareply.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.gps.wareply.bubble.BubbleManager
import com.gps.wareply.data.LogEntry
import com.gps.wareply.data.Prefs

class WaNotificationListener : NotificationListenerService() {

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
        super.onListenerDisconnected()
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

        val autoText = AutoReplyEngine.decide(this, msg)
        var autoSent: String? = null

        if (autoText != null) {
            val ok = action.send(this, autoText)
            if (ok) {
                AutoReplyEngine.markReplied(msg.chatKey)
                autoSent = autoText
            }
            Prefs.addLog(
                LogEntry(
                    time = now,
                    chat = msg.displayChat,
                    incoming = msg.text,
                    reply = autoText,
                    auto = true,
                    ok = ok
                )
            )
        }

        if (Prefs.bubbleEnabled) {
            BubbleManager.show(
                this,
                BubbleManager.Pending(
                    chatKey = msg.chatKey,
                    sender = msg.sender,
                    groupName = msg.groupName,
                    text = msg.text,
                    action = action,
                    autoReplied = autoSent,
                    contentIntent = msg.contentIntent
                )
            )
        }
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
