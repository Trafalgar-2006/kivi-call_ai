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
| Call watcher | `calls/CallWatcher.kt` | A companion `InCallService`. It connects to Gemini while the phone rings, then answers and mutes your mic. |
| Audio bridge | `audio/Audio.kt` | A Shizuku service running as the shell user. It records the caller (`VOICE_DOWNLINK`) and plays Kivi's voice into the call (`TELEPHONY_TX`). On every connect it re-registers Kivi with Telecom as a call companion. |
| Voice | `ai/LiveSession.kt` | Gemini Live (`gemini-3.8-live`) over a WebSocket. It uses two tools, `take_note` and `hang_up`. If the connection drops mid-call, Kivi plays an apology (`ai/Fallback.kt`). |
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
- **Free tier:** Google may use call audio to improve its products. Notes stay on the phone.
- **If callers can't hear Kivi,** set `MUTE_MIC = false` in `calls/CallWatcher.kt`.
