package com.gps.wareply.bubble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.gps.wareply.R
import com.gps.wareply.data.LogEntry
import com.gps.wareply.data.Prefs
import com.gps.wareply.service.ReplyAction
import kotlin.math.abs

/**
 * The floating chat head. One overlay window, reused for every message: collapsed
 * it's a draggable circle, expanded it's a card with the message and a reply box
 * wired to the same RemoteInput channel the auto-replies use.
 */
@SuppressLint("StaticFieldLeak")
object BubbleManager {

    data class Pending(
        val chatKey: String,
        val sender: String,
        val groupName: String?,
        val text: String,
        val action: ReplyAction?,
        val autoReplied: String?,
        val contentIntent: PendingIntent?
    )

    private val main = Handler(Looper.getMainLooper())
    private const val AUTO_HIDE_MS = 90_000L

    private var appCtx: Context? = null
    private var wm: WindowManager? = null
    private var root: BubbleRoot? = null
    private var params: WindowManager.LayoutParams? = null

    private var current: Pending? = null
    private var expanded = false
    private var unread = 0
    private var collapsedX = 0
    private var collapsedY = 0

    private val hideRunnable = Runnable { if (!expanded) hide() }

    fun show(ctx: Context, p: Pending) {
        val c = ctx.applicationContext
        main.post {
            if (!Settings.canDrawOverlays(c)) return@post
            appCtx = c
            current = p
            if (root == null) {
                create(c)
                unread = 1
            } else {
                unread++
            }
            bind(p)
            main.removeCallbacks(hideRunnable)
            main.postDelayed(hideRunnable, AUTO_HIDE_MS)
            if (!expanded && Prefs.vibrateOnBubble) buzz(c)
        }
    }

    fun hide() = main.post { destroy() }

    /** When the user opens the chat in WhatsApp the notification clears — drop the bubble too. */
    fun onChatCleared(tag: String?) = main.post {
        if (tag != null && current?.chatKey == tag && !expanded) destroy()
    }

    // ---------------- window ----------------

    private fun create(ctx: Context) {
        val manager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(ctx).inflate(R.layout.bubble, null) as BubbleRoot

        val metrics = ctx.resources.displayMetrics
        collapsedX = metrics.widthPixels - (72 * metrics.density).toInt()
        collapsedY = (metrics.heightPixels * 0.28f).toInt()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = collapsedX
            y = collapsedY
        }

        wm = manager
        root = view
        params = lp
        expanded = false

        view.onBack = { collapse() }
        view.onOutsideTouch = { if (expanded) collapse() }
        view.findViewById<View>(R.id.close).setOnClickListener { destroy() }
        view.findViewById<View>(R.id.send).setOnClickListener { sendTyped() }
        view.findViewById<View>(R.id.openWa).setOnClickListener { openChat() }
        attachDrag(view.findViewById(R.id.bubbleIcon))

        runCatching { manager.addView(view, lp) }
            .onFailure { wm = null; root = null; params = null }
    }

    private fun destroy() {
        main.removeCallbacks(hideRunnable)
        val v = root
        val manager = wm
        if (v != null && manager != null) runCatching { manager.removeView(v) }
        root = null; wm = null; params = null
        current = null; expanded = false; unread = 0
    }

    private fun bind(p: Pending) {
        val v = root ?: return
        v.findViewById<TextView>(R.id.sender).text = p.sender
        v.findViewById<TextView>(R.id.chatName).apply {
            if (p.groupName != null && p.groupName != p.sender) {
                text = "in ${p.groupName}"
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }
        v.findViewById<TextView>(R.id.message).text = p.text
        v.findViewById<TextView>(R.id.autoNote).apply {
            if (p.autoReplied != null) {
                text = "Auto-replied: ${p.autoReplied}"
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }
        v.findViewById<TextView>(R.id.badge).apply {
            if (unread > 1 && !expanded) {
                text = if (unread > 9) "9+" else unread.toString()
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }
        v.findViewById<View>(R.id.send).isEnabled = p.action != null
        v.findViewById<View>(R.id.openWa).visibility =
            if (p.contentIntent != null) View.VISIBLE else View.INVISIBLE
    }

    // ---------------- expand / collapse ----------------

    private fun expand() {
        val v = root ?: return
        val manager = wm ?: return
        val lp = params ?: return
        expanded = true
        unread = 0
        main.removeCallbacks(hideRunnable)

        val metrics = v.context.resources.displayMetrics
        collapsedX = lp.x
        collapsedY = lp.y

        lp.width = (metrics.widthPixels * 0.94f).toInt()
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        lp.x = ((metrics.widthPixels - lp.width) / 2)
        lp.y = (metrics.heightPixels * 0.22f).toInt()

        v.findViewById<View>(R.id.collapsed).visibility = View.GONE
        v.findViewById<View>(R.id.expanded).visibility = View.VISIBLE
        current?.let { bind(it) }

        runCatching { manager.updateViewLayout(v, lp) }

        val input = v.findViewById<EditText>(R.id.input)
        input.setText("")
        input.requestFocus()
        main.postDelayed({
            (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }

    private fun collapse() {
        val v = root ?: return
        val manager = wm ?: return
        val lp = params ?: return
        expanded = false

        val input = v.findViewById<EditText>(R.id.input)
        (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()

        lp.width = WindowManager.LayoutParams.WRAP_CONTENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        lp.x = collapsedX
        lp.y = collapsedY

        v.findViewById<View>(R.id.expanded).visibility = View.GONE
        v.findViewById<View>(R.id.collapsed).visibility = View.VISIBLE

        runCatching { manager.updateViewLayout(v, lp) }
        main.removeCallbacks(hideRunnable)
        main.postDelayed(hideRunnable, AUTO_HIDE_MS)
    }

    // ---------------- actions ----------------

    private fun sendTyped() {
        val v = root ?: return
        val ctx = appCtx ?: return
        val p = current ?: return
        val input = v.findViewById<EditText>(R.id.input)
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val action = p.action
        if (action == null) {
            Toast.makeText(ctx, "This chat has no reply action", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = action.send(ctx, text)
        Prefs.addLog(
            LogEntry(
                time = System.currentTimeMillis(),
                chat = p.groupName ?: p.sender,
                incoming = p.text,
                reply = text,
                auto = false,
                ok = ok
            )
        )
        Toast.makeText(ctx, if (ok) "Sent" else "Could not send", Toast.LENGTH_SHORT).show()
        input.setText("")
        destroy()
    }

    private fun openChat() {
        val p = current ?: return
        runCatching { p.contentIntent?.send() }
        destroy()
    }

    // ---------------- drag ----------------

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(handle: ImageView) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val slop = 12 * handle.resources.displayMetrics.density

        handle.setOnTouchListener { _, e ->
            val lp = params ?: return@setOnTouchListener false
            val manager = wm ?: return@setOnTouchListener false
            val v = root ?: return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { manager.updateViewLayout(v, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) expand() else snapToEdge()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val v = root ?: return
        val manager = wm ?: return
        val lp = params ?: return
        val metrics = v.context.resources.displayMetrics
        val bubbleW = (54 * metrics.density).toInt()
        lp.x = if (lp.x + bubbleW / 2 < metrics.widthPixels / 2) 0
        else metrics.widthPixels - bubbleW
        lp.y = lp.y.coerceIn(0, metrics.heightPixels - bubbleW * 2)
        collapsedX = lp.x
        collapsedY = lp.y
        runCatching { manager.updateViewLayout(v, lp) }
    }

    private fun buzz(ctx: Context) {
        val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            vib.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Lets the settings screen pop a demo bubble so permissions can be verified. */
    fun showDemo(ctx: Context) = show(
        ctx,
        Pending(
            chatKey = "demo",
            sender = "Preview",
            groupName = null,
            text = "This is how an incoming WhatsApp message will look. " +
                "Drag the circle to move it, tap to open, tap outside the card to leave it.",
            action = null,
            autoReplied = null,
            contentIntent = null
        )
    )
}
