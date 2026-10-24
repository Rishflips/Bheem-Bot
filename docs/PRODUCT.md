# Product

## Goal

One premium Android app that is a general personal assistant on local open models: it replaces a handful of fragmented productivity, finance, and health utilities with a single ambient surface, and does everyday requests without the cloud. It runs on the phone, speaks the user's language (Hindi, English, Hinglish first), and asks for as little typing as possible.

"Premium" here means: instant, polished, predictable, and trustworthy. It does not mean a long feature list.

## Who it is for

Indian Android users who juggle many small apps and chores: reminders they forget, messages they mean to send, notes and tasks scattered across apps, payment alerts they never log, bills that sneak up. They do not want to learn prompting. Design for the person who will never open a settings screen.

## Experience

### Today feed
The home screen. A short list of cards (typically 3–5), ordered by urgency and confidence.

- Each card has a title, one line of context, and a primary action pre-filled and ready.
- Swipe or tap to approve. Dismiss or snooze with one gesture. Dismissals teach ranking.
- Every executed action shows an **Undo** for a short window, and appears in the activity ledger.
- Empty state is calm: "Nothing needs you." A feed that cries wolf loses trust faster than one that stays quiet.

### The Bar
A persistent control at the bottom of the screen.

- **Hold to talk** in Hindi, English, or Hinglish. Release to send.
- **Type** for quiet places.
- **Snap** a photo or pick a screenshot (receipts, bills, posters) for extraction.
- The result is always a proposed typed action shown for confirmation, never a free-text wall.

### Capture from anywhere
Bheem should be reachable without opening it: the system share sheet, notification actions, a quick-settings tile, a home-screen widget, and a lock-screen shortcut.

### First run
1. Three plain-language permission steps, each explaining the benefit, none upfront all at once: notification access, microphone, notifications.
2. The app is useful immediately in **rules-only mode** (parsers, no language model).
3. The model downloads in the background on Wi-Fi, resumable, with visible progress. Bheem upgrades itself when it finishes.

### Languages
**Decided: v0.01 is English only.** Language support comes second: Hindi and Hinglish are added once English works end to end, and the Phase 0 Hindi and Hinglish work moves after it.

v1 UI and voice: English and Hindi, with Hinglish understood in both. More Indic languages are added through the model layer as speech models are validated (see [MODELS.md](MODELS.md)).

## v1 skills

Bheem is a general assistant first. The skills below share one kernel, one action catalog, and one gate. Money is one of them, not the centre of the product.

### Tasks and Reminders
- "Kal subah 6 baje mummy ki dawai yaad dilana" becomes a reminder, with the parsed time shown for confirmation.
- Creates reminders, tasks, alarms, and calendar events through standard Android intents.
- Extracts dates and action items from shared text or screenshots, shown as proposals.
- **Routines.** "Every weekday at 9, tell me what I spent yesterday" or "when a payment over ₹5,000 comes in, flag it" creates a routine by voice. It appears as a card with **Edit**, **Pause**, and **Undo**, and lives in one Routines screen with its run history. v1 routines are local time or local event triggers only.

### Ask
- Natural-language questions over Bheem's own data: spending, tasks, notes. Answers cite the underlying records.
- Read-only. Ask never triggers an action.

### Money
- Reads payment and bank notifications locally, extracts amount, direction, merchant, and account hint, and logs it with no typing.
- Never stores OTPs. OTP-like messages are dropped before storage.
- Categorises spend (rules first, model for ambiguous merchants), detects recurring bills, and raises a due-date reminder card.
- Splits a shared bill: drafts a WhatsApp or SMS message with a UPI payment link. The user taps send. Bheem never moves money on its own.
- Paying a bill opens the user's own UPI app with the payment pre-filled (a `upi://pay` hand-off). The UPI app's own PIN or biometric is the authorisation.

## Skill packs

A skill pack is one Markdown file with structured frontmatter and a plain-language playbook. It is how the community adds banks, merchants, and routines without touching code.

- Declares its name, version, the catalog actions it may use, notification packages it listens to, parser templates, and suggested routines.
- Cannot add action types, run code, or carry credentials or history.
- On import, the user sees what it will do. Routines arrive **paused**, and any new notification package needs an explicit yes.
- Ships in-app packs for the v1 skills, in the same format, so the format is exercised from day one.

Packs arrive after the core is stable (Phase 4). The format is designed with that in mind from the start.

## Interaction model (decided)

v1 is **Today feed only**. There are no per-skill conversation threads. The feed, the Bar, and the Routines and Ask screens cover every v1 flow. The kernel does not depend on this choice, so a conversation view can be added after v1 if beta feedback asks for it.

## After v1

- **v1.1 Health-lite:** medicine reminders, water and sleep nudges, steps and sleep summaries from Health Connect.
- **Later, only if validated:** meal photo logging (estimates are rough, so labelled as such), a local document vault, partner-based India rails (Account Aggregator, ONDC) once a regulated partner route exists.

## Non-goals (for now)

- iOS.
- Autonomous payments, or any action that moves money without the user's own UPI app confirming it.
- Reading SMS through the restricted SMS permission. Notification access, share, and screenshots are the inputs.
- Driving other apps' screens through accessibility services.
- Cloud inference, accounts, or sync. If remote features ever exist they will be opt-in, off by default, and outside the core.
- Medical or financial advice. Bheem reports what is in your data.

## Success measures

Targets are provisional and get fixed after the Phase 0 benchmark ([ROADMAP.md](ROADMAP.md)).

| Area | Measure |
| :--- | :--- |
| Accuracy | Notification parser precision and recall on the fixture set; intent accuracy on the Hinglish test set |
| Reliability | Zero uncaught crashes in the capture-to-card path across the test matrix; invalid model output never reaches an executor |
| Speed | Time from notification to card; cold start; time to first response on the reference mid-range phone |
| Efficiency | Daily battery cost and peak RAM on the reference phone |
| Trust | Every action is visible in the ledger and undoable; erase-everything works |
