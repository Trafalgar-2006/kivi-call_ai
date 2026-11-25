package dev.callai.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The network under the Gemini socket. Wi-Fi is the default, but a phone call can starve it: Android lowers Wi-Fi
 * transmit power while the phone is held to the head, and on 25 Nov a call lost Gemini that way. So while a call is
 * on, Kivi keeps mobile data up beside Wi-Fi, and any reconnect after a failure goes over mobile data.
 */
object Net {
    // OkHttp's default 10 s read timeout killed live calls whenever Gemini was quiet for 10 s (the caller pausing).
    // No read timeout on the socket; a ping every 10 s keeps the line alive and notices a dead one within 10–20 s
    // (Gemini answers pings in about 0.1 s).
    val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var cellular: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Ask Android to bring mobile data up alongside Wi-Fi for the length of a call. No SIM data: nothing happens. */
    @Synchronized fun holdCellular(ctx: Context) {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { cellular = n }
            override fun onLost(n: Network) { if (cellular == n) cellular = null }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { ctx.getSystemService(ConnectivityManager::class.java).requestNetwork(request, cb) }
            .onSuccess { callback = cb }
    }

    @Synchronized fun releaseCellular(ctx: Context) {
        callback?.let { runCatching { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        callback = null
        cellular = null
    }

    /** The client for a (re)connect, and what it runs over: mobile data after a failure on Wi-Fi, else the default. */
    fun client(ctx: Context, afterFailure: Boolean): Pair<OkHttpClient, String?> {
        val n = cellular
        if (!afterFailure || n == null || !onWifi(ctx)) return http to null
        val dns = object : Dns { override fun lookup(hostname: String) = n.getAllByName(hostname).toList() }
        return http.newBuilder().socketFactory(n.socketFactory).dns(dns).build() to "mobile data"
    }

    private fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
