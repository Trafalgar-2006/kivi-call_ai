package dev.callai.calls

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import dev.callai.R
import dev.callai.data.Settings
import dev.callai.ui.MainActivity
import java.util.Date

private const val BUSY = "dev.callai.widget.BUSY"
private const val END = "dev.callai.widget.END"

/** Home-screen widget: what Kivi is doing right now, plus Busy for an hour and End mode in one tap. */
class ModeWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            BUSY -> Settings.config { it.copy(busyUntil = System.currentTimeMillis() + 3_600_000L) }
            END -> Settings.config { it.copy(sleepUntil = 0, busyUntil = 0, driveUntil = 0, awayUntil = 0) }
            else -> return super.onReceive(ctx, intent)
        }
        refresh(ctx)
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ModeWidget::class.java))
            if (ids.isEmpty()) return
            val c = Settings.now.config
            val lead = activeModes(c, situation(ctx, c)).firstOrNull()
            val until = when (lead) { Mode.SLEEP -> c.sleepUntil; Mode.BUSY -> c.busyUntil; Mode.DRIVE -> c.driveUntil; Mode.AWAY -> c.awayUntil; else -> 0 }
                .takeIf { it in (System.currentTimeMillis() + 1)..<FOREVER }?.let { " until " + DateFormat.getTimeFormat(ctx).format(Date(it)) }.orEmpty()
            val text = when {
                lead != null && lead != Mode.STRANGERS -> "Kivi answers everyone: ${lead.name.lowercase().replaceFirstChar { it.uppercase() }}$until"
                c.strangers -> "Kivi answers unknown numbers"
                else -> "Every call rings for you"
            }
            fun tap(action: String) = PendingIntent.getBroadcast(ctx, action.hashCode(), Intent(ctx, ModeWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(ctx.packageName, R.layout.widget_mode).apply {
                setTextViewText(R.id.widget_state, text)
                setOnClickPendingIntent(R.id.widget_busy, tap(BUSY))
                setOnClickPendingIntent(R.id.widget_end, tap(END))
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            mgr.updateAppWidget(ids, views)
        }
    }
}
