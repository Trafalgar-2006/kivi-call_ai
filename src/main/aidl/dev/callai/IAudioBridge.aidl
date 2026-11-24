package dev.callai;

// Runs as the shell user inside Shizuku, which is allowed to hear and speak into phone calls.
interface IAudioBridge {
    void destroy() = 16777114; // reserved: Shizuku calls this to stop the service
    ParcelFileDescriptor start() = 1; // begin a call: returns the caller's voice (16 kHz mono PCM16) and readies playback
    oneway void play(int gen, in byte[] pcm) = 2; // 24 kHz mono PCM16 into the call; stale gens are dropped
    void flush(int gen) = 3; // drop unplayed audio (caller interrupted)
    boolean isPlaying() = 4;
    void stopAll() = 5;
    String exec(String cmd) = 6;
}
