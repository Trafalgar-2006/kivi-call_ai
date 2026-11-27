package dev.callai

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import dev.callai.data.Store

/**
 * A caller wants to talk to you: the screen wakes and blinks twice, blue, or red when it's urgent, so a glance tells
 * you. The overlay needs "display over other apps", which Kivi grants itself through Shizuku; without it, the
 * notification and the screen waking still do the job. It never takes touches, so it can't get in your way.
 */
object Flash {
    private val main = Handler(Looper.getMainLooper())

    fun show(ctx: Context, urgent: Boolean) = main.post {
        val app = ctx.applicationContext
        @Suppress("DEPRECATION") // still the only way for an app to switch the screen on for a moment
        runCatching { app.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "kivi:flash").acquire(3_000) }
        if (!Settings.canDrawOverlays(app)) return@post Store.log("Screen flash skipped: Kivi can't draw over other apps")
        val wm = app.getSystemService(WindowManager::class.java)
        val v = View(app).apply { setBackgroundColor(if (urgent) 0xFFE53935.toInt() else 0xFF1E88E5.toInt()); alpha = 0f }
        runCatching {
            wm.addView(v, WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT))
            ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 0.85f, 0f, 0.85f, 0f).setDuration(1_400).apply {
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { runCatching { wm.removeView(v) } } })
            }.start()
        }.onFailure { Store.log("Screen flash failed: ${it.message}") }
    }
}
