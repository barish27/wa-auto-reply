# WA Auto Reply

An Android app that puts a floating bubble over whatever is on screen when a
WhatsApp message arrives, lets you reply from there without opening WhatsApp,
and — when you turn it on — answers on your behalf using rules you define.

## How it works

Android lets an app with **notification access** read notifications and fire the
`RemoteInput` "Reply" action attached to them. WhatsApp attaches that action to
every chat notification; it is the same channel Android Auto and Wear OS use to
send replies. This app reads the notification, and sends through that action.

Consequences worth knowing:

- Nothing is scraped from WhatsApp's database and no private API is touched.
- No message leaves the phone. There is no server and no internet permission.
- If a chat notification has no reply action, the app ignores it. That is also
  how it tells real messages apart from "Checking for new messages", backup
  progress rows, call notifications and the group summary.

## Getting the APK

**No PC? Let GitHub build it.** The repo ships with
`.github/workflows/build.yml`. Create an empty GitHub repo, push this folder to
`main`, and the APK builds automatically on every push:

- **Actions** tab → latest run → *WaAutoReply-debug-apk* (a zip containing the APK), or
- **Releases** → *Latest debug build* → `app-debug.apk`, a direct one-tap
  download that installs straight from a phone browser.

Nothing to install and no signing key needed — it is a debug build.

## Build locally

Open the folder in Android Studio (Ladybug or newer) and press Run. Or:

```
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Targets: `minSdk 26`, `compileSdk 35`, Kotlin 2.0.21, AGP 8.7.3, Compose BOM
2024.12.01. No Room, no KSP, no annotation processing — it should sync and build
without extra setup.

> The project was written without an Android SDK available, so it has not been
> compiled. Expect to fix the odd import or lint complaint on first build.

## Setup on the phone

1. Install the APK.
2. **Android 13+, sideloaded:** Settings → Apps → WA Auto Reply → ⋮ →
   **Allow restricted settings**. Without this, notification access stays greyed
   out and nothing works.
3. Open the app, Setup tab, grant all three:
   - **Notification access** — to see the messages.
   - **Display over other apps** — for the bubble.
   - **Ignore battery optimisation** — so the listener is not killed. On
     Xiaomi / Oppo / Vivo / Samsung also set the app to "No restrictions" and
     lock it in the recents list, or the OEM will kill it after a few hours.
4. Make sure WhatsApp notifications are **on** and message previews are **not**
   hidden — if WhatsApp only shows "1 new message", there is nothing to read and
   often no reply action either.
5. Tap **Show a preview bubble** to confirm the overlay works.

## The bubble

- Collapsed it is a draggable circle; let go near an edge and it snaps there.
- Tap it to open the card: sender, the message, a reply box, and **Open in
  WhatsApp** to jump into the real chat.
- Back, the ✕, or a tap outside closes it. It hides itself after 90 seconds.
- If it auto-replied already, the card shows you what was sent.

## Auto-reply rules

Rules tab. The **first enabled rule that matches wins**. Each rule has a trigger
(any message / specific contact / sender name contains / message contains), a
reply, and whether it applies in groups. **Specific contact** compares the
sender's WhatsApp display name exactly (ignoring case and extra spaces), so a
reply intended for one person does not also match a similarly named chat.

Placeholders in a reply: `{name}` (first name), `{fullname}`, `{chat}`, `{time}`.

Three guards exist so the app does not embarrass you, all on the Setup tab:

| Guard | Default | Why |
|---|---|---|
| Never auto-reply in groups | on | A canned reply in a group is seen by everyone |
| Cooldown | 30 min | One auto-reply per chat per window, not one per message |
| Only while screen is off | off | Turn on so it never replies while you are using the phone |
| Active hours | off | Stops a "driving" reply going out at 2am |

Three rules ship pre-made (At site, Driving, Urgent keyword), all **disabled**.
Nothing is sent until you enable auto-reply *and* enable a rule.

## A word on using it

An auto-reply the recipient thinks is you is a small deception, and a reply that
fires at the wrong moment is worse than silence. The sample rules end with
"(auto-reply)" for that reason — worth keeping. Also note that automated
messaging sits awkwardly with WhatsApp's terms; a personal away-message is the
normal case, bulk or marketing use is not, and is a good way to get a number
banned. Sideload this; notification-access apps have a hard time on Play.

## Layout

```
app/src/main/java/com/gps/wareply/
├── App.kt                        Application; initialises Prefs
├── MainActivity.kt               Compose host + theme
├── data/
│   ├── Rule.kt                   Rule + LogEntry models (JSON)
│   └── Prefs.kt                  SharedPreferences store; Compose-observable
├── service/
│   ├── WaNotificationListener.kt NotificationListenerService entry point
│   ├── IncomingMessage.kt        Parses WhatsApp notifications (MessagingStyle)
│   ├── ReplyAction.kt            Finds + fires the RemoteInput reply action
│   └── AutoReplyEngine.kt        Rule matching, cooldown, hours, group gate
├── bubble/
│   ├── BubbleManager.kt          The overlay window: drag, expand, send
│   └── BubbleRoot.kt             Root view; Back key + outside taps
└── ui/MainScreen.kt              Setup / Rules / History tabs
```

## Likely issues

- **Bubble never appears** — overlay permission, or WhatsApp notifications are
  silent/minimised. Check the History tab: if entries appear, the listener works
  and only the overlay is at fault.
- **"Could not send"** — the reply action expired because the notification was
  dismissed. Open the chat instead.
- **Stops working after a few hours** — OEM battery killer. Step 3 above.
- **Nothing happens at all** — notification access was revoked (Android does
  this after an app update). Re-grant it, or toggle it off and on.
