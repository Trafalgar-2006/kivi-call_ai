package dev.callai.audio

import dev.callai.BuildConfig
import dev.callai.IAudioBridge
import android.content.AttributionSource
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

const val TAG = "CallAI"
private const val IN_RATE = 16000 // Gemini Live input
const val OUT_RATE = 24000         // Gemini Live output
private const val CHUNK = IN_RATE / 10 * 2 // 100 ms of 16-bit mono

private fun pcm16(rate: Int, channel: Int) = AudioFormat.Builder()
    .setSampleRate(rate).setChannelMask(channel).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()

private fun speechTrack(ctx: Context?) = AudioTrack.Builder()
    .apply { if (ctx != null) setContext(ctx) }
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
    .setAudioFormat(pcm16(OUT_RATE, AudioFormat.CHANNEL_OUT_MONO))
    .setBufferSizeInBytes(OUT_RATE) // 0.5 s
    .build()

/** Where a conversation's audio comes from and goes to: the phone call, or mic + speaker for testing. */
interface Audio {
    fun startCapture(onPcm: (ByteArray) -> Unit)
    fun play(gen: Int, pcm: ByteArray)
    fun flush(gen: Int)
    fun isPlaying(): Boolean
    fun stop()
}

/** Feeds an AudioTrack from its own thread so callers never block. flush() drops unplayed audio (barge-in). */
class Player(private val track: AudioTrack) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var gen = 0
    @Volatile private var endsAt = 0L
    @Volatile private var running = true

    init {
        track.play()
        thread(name = "player") {
            var logged = false
            while (running) {
                val pcm = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                endsAt = maxOf(endsAt, SystemClock.uptimeMillis()) + pcm.size * 1000L / (OUT_RATE * 2)
                track.write(pcm, 0, pcm.size)
                if (!logged) { logged = true; Log.i(TAG, "playing to device type ${track.routedDevice?.type}") }
            }
            track.release()
        }
    }

    fun play(g: Int, pcm: ByteArray) { if (g == gen) queue.offer(pcm) }
    fun flush(g: Int) { gen = g; queue.clear(); track.pause(); track.flush(); track.play(); endsAt = 0 }
    fun isPlaying() = queue.isNotEmpty() || SystemClock.uptimeMillis() < endsAt + 300
    fun stop() { running = false; queue.clear(); runCatching { track.pause(); track.flush() } }
}

/**
 * Shizuku user service: runs as the shell user (uid 2000), which holds CAPTURE_AUDIO_OUTPUT and
 * MODIFY_PHONE_STATE, so it can record the caller (VOICE_DOWNLINK) and play into the call (TELEPHONY_TX).
 */
class AudioBridge(context: Context) : IAudioBridge.Stub() {
    private val shell = object : ContextWrapper(context) {
        override fun getPackageName() = "com.android.shell"
        override fun getOpPackageName() = "com.android.shell"
        override fun getAttributionSource(): AttributionSource =
            AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build()
    }
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: Player? = null

    override fun destroy() { stopAll(); exitProcess(0) }

    @Synchronized override fun start(): ParcelFileDescriptor {
        stopAll()
        val track = speechTrack(shell)
        val uplink = shell.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
        Log.i(TAG, "uplink device: ${uplink?.id}")
        track.setPreferredDevice(uplink)
        player = Player(track)

        val rec = AudioRecord.Builder().setContext(shell)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)
            .setAudioFormat(pcm16(IN_RATE, AudioFormat.CHANNEL_IN_MONO))
            .setBufferSizeInBytes(IN_RATE * 2)
            .build()
        rec.startRecording()
        Log.i(TAG, "capture state=${rec.recordingState} device=${rec.routedDevice?.type}")
        recorder = rec
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread(name = "capture") {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                    val buf = ByteArray(CHUNK)
                    while (recorder === rec) {
                        val n = rec.read(buf, 0, buf.size)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }.onFailure { Log.i(TAG, "capture ended: $it") }
            rec.release()
        }
        return read
    }

    override fun play(gen: Int, pcm: ByteArray) { player?.play(gen, pcm) }
    override fun flush(gen: Int) { player?.flush(gen) }
    override fun isPlaying() = player?.isPlaying() ?: false

    @Synchronized override fun stopAll() {
        recorder?.let { recorder = null; runCatching { it.stop() } }
        player?.stop(); player = null
    }

    override fun exec(cmd: String): String =
        ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start().inputStream.bufferedReader().readText()
}

/** The real phone call, through the Shizuku bridge. The caller's voice is levelled: phone lines are often quiet. */
class CallAudio(private val bridge: IAudioBridge) : Audio {
    private val leveler = Leveler()

    override fun startCapture(onPcm: (ByteArray) -> Unit) {
        val pfd = bridge.start()
        thread(name = "downlink") {
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    val buf = ByteArray(CHUNK)
                    while (true) { val n = input.read(buf); if (n < 0) break; onPcm(leveler.apply(buf.copyOf(n))) }
                }
            }
        }
    }

    fun levels() = leveler.summary()
    override fun play(gen: Int, pcm: ByteArray) = bridge.play(gen, pcm)
    override fun flush(gen: Int) = bridge.flush(gen)
    override fun isPlaying() = bridge.isPlaying()
    override fun stop() { runCatching { bridge.stopAll() } }
}

/** Test mode: you talk to the assistant through the mic and speaker, no call needed. */
class LocalAudio(ctx: Context) : Audio {
    private val am = ctx.getSystemService(AudioManager::class.java)
    @Volatile private var rec: AudioRecord? = null
    private val player: Player

    init {
        am.mode = AudioManager.MODE_IN_COMMUNICATION // turns on echo cancellation
        am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?.let { am.setCommunicationDevice(it) }
        player = Player(speechTrack(null))
    }

    @Suppress("MissingPermission") // RECORD_AUDIO is checked before test mode starts
    override fun startCapture(onPcm: (ByteArray) -> Unit) {
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, IN_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, IN_RATE * 2)
        r.startRecording()
        rec = r
        thread(name = "mic") {
            val buf = ByteArray(CHUNK)
            while (rec === r) { val n = r.read(buf, 0, buf.size); if (n < 0) break; if (n > 0) onPcm(buf.copyOf(n)) }
            r.release()
        }
    }
    override fun play(gen: Int, pcm: ByteArray) = player.play(gen, pcm)
    override fun flush(gen: Int) = player.flush(gen)
    override fun isPlaying() = player.isPlaying()
    override fun stop() {
        rec?.let { rec = null; runCatching { it.stop() } }
        player.stop()
        am.clearCommunicationDevice()
        am.mode = AudioManager.MODE_NORMAL
    }
}

/** App-side handle to the Shizuku bridge. */
object Bridge {
    @Volatile var service: IAudioBridge? = null; private set
    /** Telecom will hand us calls: the app-op and the companion override are both in place. */
    @Volatile var companion = false; private set
    private var app: Context? = null

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        // Shizuku sends its binder to authorised apps when it starts, which also wakes Kivi after a reboot.
        Shizuku.addBinderReceivedListenerSticky { bind() }
        Shizuku.addBinderDeadListener { service = null; companion = false }
        Shizuku.addRequestPermissionResultListener { _, result -> if (result == PackageManager.PERMISSION_GRANTED) bind() }
    }

    fun running() = Shizuku.pingBinder()
    fun allowed() = running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    fun connected() = service?.asBinder()?.pingBinder() == true

    fun bind() {
        val ctx = app ?: return
        if (connected() || !allowed()) return
        val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, AudioBridge::class.java.name))
            .processNameSuffix("bridge").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
        Shizuku.bindUserService(args, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null || !binder.pingBinder()) return
                val s = IAudioBridge.Stub.asInterface(binder)
                service = s
                register(s, ctx.packageName)
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null; companion = false }
        })
    }

    // Both reset on reboot, so they're re-applied on every connect. Without them Telecom ignores companion apps.
    private fun register(s: IAudioBridge, pkg: String) = thread(name = "register") {
        // Telecom's override is a list that grows on every add, so clear our copies first and add exactly one.
        val out = runCatching {
            s.exec("appops set $pkg MANAGE_ONGOING_CALLS allow; " +
                "for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do " +
                "dumpsys telecom | grep -i DefaultCallCompanionApps | grep -q $pkg || break; telecom add-or-remove-call-companion-app $pkg 0; done; " +
                "telecom add-or-remove-call-companion-app $pkg 1; " +
                "appops get $pkg MANAGE_ONGOING_CALLS; dumpsys telecom | grep -i DefaultCallCompanionApps")
        }.getOrDefault("")
        companion = out.contains("MANAGE_ONGOING_CALLS: allow") && out.contains(pkg)
        Log.i(TAG, "companion registration ok=$companion")
    }
}
