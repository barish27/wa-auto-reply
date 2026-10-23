package com.gps.wareply.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.gps.wareply.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Wraps MediaPipe's on-device LLM.
 *
 * One instance at a time, guarded by a mutex: loading the model costs a few
 * seconds and roughly a gigabyte of RAM, and two concurrent loads on a mid-range
 * phone will simply be killed. The handle is dropped after an idle period so the
 * notification listener is not sitting on that much memory between messages.
 */
object LlmEngine {

    private val mutex = Mutex()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var engine: LlmInference? = null
    @Volatile private var loadedFrom: String? = null

    /** Set when a load fails, so the UI can show why instead of silently doing nothing. */
    @Volatile var lastError: String? = null
        private set

    val isLoaded: Boolean get() = engine != null

    private val idleRelease = Runnable { release() }

    /**
     * Runs the prompt. Returns null on timeout, load failure, or empty output —
     * callers fall back to the rule's canned text rather than sending nothing.
     */
    suspend fun generate(ctx: Context, prompt: String, timeoutMs: Long): String? =
        withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.Default) {
                mutex.withLock {
                    val llm = ensureLoaded(ctx) ?: return@withLock null
                    try {
                        val raw = llm.generateResponse(prompt)
                        scheduleIdleRelease()
                        raw?.takeIf { it.isNotBlank() }
                    } catch (e: Throwable) {
                        lastError = e.message ?: e.javaClass.simpleName
                        // A failed generation usually means the handle is unusable.
                        releaseLocked()
                        null
                    }
                }
            }
        }

    /** Loads the model so the user can see it work before relying on it. */
    suspend fun warmUp(ctx: Context): String? = mutexLoad(ctx)

    private suspend fun mutexLoad(ctx: Context): String? = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (ensureLoaded(ctx) == null) lastError ?: "Could not load the model" else null
        }
    }

    private fun ensureLoaded(ctx: Context): LlmInference? {
        val path = ModelStore.file(ctx).absolutePath
        engine?.let { if (loadedFrom == path) return it else releaseLocked() }
        if (!ModelStore.isReady(ctx)) {
            lastError = "No model imported yet"
            return null
        }
        return try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(Prefs.aiMaxTokens)
                .setMaxTopK(64)
                .build()
            val created = LlmInference.createFromOptions(ctx.applicationContext, options)
            engine = created
            loadedFrom = path
            lastError = null
            created
        } catch (e: Throwable) {
            // Most often out of memory on a mid-range device, or an unsupported
            // .task build. Either way there is nothing to retry right now.
            lastError = e.message ?: e.javaClass.simpleName
            engine = null
            loadedFrom = null
            null
        }
    }

    private fun scheduleIdleRelease() {
        main.removeCallbacks(idleRelease)
        val minutes = Prefs.aiIdleUnloadMinutes
        if (minutes > 0) main.postDelayed(idleRelease, minutes * 60_000L)
    }

    fun release() {
        main.removeCallbacks(idleRelease)
        releaseLocked()
    }

    private fun releaseLocked() {
        val e = engine
        engine = null
        loadedFrom = null
        try { (e as? AutoCloseable)?.close() } catch (_: Throwable) { }
    }
}
