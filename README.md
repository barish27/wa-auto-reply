# WA Auto Reply — offline Android assistant

WA Auto Reply reads WhatsApp and WhatsApp Business notification messages, drafts a reply with a model stored on the phone, and lets the owner approve, edit, reject, or (only after opt-in) send it automatically.

## Privacy and sending boundaries

- The app has **no `INTERNET` permission**, no API-key fields, and no cloud inference path.
- The imported MediaPipe-compatible `.task` model, prompts, and Room conversation database remain in app-private device storage.
- It does **not** use WhatsApp's private or direct-send APIs. It only uses Android's `NotificationListenerService` and the `RemoteInput` reply action already exposed by an individual WhatsApp notification. If that action is unavailable or has expired, the app cannot send and offers **Open in WhatsApp** instead.
- WhatsApp and WhatsApp Business are both recognized: `com.whatsapp` and `com.whatsapp.w4b`.

## Features

- MediaPipe LLM Inference runs the imported model locally.
- Sender avatar/message floating chat head with draft approval, editing, discard, and manual RemoteInput send.
- Automatic sending is off by default. AI drafts require approval by default.
- Rule, group, active-hours, screen-lock, and per-chat cooldown guards are preserved.
- Room stores local per-contact message/reply history. The model receives only the recent history for that same contact as prompt context.
- Search all locally stored history, delete one contact's history, or delete all stored history.
- Offline model import, replacement/removal, model warm-up/test, prompt persona, style examples, and generation limits.

## Model import

This app deliberately cannot download a model. On a computer or phone with an internet connection, obtain a MediaPipe/LiteRT-compatible `.task` model (for example, an instruction-tuned Gemma 3 1B int4 task model), then transfer it to the device and use the **AI** tab's Import button. Model licence acceptance and device RAM requirements are the user's responsibility.

## Setup

1. Install the APK.
2. Grant notification access and display-over-other-apps access from **Setup**.
3. Import a compatible local model in **AI**, then use **Generate a reply** to verify it loads.
4. Create and enable a rule. Leave automatic sending off unless that behavior is explicitly wanted.

Android and WhatsApp can change notification layouts or omit a reply action. The app intentionally does nothing when it cannot safely find the `RemoteInput` action.

## Build

Use Android Studio with JDK 17 and Android SDK platform 35, or run:

```sh
gradle :app:assembleDebug --no-daemon
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` on a successful build. GitHub Actions builds the same target and publishes it as an Actions artifact; the workflow is the source of truth when a local Android SDK is unavailable.
