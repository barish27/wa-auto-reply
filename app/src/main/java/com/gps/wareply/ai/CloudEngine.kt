package com.gps.wareply.ai

import com.gps.wareply.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to any OpenAI-compatible /chat/completions endpoint.
 *
 * Deliberately not tied to one model name: the API's model ids change often, so
 * the id, the base URL and the key all come from settings. That also means this
 * works unchanged against Azure, OpenRouter, or a local Ollama server.
 */
object CloudEngine {

    @Volatile var lastError: String? = null
        private set

    suspend fun generate(systemPrompt: String, userPrompt: String, timeoutMs: Long): String? =
        withContext(Dispatchers.IO) {
            val key = Prefs.cloudApiKey.trim()
            if (key.isEmpty()) {
                lastError = "No API key set"
                return@withContext null
            }

            // Some newer models reject temperature or max_completion_tokens.
            // Try the full request, then a bare one before giving up.
            val full = body(systemPrompt, userPrompt, rich = true)
            when (val first = post(full, key, timeoutMs)) {
                is Reply.Ok -> first.text
                is Reply.Fatal -> null
                is Reply.Retryable -> {
                    val bare = body(systemPrompt, userPrompt, rich = false)
                    (post(bare, key, timeoutMs) as? Reply.Ok)?.text
                }
            }
        }

    private sealed interface Reply {
        data class Ok(val text: String?) : Reply
        object Retryable : Reply
        object Fatal : Reply
    }

    private fun body(system: String, user: String, rich: Boolean): JSONObject =
        JSONObject().apply {
            put("model", Prefs.cloudModel.trim().ifBlank { "gpt-5-nano" })
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            if (rich) {
                put("max_completion_tokens", 300)
                put("temperature", 0.7)
            }
        }

    private fun post(payload: JSONObject, key: String, timeoutMs: Long): Reply {
        val base = Prefs.cloudBaseUrl.trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val url = URL("$base/chat/completions")
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = timeoutMs.toInt().coerceAtLeast(5_000)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (code in 200..299) {
                lastError = null
                Reply.Ok(parseContent(text))
            } else {
                val msg = runCatching {
                    JSONObject(text).getJSONObject("error").optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $code"
                lastError = msg
                // A 400 about an unsupported parameter is worth one bare retry.
                if (code == 400) Reply.Retryable else Reply.Fatal
            }
        } catch (e: Throwable) {
            lastError = e.message ?: e.javaClass.simpleName
            Reply.Fatal
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseContent(raw: String): String? = runCatching {
        val choices = JSONObject(raw).optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        choices.getJSONObject(0).optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
