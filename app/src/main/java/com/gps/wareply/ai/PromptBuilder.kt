package com.gps.wareply.ai

import com.gps.wareply.data.Prefs
import com.gps.wareply.service.IncomingMessage

/**
 * Builds the prompt and cleans up what comes back.
 *
 * A 1B model will happily produce a paragraph, a greeting block, or its own
 * chat tokens, so the output is trimmed hard before anything is sent as the user.
 */
object PromptBuilder {

    /** Up to six of the owner's own replies, as imitation targets. */
    private fun styleBlock(): String {
        if (!Prefs.styleEnabled) return ""
        val examples = Prefs.styleExamples()
            .filter { it.enabled && it.incoming.isNotBlank() && it.reply.isNotBlank() }
            .take(6)
        if (examples.isEmpty()) return ""
        return buildString {
            append("\n\nHere is how the owner actually replies. Copy this tone, length and ")
            append("language mix as closely as you can:\n")
            examples.forEach { e ->
                append("\nThem: ").append(e.incoming.trim())
                append("\nOwner: ").append(e.reply.trim()).append("\n")
            }
        }
    }

    /** The standing instructions, as a chat API's system message. */
    fun systemPrompt(): String {
        val persona = Prefs.aiPersona.trim().ifBlank {
            "You are busy at work and cannot talk properly right now."
        }
        return buildString {
            append("You write one short WhatsApp reply on behalf of the phone's owner. ")
            append("The reply is sent automatically, without the owner reading it first.\n\n")
            append(persona).append("\n\n")
            append("Rules:\n")
            append("- Reply in the same language the message used, including Hinglish or Odia.\n")
            append("- At most ").append(Prefs.aiMaxWords).append(" words. One message, no paragraphs.\n")
            append("- Sound like a normal person texting. No \"Dear\", no sign-off.\n")
            append("- Never agree to a meeting, payment, deadline or any commitment. ")
            append("If a decision is asked for, say the owner will reply properly later.\n")
            append("- Never invent facts, numbers or dates. If unsure, stay vague.\n")
            append("- Output only the reply text itself.")
            append(styleBlock())
        }
    }

    /** The incoming message, as a chat API's user message. */
    fun userPrompt(msg: IncomingMessage): String = buildString {
        if (msg.isGroup && msg.groupName != null) {
            append("Group: ").append(msg.groupName).append("\n")
        }
        append("From ").append(msg.sender).append(": ").append(msg.text)
    }

    fun build(msg: IncomingMessage): String {
        val persona = Prefs.aiPersona.trim().ifBlank {
            "You are busy at work and cannot talk properly right now."
        }
        val words = Prefs.aiMaxWords

        val instructions = buildString {
            append("You are writing one short WhatsApp reply on behalf of the phone's owner.\n")
            append(persona).append("\n\n")
            append("Rules:\n")
            append("- Reply in the same language the message used.\n")
            append("- At most ").append(words).append(" words. One short message, no paragraphs.\n")
            append("- Sound like a normal person texting. No greetings like \"Dear\", no sign-off.\n")
            append("- Never agree to a meeting, a payment, a deadline or any commitment. ")
            append("If the message asks for a decision, say you will reply properly later.\n")
            append("- Never invent facts, numbers or dates.\n")
            append("- Output only the reply text itself, nothing else.")
            append(styleBlock())
            append("\n\nMessage from ").append(msg.sender).append(": \"").append(msg.text).append("\"")
        }

        return if (Prefs.aiUseChatTemplate) {
            "<start_of_turn>user\n$instructions<end_of_turn>\n<start_of_turn>model\n"
        } else {
            "$instructions\n\nReply:"
        }
    }

    /** Returns a sendable reply, or null when the output is unusable. */
    fun clean(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var t: String = raw

        // Strip chat-template scaffolding the model sometimes echoes back.
        for (tok in listOf("<start_of_turn>", "<end_of_turn>", "<eos>", "<bos>", "model\n", "user\n")) {
            t = t.replace(tok, " ")
        }
        t = t.trim()

        // Drop a leading label such as "Reply:" the model may prepend.
        t = t.removePrefix("Reply:").removePrefix("reply:").trim()

        // Keep only the first paragraph; 1B models drift after that.
        t = t.split("\n\n").first().replace("\n", " ").trim()

        // Unwrap surrounding quotes.
        if (t.length > 1 && t.first() in "\"'“" && t.last() in "\"'”") {
            t = t.substring(1, t.length - 1).trim()
        }

        t = t.replace(Regex("\\s+"), " ").trim()
        if (t.isBlank()) return null

        val words = t.split(" ")
        val cap = Prefs.aiMaxWords
        if (words.size > cap) t = words.take(cap).joinToString(" ").trimEnd(',', ';', ':') + "…"

        // A model that returns its own instructions back is not a usable reply.
        if (t.contains("WhatsApp reply on behalf", true)) return null
        if (t.length < 2) return null

        return t
    }
}
