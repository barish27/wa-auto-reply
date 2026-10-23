package com.gps.wareply.data

import org.json.JSONObject
import java.util.UUID

enum class MatchType(val label: String) {
    ANY_MESSAGE("Any message"),
    CONTACT_CONTAINS("Sender name contains"),
    TEXT_CONTAINS("Message contains")
}

data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New rule",
    val enabled: Boolean = true,
    val match: MatchType = MatchType.ANY_MESSAGE,
    val value: String = "",
    val reply: String = "",
    val includeGroups: Boolean = false,
    /** Let the on-device model write this one; [reply] becomes the fallback text. */
    val useAi: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("match", match.name)
        put("value", value)
        put("reply", reply)
        put("includeGroups", includeGroups)
        put("useAi", useAi)
    }

    companion object {
        fun fromJson(o: JSONObject) = Rule(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Rule"),
            enabled = o.optBoolean("enabled", true),
            match = runCatching { MatchType.valueOf(o.optString("match")) }
                .getOrDefault(MatchType.ANY_MESSAGE),
            value = o.optString("value", ""),
            reply = o.optString("reply", ""),
            includeGroups = o.optBoolean("includeGroups", false),
            useAi = o.optBoolean("useAi", false)
        )
    }
}

data class LogEntry(
    val time: Long,
    val chat: String,
    val incoming: String,
    val reply: String,
    val auto: Boolean,
    val ok: Boolean,
    /** Written by the on-device model rather than taken from the rule text. */
    val ai: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("time", time); put("chat", chat); put("incoming", incoming)
        put("reply", reply); put("auto", auto); put("ok", ok); put("ai", ai)
    }

    companion object {
        fun fromJson(o: JSONObject) = LogEntry(
            time = o.optLong("time"),
            chat = o.optString("chat"),
            incoming = o.optString("incoming"),
            reply = o.optString("reply"),
            auto = o.optBoolean("auto"),
            ok = o.optBoolean("ok", true),
            ai = o.optBoolean("ai", false)
        )
    }
}
