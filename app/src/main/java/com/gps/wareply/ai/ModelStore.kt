package com.gps.wareply.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds the imported Gemma model.
 *
 * The litert-community Gemma repos on Hugging Face are gated behind the Gemma
 * licence, so the app cannot fetch the file itself. The user accepts the licence
 * once, downloads the .task file, and imports it here. That is also why this app
 * still declares no INTERNET permission.
 */
object ModelStore {

    /** Anything smaller than this is a failed or truncated download, not a model. */
    private const val MIN_PLAUSIBLE_BYTES = 100L * 1024 * 1024

    fun file(ctx: Context): File = File(ctx.filesDir, "gemma.task")

    fun isReady(ctx: Context): Boolean = file(ctx).length() >= MIN_PLAUSIBLE_BYTES

    fun sizeBytes(ctx: Context): Long = file(ctx).length()

    /**
     * Streams the picked file into app storage. Returns the byte count, or throws.
     * [onProgress] gets bytes copied so far; total is -1 when the length is unknown.
     */
    suspend fun importFrom(
        ctx: Context,
        uri: Uri,
        onProgress: (copied: Long, total: Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        LlmEngine.release()               // never hold a handle to a file being replaced
        val target = file(ctx)
        val staging = File(ctx.filesDir, "gemma.task.part")
        staging.delete()

        val total = ctx.contentResolver.openAssetFileDescriptor(uri, "r")
            ?.use { it.length }?.takeIf { it > 0 } ?: -1L

        try {
            ctx.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open that file" }
                staging.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var copied = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        copied += n
                        if (copied - lastReport > 4L * 1024 * 1024) {
                            lastReport = copied
                            onProgress(copied, total)
                        }
                    }
                    output.flush()
                    onProgress(copied, total)
                }
            }
            if (staging.length() < MIN_PLAUSIBLE_BYTES) {
                val got = staging.length()
                staging.delete()
                throw IllegalStateException(
                    "That file is only ${got / 1024 / 1024} MB. The Gemma 3 1B int4 model " +
                        "is about 555 MB — the download was probably incomplete."
                )
            }
            target.delete()
            check(staging.renameTo(target)) { "Could not move the model into place" }
            target.length()
        } catch (e: Throwable) {
            staging.delete()
            throw e
        }
    }

    fun delete(ctx: Context) {
        LlmEngine.release()
        file(ctx).delete()
    }
}
