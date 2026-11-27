# Kivi

Kivi is a personal AI call assistant for Android. When you can't pick up, it answers, talks with the caller in English, Telugu, Hindi, Kannada, Tamil or Malayalam (switching when they switch), and leaves you a note about who called and why, marked **Needs you**, **Handled** or **Blocked**.

It runs on your own phone and number. It doesn't need root or call forwarding. Call audio goes through Shizuku, and the voice is Gemini Live.

## Who reaches you

| Mode | What Kivi does |
|---|---|
| **Strangers** | Always on: answers numbers that aren't in your contacts. |
| **Sleep** | Answers everyone. Runs on a timer or a nightly schedule. |
| **Busy** | Like Sleep. Runs on a timer, or whenever your calendar shows you as busy. |
| **Drive** | Like Sleep. Runs on a timer, from the quick-settings tile, or when your car's Bluetooth connects. |
| **Away** | Like Sleep, on a timer. |
| **Do Not Disturb** | While DND or Bedtime is on, Kivi answers everyone (Settings › Follow Do Not Disturb). |

The home screen's "Right now" line says in one sentence who reaches you, including what happens when a mode ends. The home-screen widget shows the same and starts Busy for an hour, or ends a mode, in one tap.

These callers always ring through:
- people you add under Settings › Always ring through;
- anyone on their **3rd call in a row**, each within 10 minutes of the last. Calls 1 and 2 go to Kivi, which tells a saved contact who says it's urgent that one more call will reach you. Callers Kivi blocked never get through;
- a second caller while Kivi is already on a call (you get a notification, and it still counts toward 3 in a row).

Saved contacts ring longer before Kivi picks up (8 s by default), so you can answer a friend yourself. Any call that rings for you shows **Send to Kivi** in its notification: one tap and Kivi answers it, even with Kivi off. Numbers you block from a note are declined without ringing, and so is a stranger Kivi blocked as spam twice; neither uses Gemini.

Tap **unmute** in the dialer to take over a call; Kivi goes quiet. During a call, the live screen lets you type what Kivi should say, listen in on the speaker, or step in. When a caller asks for you, Kivi says "I'll let you know" and your screen flashes blue (red if they say it's urgent), with a notification to take the call.

Kivi never answers into silence. If Shizuku, the key, internet or contacts access is missing, the phone just rings and you get a notification saying why. Settings › Diagnostics lists every check with a fix, and a self-check runs every morning at 8 so you hear about a problem before a caller does.

## What Kivi knows

- **About you** (Settings › You): what you tell it. Saved contacts hear what helps them; strangers hear none of it.
- **Instructions for particular people** (Settings › You): "Amma: tell her I'll call after 8".
- **Instructions for every call** (Settings › How Kivi talks): "Speak Telugu with my family".
- **Who a contact is to you**: Kivi greets saved contacts by name and reads their labels (Family, Friends, your own), nickname and star from your contacts.
- For saved contacts only: until when you're busy (your mode's timer, the sleep schedule, or your calendar; Kivi says it will remind you to call them back, and does), and the WhatsApp or SMS messages they sent you in the last 6 hours (read from notifications, kept in memory, never codes or OTPs).
- The caller's earlier calls from the last two weeks, and today's date and time.
- **FlyOS** (Settings › FlyOS, optional): your laptop's assistant gets every call note, and briefs Kivi while the phone rings.

## How it works

| Piece | File | Job |
|---|---|---|
| Rules | `calls/Screening.kt` | A pure function that decides whether a call rings or goes to Kivi. Unit-tested. |
| Call watcher | `calls/CallWatcher.kt` | A companion `InCallService`. It connects to Gemini while the phone rings and answers once everything is ready (up to 15 s more if Gemini is slow). It mutes your mic, handles a second caller, and keeps mobile data ready in case Wi-Fi fails mid-call. |
| Audio bridge | `audio/Audio.kt` | A Shizuku service running as the shell user. It records the caller (`VOICE_DOWNLINK`) and plays Kivi's voice into the call (`TELEPHONY_TX`). On every connect it re-registers Kivi with Telecom and exempts it from battery optimisation; if the bridge dies, Kivi restarts it. |
| Voice | `ai/LiveSession.kt` | Gemini Live (`gemini-3.8-live-extended-thinking`) over a WebSocket. If the line drops while the phone rings, it retries. Mid-call, the caller hears "one moment please" and Kivi reconnects for up to 20 s, resuming the same session when Gemini allows, over mobile data if Wi-Fi was the problem. At 2:30 Kivi starts wrapping up; at 3:00 the call ends. |
| Network | `ai/Net.kt` | The Gemini socket: no read timeout, a ping every 10 s, mobile data as the fallback. |
| Notes | `ai/Summary.kt` | After the call, a fast text model writes the note from the transcript, including the caller's language (which picks their voice next time) and any callback time (one tap to put it in your calendar). |
| Replies | `ai/AutoReply.kt` | The drafted reply goes by SMS automatically (never with a link, long number or `@` handle) or by WhatsApp with one tap. |
| Daily | `Daily.kt` | The 8 am self-check and the 9 pm summary. |
| Storage | `data/` | SQLite (calls, recent rings, call metrics, diagnostics log) and SharedPreferences. The Gemini key and FlyOS token are sealed with the phone's hardware keystore. |
| Health | `Health.kt`, `Metrics.kt` | The checks that must pass before Kivi may answer, and Diagnostics' "Last 7 days": answer rate, greeting speed, drops, how calls ended. |
| UI | `ui/` | Jetpack Compose with Material 3. Settings › Privacy can lock the app with your fingerprint. Design notes are in `PRODUCT.md` and `DESIGN.md`. |

## Build, test, install (Windows)

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Android\jdk-21.0.13.1+2"
& "$env:LOCALAPPDATA\Android\gradle-8.14.3\bin\gradle.bat" testDebugUnitTest installDebug   # debug build, with the test hooks
& "$env:LOCALAPPDATA\Android\gradle-8.14.3\bin\gradle.bat" assembleRelease                  # small release build
```

The release build is shrunk (about 3 MB) and signed with this PC's debug key, so it installs over a debug build and keeps your notes. To publish an update, attach the release APK to a GitHub release tagged `vX.Y`; Settings › Check for updates finds it and installs it in one tap (Android asks you to confirm).

## Testing

- **Unit tests** (`src/test`): the screening rules, replies, notes, summaries, metrics and more. `gradle testDebugUnitTest`.
- **Phone suite** (`tools/calltest.sh`): rings the phone over the debug build's fake line and attacks Kivi: hidden callers, piled-up hang-ups, 3 in a row, no internet, internet back, mid-call drops of 6 s and 40 s, a killed bridge, picking up yourself, a second caller, and a recorded Telugu caller. Each prints PASS or FAIL; it cleans up after itself.
- **Model tests** (`tools/livetest.mjs`, `tools/summarytest.mjs`): Kivi's real prompt and the note writer against hostile callers (prompt injection, the alarm trick, money scams, phishing links) and ordinary ones. Run them before changing a prompt or model: `cd tools && npm install && GEMINI_KEY=... node livetest.mjs`.
- **Line check** (`tools/linecheck.mjs`): setup speed and ping replies from Gemini.
- **Thinking speed** (`tools/thinkspeed.mjs`): how long Kivi pauses at each thinking level. Measured in November 2026, low, medium and high were all within 50 ms (about 0.45 s to greet, 0.65 s to reply), so the default stays medium.
- **Recorded callers** (`tools/make_caller.mjs`): makes a WAV of a caller's line for the fake line to play.

## FlyOS link

Kivi talks to FlyOS on your laptop over Tailscale, with the token as a bearer header:

- `POST {address}/api/kivi/call`: each call note as JSON (time, number, name, reason, category, reply, transcript, duration_ms, language, callback_at, `"trust": "tainted"`).
- `GET {address}/api/kivi/brief?number=…`: plain text about the caller, only what they may hear (it goes to Gemini), within 1.5 s; or 204.

## Things to know

- **After a restart,** start Shizuku again. Kivi reminds you, and re-registers itself once Shizuku is running.
  - On the Android 17 beta, Shizuku 13.6.0 crashed on "Start via Wireless debugging" because it holds `WRITE_SECURE_SETTINGS` and Android 17 no longer lets apps switch debugging on ([fix pending upstream](https://github.com/RikkaApps/Shizuku/pull/2673)).
  - Revoking that permission avoids the crash: `adb shell pm revoke moe.shizuku.privileged.api android.permission.WRITE_SECURE_SETTINGS`. Then turn on Wireless debugging yourself and tap Start in Shizuku.
  - From a PC, this always works: `adb shell "$(adb shell pm path moe.shizuku.privileged.api | sed 's/package://;s/base.apk//')lib/arm64/libshizuku.so"`.
- **If a call rang through,** Diagnostics → Recent activity says why.
- **Bluetooth headsets:** some answer every call by themselves a few seconds in (one here did it at 3.7 s), before Kivi's turn. Kivi logs it and tells you; turn off the headset's auto-answer, or set Settings › Pick up after to 3 s. `tools/calltest.sh` refuses to run while a headset is connected.
- **Testing calls without a second phone:** debug builds include a fake phone line (`src/debug/.../FakeCarrier.kt`, never in release). `tools/calltest.sh` drives it; if you ring it by hand, unregister the line afterwards, or the dialer offers "Fake" for outgoing calls.
- **Free tier:** Google may use call audio to improve its products; a paid key (billing on) keeps calls out of that. Notes and recordings stay on the phone. When the free daily limit runs out, calls ring for you until it resets (midnight Pacific), and the notification says so.
- **Model retirements:** when Google retires Kivi's Gemini model, Kivi lists Google's models and moves to the newest one like it (the note writer too), without a new build.
- **Callback reminders** come as a notification at the time the caller asked for.
- **Settings backup** (Settings › Notes): everything except the key and FlyOS token, as one file for a new phone.
- **Call recording** is off by default. When on, Kivi tells callers the call is recorded.
- **If callers can't hear Kivi,** set `MUTE_MIC = false` in `calls/CallWatcher.kt`.
