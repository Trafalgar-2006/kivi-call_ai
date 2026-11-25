package dev.callai

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.callai.ai.LiveSession
import dev.callai.ai.Summary
import dev.callai.data.CallNote
import dev.callai.data.Settings
import dev.callai.data.Store
import java.util.Calendar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val CHECK = "dev.callai.DAILY_CHECK"
private const val DIGEST = "dev.callai.DAILY_DIGEST"

/** Two daily alarms: an 8 am self-check, so you hear about a broken Kivi before a caller does, and a 9 pm summary. */
object Daily {
    /** Safe to call often: re-registering replaces the same alarms. Alarms don't survive a restart, so boot calls it too. */
    fun schedule(ctx: Context) {
        alarm(ctx, CHECK, 8)
        alarm(ctx, DIGEST, 21)
    }

    private fun alarm(ctx: Context, action: String, hour: Int) {
        val pi = PendingIntent.getBroadcast(ctx, action.hashCode(), Intent(ctx, DailyReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        ctx.getSystemService(AlarmManager::class.java)
            .setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            // The check needs the network and up to a minute: hand it to a job.
            CHECK -> ctx.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(CHECK.hashCode(), ComponentName(ctx, SelfCheck::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build())
            DIGEST -> if (Settings.now.digest) digest(Store.calls.value, startOfToday())?.let { (title, text) -> Notify.digest(ctx, title, text) }
        }
    }
}

/** Everything a real call needs, tested without a call. Quiet when all is well; one notification when it isn't. */
class SelfCheck : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "self-check") {
            // Started by the alarm, Kivi may have just launched: give Shizuku and the bridge time to connect.
            val until = SystemClock.uptimeMillis() + 15_000
            while (Health.blocker(this) != null && SystemClock.uptimeMillis() < until) Thread.sleep(1000)
            val problem = Health.blocker(this) ?: geminiProblem() ?: Summary.check()
            if (problem == null) Store.log("Daily check: Kivi is ready to answer")
            else {
                Store.log("Daily check failed: $problem")
                Notify.problem(this, "Kivi can't answer calls right now", problem)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    private fun geminiProblem(): String? {
        var result: String? = "Gemini didn't answer"
        val done = CountDownLatch(1)
        LiveSession.check(this) { result = it; done.countDown() }
        done.await(15, TimeUnit.SECONDS)
        return result
    }
}

fun startOfToday(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** "4 calls today, 1 needs you" and who needs you, or null on a day without calls. */
fun digest(calls: List<CallNote>, since: Long): Pair<String, String>? {
    val today = calls.filter { it.time >= since }
    if (today.isEmpty()) return null
    val needs = today.filter { it.category == "needs_you" }
    val title = (if (today.size == 1) "1 call today" else "${today.size} calls today") + if (needs.isEmpty()) ", nothing needs you" else ", ${needs.size} need${if (needs.size == 1) "s" else ""} you"
    val text = needs.take(5).joinToString("\n") { "${it.name}: ${it.reason}" }
        .ifEmpty { "Kivi handled everything." } + if (needs.size > 5) "\nand ${needs.size - 5} more" else ""
    return title to text
}
