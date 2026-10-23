package com.gps.wareply.service

import android.content.Context
import android.os.PowerManager
import com.gps.wareply.data.MatchType
import com.gps.wareply.data.Prefs
import com.gps.wareply.data.Rule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Decides whether a message should be answered on the user's behalf, and with what.
 * Every gate here exists to stop the app embarrassing its owner: cooldowns stop
 * repeat-spam, the group switch stops auto-replies landing in front of 40 people,
 * and active hours stop a "driving" reply going out at midnight.
 */
object AutoReplyEngine {

    /** chatKey -> epoch millis of the last auto-reply we sent there. */
    private val lastReplied = mutableMapOf<String, Long>()

    /** What should be sent, and whether the model gets to write it. */
    data class Decision(val canned: String, val useAi: Boolean)

    fun decide(ctx: Context, msg: IncomingMessage): Decision? {
        if (!Prefs.autoReplyEnabled) return null
        if (msg.isGroup && Prefs.skipGroups) return null
        if (!withinActiveHours()) return null
        if (Prefs.onlyWhenScreenOff && screenIsOn(ctx)) return null

        val cooldownMs = Prefs.cooldownMinutes * 60_000L
        val last = lastReplied[msg.chatKey]
        if (last != null && System.currentTimeMillis() - last < cooldownMs) return null

        val rule = Prefs.rules().firstOrNull { it.enabled && matches(it, msg) } ?: return null

        val useAi = Prefs.aiEnabled && rule.useAi
        // A fixed-text rule with no text has nothing to send. An AI rule may have
        // an empty fallback as long as the fallback is switched off.
        if (rule.reply.isBlank() && !useAi) return null

        return Decision(canned = expand(rule.reply, msg), useAi = useAi)
    }

    fun markReplied(chatKey: String) {
        lastReplied[chatKey] = System.currentTimeMillis()
    }

    fun clearCooldown(chatKey: String) {
        lastReplied.remove(chatKey)
    }

    /** Lets the user clear all cooldowns from the UI. */
    fun resetCooldowns() = lastReplied.clear()

    private fun matches(rule: Rule, msg: IncomingMessage): Boolean {
        if (msg.isGroup && !rule.includeGroups) return false
        return when (rule.match) {
            MatchType.ANY_MESSAGE -> true
            MatchType.CONTACT_CONTAINS ->
                rule.value.isNotBlank() &&
                    (msg.sender.contains(rule.value, true) ||
                        msg.groupName?.contains(rule.value, true) == true)
            MatchType.TEXT_CONTAINS ->
                rule.value.isNotBlank() && msg.text.contains(rule.value, true)
        }
    }

    private fun expand(template: String, msg: IncomingMessage): String {
        val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        return template
            .replace("{name}", msg.sender.substringBefore(' ').ifBlank { "there" })
            .replace("{fullname}", msg.sender)
            .replace("{time}", time)
            .replace("{chat}", msg.displayChat)
    }

    private fun withinActiveHours(): Boolean {
        if (!Prefs.useActiveHours) return true
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val from = Prefs.activeFrom
        val to = Prefs.activeTo
        return if (from <= to) now in from..to else now >= from || now <= to
    }

    private fun screenIsOn(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: false
}
