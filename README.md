# Kivi

Kivi is a personal AI call assistant for Android. When you can't pick up, it answers, talks with the caller in English, Telugu, Hindi, Kannada, Tamil or Malayalam (switching when they switch), and leaves you a note about who called and why, marked **Needs you**, **Handled** or **Blocked**.

It runs on your own phone and number. It doesn't need root or call forwarding. Call audio goes through Shizuku, and the voice is Gemini Live on the free tier.

## Who reaches you

| Mode | What Kivi does |
|---|---|
| **Strangers** | Always on: answers numbers that aren't in your contacts. |
| **Sleep** | Answers everyone. Runs on a timer or a nightly schedule. |
| **Busy** | Like Sleep. Runs on a timer, or whenever your calendar shows you as busy. |
| **Drive** | Like Sleep. Runs on a timer, from the quick-settings tile, or when your car's Bluetooth connects. |
| **Away** | Like Sleep, on a timer. |

These callers always ring through:
- people you add under Settings › Always ring through (favourites get no special pass);
- anyone on their **3rd call in a row**, each within 10 minutes of the last. Calls 1 and 2 go to Kivi, which tells a saved contact who says it's urgent that one more call will reach you. After that ring the count starts over. Callers Kivi blocked never get through.

Tap **unmute** in the dialer to take over a call; Kivi goes quiet.

Kivi never answers into silence. If Shizuku, the key, internet or contacts access is missing, the phone just rings normally and you get a notification saying why. Settings › Diagnostics lists every check with a fix.

## How it works

| Piece | File | Job |
|---|---|---|
| Rules | `calls/Screening.kt` | A pure function that decides whether a call rings or goes to Kivi. Unit-tested. |
| Call watcher | `calls/CallWatcher.kt` | A companion `InCallService`. It connects to Gemini while the phone rings and answers once everything is ready. If Gemini is slow, it waits up to 15 s longer, then lets the call ring through. It mutes your mic. |
| Audio bridge | `audio/Audio.kt` | A Shizuku service running as the shell user. It records the caller (`VOICE_DOWNLINK`) and plays Kivi's voice into the call (`TELEPHONY_TX`). On every connect it re-registers Kivi with Telecom as a call companion and exempts Kivi from battery optimisation, since Android otherwise blocks Kivi's internet after a minute in the background. |
| Voice | `ai/LiveSession.kt` | Gemini Live (`gemini-3.8-live-extended-thinking`) over a WebSocket. The model can't use tools, so Kivi ends calls by saying "Bye bye!", and the app listens for that. If the connection drops while the phone is still ringing, it tries again. If it drops mid-call, it reconnects once, then plays an apology (`ai/Fallback.kt`). |
| Notes | `ai/Summary.kt` | After the call, a fast text model writes the note from the transcript. |
| Storage | `data/` | Android's built-in SQLite (calls, recent rings, diagnostics log) and SharedPreferences (settings). |
| Health | `Health.kt` | The checks that must pass before Kivi may answer. |
| UI | `ui/` | Jetpack Compose with Material 3. Design notes are in `PRODUCT.md` and `DESIGN.md`. |

## Build, test, install (Windows)

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-21.0.13.1+2"
& "$env:LOCALAPPDATA\Android\gradle-8.14.3\bin\gradle.bat" testDebugUnitTest installDebug
```

## Things to know

- **After a restart,** start Shizuku again. Kivi reminds you, and re-registers itself once Shizuku is running.
  - On the Android 17 beta, Shizuku 13.6.0 crashed on "Start via Wireless debugging" because it holds `WRITE_SECURE_SETTINGS` and Android 17 no longer lets apps switch debugging on ([fix pending upstream](https://github.com/RikkaApps/Shizuku/pull/2673)).
  - Revoking that permission avoids the crash: `adb shell pm revoke moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`. Then turn on Wireless debugging yourself and tap Start in Shizuku.
  - From a PC, this always works: `adb shell "$(adb shell pm path moe.shizuku.privileged.api | sed 's/package://;s/base.apk//')lib/arm64/libshizuku.so"`.
- **If a call rang through,** Diagnostics → Recent activity says why.
- **Testing calls without a second phone:** debug builds include a fake phone line (`src/debug/.../FakeCarrier.kt`, never in release). It rings the phone through the real Telecom path from a PC, and also adds worst-case notes for checking the screens. The commands are at the top of that file. Unregister the line afterwards, or the dialer offers "Fake" for outgoing calls.
- **Auto-reply texts** never contain links, long numbers or `@` handles, whatever a caller says (`ai/AutoReply.kt`).
- **Free tier:** Google may use call audio to improve its products. Notes stay on the phone.
- **If callers can't hear Kivi,** set `MUTE_MIC = false` in `calls/CallWatcher.kt`.
