package com.gps.wareply.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/**
 * A reply channel pulled out of a WhatsApp notification.
 *
 * WhatsApp attaches a RemoteInput "Reply" action to each chat notification — the
 * same one Android Auto and Wear OS use. Firing its PendingIntent with the text
 * filled in sends the message through WhatsApp itself; nothing is spoofed and no
 * private API is touched.
 */
class ReplyAction(
    private val pendingIntent: PendingIntent,
    private val resultKeys: List<String>
) {
    fun send(ctx: Context, text: String): Boolean = try {
        val intent = Intent()
        val inputs = resultKeys.map { RemoteInput.Builder(it).build() }.toTypedArray()
        val results = Bundle().apply { resultKeys.forEach { putCharSequence(it, text) } }
        RemoteInput.addResultsToIntent(inputs, intent, results)
        RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        pendingIntent.send(ctx, 0, intent)
        true
    } catch (e: Exception) {
        false
    }

    companion object {
        /**
         * Looks in all three places WhatsApp has used over the years: the visible
         * actions, the wearable extender, and the invisible (Auto) actions.
         * Returns null when the notification carries no reply channel — which is
         * also how we tell a real chat message from "checking for new messages",
         * backup progress, call notifications and the group summary.
         */
        fun from(n: Notification): ReplyAction? {
            n.actions?.forEach { a ->
                val keys = a.remoteInputs
                    ?.filter { it.allowFreeFormInput }
                    ?.map { it.resultKey }
                    .orEmpty()
                if (keys.isNotEmpty() && a.actionIntent != null) {
                    return ReplyAction(a.actionIntent, keys)
                }
            }

            fun fromCompat(a: NotificationCompat.Action): ReplyAction? {
                val keys = a.remoteInputs
                    ?.filter { it.allowFreeFormInput }
                    ?.map { it.resultKey }
                    .orEmpty()
                return if (keys.isNotEmpty() && a.actionIntent != null)
                    ReplyAction(a.actionIntent!!, keys) else null
            }

            NotificationCompat.WearableExtender(n).actions.forEach { a ->
                fromCompat(a)?.let { return it }
            }
            for (i in 0 until NotificationCompat.getActionCount(n)) {
                NotificationCompat.getAction(n, i)?.let { a -> fromCompat(a)?.let { return it } }
            }
            NotificationCompat.getInvisibleActions(n).forEach { a ->
                fromCompat(a)?.let { return it }
            }
            return null
        }
    }
}
