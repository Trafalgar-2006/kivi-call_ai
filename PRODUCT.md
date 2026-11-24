# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin + Jetpack Compose (Material 3). The app runs on the phone itself; Shizuku provides shell-level call audio. Voice AI is Gemini Live (`gemini-3.8-live`, free tier) over a raw WebSocket. Storage uses Android's built-in SQLite and SharedPreferences, so there's no server.

## Users

One person, the phone's owner, who often can't pick up: asleep, driving, in meetings, or ignoring unknown numbers. They want calls answered politely in the caller's language and a short, reliable note of who called and why. They're technical enough to set up Shizuku once. For now, it's only their phone (the build is meant to grow into a service later).

## Product Purpose

Kivi answers calls the owner can't take, talks with the caller, and leaves a note sorted into **Needs you**, **Handled** or **Blocked**, with one tap to call back or send the drafted reply. It works when the owner trusts it to:

- never answer into silence;
- never leak anything about them to strangers;
- turn away scammers;
- make sure no urgent call is missed.

## Positioning

It runs on your own phone and your own number, with no root and no call forwarding. It speaks Telugu, Hindi, Kannada, Tamil, Malayalam and English, switching as the caller switches. Notes stay on the phone; only call audio goes to the voice model.

## Operating Context

- **When it runs:** modes decide when Kivi answers. Sleep (schedule or timer), Busy (timer or calendar), Drive (tile, timer, or the car's Bluetooth), Away (timer), Strangers (unknown numbers, always on).
- **Who still rings:** only the people the owner adds to "Always ring through", and anyone who calls 3 times in a row (each call within 10 minutes of the last). Favourites get no special pass. Callers Kivi blocked never ring through.
- **Taking over:** the owner can take over a call by tapping unmute in the dialer.
- **When it's checked:** the owner reads notes later, often from a notification, in short glances.
- **Setup:** happens once (Shizuku, permissions, key, test call). After a reboot, Shizuku has to be started again.

## Capabilities and Constraints

- Shizuku's shell access captures the caller's voice (VOICE_DOWNLINK) and plays audio into the call (TELEPHONY_TX). This is confirmed available on the owner's Pixel 8a running Android 17. Other phone makers may not have these audio routes.
- Kivi is a call-companion `InCallService`, not the default dialer. It needs the `MANAGE_ONGOING_CALLS` app-op and Telecom's companion override, both re-applied through Shizuku.
- Gemini's free tier: Google may use call audio to improve its products. Paid tier, accounts, a token server and store distribution are Phase 5 and not decided yet.
- Undecided: the app's UI language (English for now), a choice of voice per mode, and multi-user support.

## Brand Commitments

- **Name:** Kivi (Kannada for "ear").
- **Mascot:** an original baby elephant. Big ears = it listens, "never forgets" = it keeps every message. It must not resemble Chitti's cat or any other existing mascot.
- **Voice:** warm, brief, honest. Kivi says it's an AI assistant at the start of every call.

## Evidence on Hand

None yet. There are no users, testimonials or metrics, and none should be invented.

## Product Principles

1. **When unsure, let it ring.** If anything is unhealthy, the phone behaves as if Kivi weren't installed.
2. **Strangers learn nothing.** Only saved contacts hear your status.
3. **Glanceable truth.** The home screen states exactly who will reach you right now, and why.
4. **One tap from note to action.** Call back, reply, done.

## Accessibility & Inclusion

Callers and owners are multilingual (6 Indian languages plus English). Transcripts must render Indic scripts. Touch targets must be at least 48dp, and text must meet AA contrast in both dark and light themes.
