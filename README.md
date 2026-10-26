# Wisp

An agent-first home screen for Android. You talk or type; Wisp does it, on your phone, in any app.

Built for a Pixel 9 Pro XL (Android 15+). Wisp replaces your launcher and your assistant, and
drives the rest of your apps the way you would, with your approval for anything that matters.

## How it works

```
                 voice / text
                      |
   Home screen (orb, feed, task tray, approval gate)
                      |
              AgentController  (all UI state, main thread)
                      |
               AgentEngine     (ReAct loop: ask Claude -> run tools -> repeat)
                      |
        +-------------+----------------+
        |             |                |
   Direct tools   Screen tools     UI tools
   (tier 1)       (tier 2)         (generative UI)
   intents,       accessibility    show_card, ask_user,
   providers      service          request_approval
```

**Three tiers, cheapest first.** The model is told to prefer them in this order.

| Tier | What | Examples | Why |
|------|------|----------|-----|
| 1 | Direct tools | `open_app`, `set_alarm`, `set_timer`, `create_calendar_event`, `find_contact`, `send_sms`, `read_notifications`, `get_device_state`, `web_fetch`, `open_url`, `open_settings` | Fast and reliable. No UI driving. |
| 2 | Screen tools | `read_screen`, `tap`, `type_text`, `scroll`, `press_key`, `screenshot`, `wait` | Works in any app, including ones with no API. Slower and more fragile. |
| 3 | Elevated | Shizuku (wireless ADB) | Planned. Background virtual display, permission grants, shell. |

**Generative UI.** The agent never sends layout. It calls `show_card` or `ask_user` with a small
spec (kind, title, rows, buttons). Wisp validates it and renders it with its own components, so a
model can't produce a broken or hostile screen. Card kinds: `info`, `list`, `metric`, `steps`,
and `choice` (from `ask_user`).

**The capsule.** When the agent works inside another app, a small pill floats at the top showing
what it's doing, with a Stop button. Taps get a brief ring so you can follow along. If it needs
your OK mid-task, the pill expands with Approve and Decline.

**Entry points.** Press Home, long-press the power button or swipe from a corner (assistant role),
or tap the launcher icon. Assistant gestures open Wisp already listening.

## Design

Quiet by default, one bold element.

- **Void** `#000000` background. True black, so the OLED switches off and everything floats.
- **Aurora** orb: Tide `#4FE3D0`, Iris `#7C86FF`, Orchid `#C77DFF`, Rose `#FF8FB8`. The only
  saturated thing on screen. It breathes when idle, swells with your voice, spins up with a
  counter-rotating arc while thinking, and pulses while speaking.
- **Deep / Haze** `#0D1220` / `#1A2238` for cards and controls. **Bone** `#EDEFF5` and
  **Fog** `#8A93A8` for text.
- **Ember** `#F2B65C` is reserved for moments that need a human decision. If you see it, Wisp is
  waiting for you.
- The agent's replies are set as large, light speech rather than chat bubbles. Your own words are a
  small pill on the right.
- Apps drawer is text-only with an alphabet rail for one-handed use. If what you type matches no
  app, the last row asks Wisp instead.

## Safety

- Anything that sends, pays, posts, deletes or can't be undone goes through an approval gate. The
  agent can't skip it: `send_sms` asks on its own, and the system prompt requires
  `request_approval` before the same kind of action inside other apps.
- Text on screen, in notifications, in web pages and in tool results is treated as data, not
  instructions.
- Wisp never types passwords, card numbers or one-time codes. When a screen needs a fingerprint,
  passkey or CAPTCHA, it hands off to you.
- Screen control only acts while a task you started is running. Secure screens (banking apps and
  anything marked secure) block screenshots and often block reading; Wisp says so instead of
  guessing.
- Your API key is stored in Keystore-backed encrypted storage and only ever sent to
  `api.anthropic.com`.

## Install and first run

Wisp updates itself, so you only ever install it once.

1. Push this repo to **`Jondude1/Wisp`** and keep it **public** (the updater reads the latest
   release without a login).
2. GitHub Actions builds on every push to `main`, signs with the permanent key in `keystore/`, and
   publishes a release `v1.0.<run>`. Download the first `Wisp-v1.0.<n>.apk` from Releases and install.
3. Open Wisp, then **Settings**:
   - Paste your Anthropic API key. Default model is `claude-sonnet-5-5`; change it any time.
   - **Screen control**: turn it on. Apps installed outside the Play Store need one extra step
     first: App info → menu → **Allow restricted settings**. Settings has a button for it.
   - Allow microphone, texting and contacts, notifications, and notification access.
   - **Set as home** and **Set as assistant**.
4. Every later push to `main` ships a new version; Wisp shows "A new version is ready" and installs it.

If an earlier build was signed with a different key, uninstall once and install the new APK.
That wipes the app's data.

## Project layout

```
app/src/main/java/com/jondude1/wisp/
  MainActivity.kt        home, assistant target, permission and role requests
  agent/                 AgentController, AgentEngine (ReAct loop), AnthropicClient,
                         DeviceTools, ScreenTools, UiTools, models
  services/              WispAccessibilityService, CapsuleOverlay, notification listener,
                         foreground service
  assistant/             default-assistant registration (VoiceInteractionService)
  voice/                 speech in (system recognizer) and out (TTS)
  update/                self-updater (GitHub releases)
  ui/                    theme tokens, orb, cards, panels, apps drawer, settings
```

## Roadmap

- Streaming replies so words appear as they're written
- Shizuku tier: background virtual display so the agent works while you keep using the phone
- Persistent tasks and background scheduling ("check this every morning")
- Proactive cards from calendar, notifications and time of day
- Wake word and an earbud-first mode
- Save calendar events silently (currently opens a pre-filled form)
- MCP servers as tools (Home Assistant, Nextcloud, your own)
- On-device model for private, quick commands

## Known limits

- Android restricts starting activities from the background. Wisp launches apps through its
  accessibility service, which is the most reliable path, but some launches can still be blocked.
- UI driving is slower and more fragile than tools: apps change, animations make the screen tree
  stale, and some views expose no text. The agent re-reads the screen after each action.
- Non-streaming API calls mean a reply appears all at once.
