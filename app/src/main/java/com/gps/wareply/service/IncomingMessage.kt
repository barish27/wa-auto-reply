package com.gps.wareply.service

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

data class IncomingMessage(
    /** Stable-ish key for the conversation (WhatsApp puts the chat JID in the tag). */
    val chatKey: String,
    /** Who wrote it. In a group this is the member, not the group. */
    val sender: String,
    /** Group name, or null for a 1:1 chat. */
    val groupName: String?,
    val text: String,
    val isGroup: Boolean,
    val postedAt: Long,
    val contentIntent: PendingIntent?
) {
    val displayChat: String get() = groupName ?: sender
}

object NotificationParser {

    val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

    fun parse(sbn: StatusBarNotification): IncomingMessage? {
        val n = sbn.notification ?: return null

        // Group summary rows, ongoing calls and progress rows are not messages.
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (sbn.isOngoing) return null

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        var body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()

        var sender = title
        var group: String? = null
        var isGroup = false

        val style = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        }.getOrNull()

        if (style != null) {
            isGroup = style.isGroupConversation
            group = style.conversationTitle?.toString()
            style.messages.lastOrNull()?.let { m ->
                body = m.text?.toString()?.trim() ?: body
                m.person?.name?.toString()?.let { sender = it }
            }
        }

        // Fallback for group rows on older builds: "Sender: message"
        if (group == null && !title.isNullOrBlank() && body?.contains(": ") == true && isGroup) {
            group = title
            val split = body!!.split(": ", limit = 2)
            if (split.size == 2) {
                sender = split[0]
                body = split[1]
            }
        }
        if (group != null && group != sender) isGroup = true

        val text = body
        if (text.isNullOrBlank()) return null
        if (sender.isNullOrBlank()) sender = group ?: "WhatsApp"

        // Skip WhatsApp's own placeholder rows.
        val lower = text.lowercase()
        if (lower.startsWith("checking for new messages") ||
            lower.startsWith("backing up") ||
            lower == "message" ||
            lower.endsWith("new messages")
        ) return null

        return IncomingMessage(
            chatKey = sbn.tag ?: group ?: sender!!,
            sender = sender!!,
            groupName = group,
            text = text,
            isGroup = isGroup,
            postedAt = sbn.postTime,
            contentIntent = n.contentIntent
        )
    }
}
