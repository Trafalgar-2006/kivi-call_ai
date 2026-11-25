package dev.callai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import dev.callai.audio.Bridge
import dev.callai.calls.CallWatcher
import dev.callai.data.Settings

enum class Fix { OPEN_SHIZUKU, ALLOW_SHIZUKU, PERMISSIONS, ADD_KEY, NONE }

fun granted(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

/** critical = Kivi must not answer while this fails. */
data class Check(val label: String, val ok: Boolean, val problem: String, val critical: Boolean, val fix: Fix = Fix.NONE)

object Health {
    fun checks(ctx: Context): List<Check> {
        val p = Settings.now
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return listOf(
            Check("Shizuku running", Bridge.running(), "Shizuku isn't running", true, Fix.OPEN_SHIZUKU),
            Check("Shizuku access", Bridge.allowed(), "Kivi isn't allowed in Shizuku", true, Fix.ALLOW_SHIZUKU),
            Check("Call audio bridge", Bridge.connected(), "the call audio bridge isn't connected", true),
            // Telecom binding CallWatcher proves it, even before Kivi's own registration check has finished.
            Check("Allowed to answer calls", Bridge.companion || CallWatcher.active != null, "Android hasn't registered Kivi to answer calls", true),
            Check("Gemini key", p.key.isNotBlank(), "there's no Gemini key", true, Fix.ADD_KEY),
            Check("Internet", online, "there's no internet", true),
            // Set through Shizuku when the bridge connects (see Bridge.register).
            Check("Unrestricted battery", ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
                "Android can cut Kivi's internet while it's in the background", false),
            // Without contacts every caller looks like a stranger, so this one is critical too.
            Check("Contacts", granted(ctx, Manifest.permission.READ_CONTACTS), "Kivi can't read your contacts", true, Fix.PERMISSIONS),
            Check("Calendar", !p.config.busyCalendar || granted(ctx, Manifest.permission.READ_CALENDAR), "Kivi can't read your calendar", false, Fix.PERMISSIONS),
            Check("Notifications", granted(ctx, Manifest.permission.POST_NOTIFICATIONS), "Kivi can't show call notes", false, Fix.PERMISSIONS),
            Check("Text replies", !p.autoReply || granted(ctx, Manifest.permission.SEND_SMS), "Kivi can't text callers back", false, Fix.PERMISSIONS),
        )
    }

    /** The first reason Kivi must let calls ring, or null when it's safe to answer. */
    fun blocker(ctx: Context): String? = checks(ctx).firstOrNull { it.critical && !it.ok }?.problem
}
