package com.gps.wareply.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray

/**
 * Tiny settings + rules + log store backed by SharedPreferences.
 * `version` is a Compose state int: reading it in a composable subscribes that
 * composable to every change made anywhere (UI or notification service).
 */
object Prefs {

    private const val FILE = "wareply"
    private const val MAX_LOG = 200

    private lateinit var sp: SharedPreferences

    /** Bumped on every write so Compose recomposes. */
    val version = mutableIntStateOf(0)

    private var ruleCache: List<Rule>? = null
    private var logCache: List<LogEntry>? = null

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!sp.getBoolean("seeded", false)) {
            saveRules(defaultRules())
            sp.edit().putBoolean("seeded", true).apply()
        }
    }

    private fun bump() { version.intValue = version.intValue + 1 }

    // ---------- switches ----------

    var autoReplyEnabled: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) { sp.edit().putBoolean("auto", v).apply(); bump() }

    var bubbleEnabled: Boolean
        get() = sp.getBoolean("bubble", true)
        set(v) { sp.edit().putBoolean("bubble", v).apply(); bump() }

    /** Never auto-reply inside group chats, whatever a rule says. */
    var skipGroups: Boolean
        get() = sp.getBoolean("skipGroups", true)
        set(v) { sp.edit().putBoolean("skipGroups", v).apply(); bump() }

    /** Only auto-reply while the screen is off / locked (i.e. you're genuinely away). */
    var onlyWhenScreenOff: Boolean
        get() = sp.getBoolean("screenOff", false)
        set(v) { sp.edit().putBoolean("screenOff", v).apply(); bump() }

    /** Minutes before the same chat can be auto-replied again. */
    var cooldownMinutes: Int
        get() = sp.getInt("cooldown", 30)
        set(v) { sp.edit().putInt("cooldown", v.coerceIn(0, 720)).apply(); bump() }

    var useActiveHours: Boolean
        get() = sp.getBoolean("useHours", false)
        set(v) { sp.edit().putBoolean("useHours", v).apply(); bump() }

    /** Minutes from midnight. */
    var activeFrom: Int
        get() = sp.getInt("from", 9 * 60)
        set(v) { sp.edit().putInt("from", v).apply(); bump() }

    var activeTo: Int
        get() = sp.getInt("to", 18 * 60)
        set(v) { sp.edit().putInt("to", v).apply(); bump() }

    var vibrateOnBubble: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) { sp.edit().putBoolean("vibrate", v).apply(); bump() }

    // ---------- rules ----------

    fun rules(): List<Rule> {
        ruleCache?.let { return it }
        val raw = sp.getString("rules", null) ?: return emptyList<Rule>().also { ruleCache = it }
        val out = mutableListOf<Rule>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out += Rule.fromJson(arr.getJSONObject(i))
        }
        ruleCache = out
        return out
    }

    fun saveRules(list: List<Rule>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("rules", arr.toString()).apply()
        ruleCache = list
        bump()
    }

    fun upsertRule(rule: Rule) {
        val list = rules().toMutableList()
        val i = list.indexOfFirst { it.id == rule.id }
        if (i >= 0) list[i] = rule else list += rule
        saveRules(list)
    }

    fun deleteRule(id: String) = saveRules(rules().filterNot { it.id == id })

    private fun defaultRules() = listOf(
        Rule(
            name = "At site",
            enabled = false,
            match = MatchType.ANY_MESSAGE,
            reply = "Hi {name}, I'm at site right now and can't check messages. " +
                    "I'll get back to you as soon as I'm free. (auto-reply)"
        ),
        Rule(
            name = "Driving",
            enabled = false,
            match = MatchType.ANY_MESSAGE,
            reply = "Driving at the moment — I'll reply once I stop. (auto-reply)"
        ),
        Rule(
            name = "Urgent keyword",
            enabled = false,
            match = MatchType.TEXT_CONTAINS,
            value = "urgent",
            reply = "Got it, {name}. I've seen this and will call you back shortly. (auto-reply)"
        )
    )

    // ---------- log ----------

    fun log(): List<LogEntry> {
        logCache?.let { return it }
        val raw = sp.getString("log", null) ?: return emptyList<LogEntry>().also { logCache = it }
        val out = mutableListOf<LogEntry>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) out += LogEntry.fromJson(arr.getJSONObject(i))
        }
        logCache = out
        return out
    }

    fun addLog(entry: LogEntry) {
        val list = (listOf(entry) + log()).take(MAX_LOG)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("log", arr.toString()).apply()
        logCache = list
        bump()
    }

    fun clearLog() {
        sp.edit().remove("log").apply()
        logCache = emptyList()
        bump()
    }
}
