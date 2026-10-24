package com.gps.wareply.data

import org.json.JSONObject
import java.util.UUID

/**
 * One "when someone says X, I reply Y" pair.
 *
 * These are shown to the model as examples of how the owner actually writes.
 * It is not training — the weights never change — but a handful of real
 * examples moves the tone much closer than any instruction does, and it works
 * identically on the cloud and on-device paths.
 */
data class StyleExample(
    val id: String = UUID.randomUUID().toString(),
    val incoming: String = "",
    val reply: String = "",
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("in", incoming); put("out", reply); put("on", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject) = StyleExample(
            id = o.optString("id", UUID.randomUUID().toString()),
            incoming = o.optString("in", ""),
            reply = o.optString("out", ""),
            enabled = o.optBoolean("on", true)
        )
    }
}
