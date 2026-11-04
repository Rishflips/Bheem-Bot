# UI design: from a list to a friend who carries the load

Advice from Fable 5.1, 2026-11-04, after reading `AGENTS.md`, `docs/ASSISTANT-DESIGN.md`, `docs/evidence/ASSISTANT-SLICES.md`, the assistant section of `docs/ARCHITECTURE.md`, the current screens (`TodayScreen.kt`, `SettingsScreens.kt`, `MainActivity.kt`, `strings.xml`, `themes.xml`) and five screenshots of the debug build on the Moto g86. It is a direction for the implementer to build in Jetpack Compose Material 3, not a spec; each slice gets its own spec before code. Every size is a **target** unless marked **measured**. Contrast ratios in section D were **computed** from the hex values with the WCAG 2 formula on 2026-11-04.

Nothing here touches what the product promises: the model proposes, only executors act, every action waits for a tap through the gate, nothing leaves the phone. The screen's job is to make those promises felt.

## A. Diagnosis

What a person feels on the current screens, ranked by how much it costs them.

1. **The card is far from the words that made it.** In `w_plan2.png` Bheem says "Here are 2 steps" and the steps appear a scroll later under a heading "Needs you". In `chat1.png` the reminder card under "Needs you" has nothing to do with the GST question above it. The person has to join the two halves in their head. This is the single biggest reason it still feels like a reminder list with a chat pasted on top.
2. **Three sections of identical cards, and the oldest thing at the top.** "Needs you", "Upcoming reminders" and "Recent activity" are the same full-width beige card. A done note, a waiting call and a reminder due in two days look alike. The thread shows only three turns and stops; the rest of the screen is the old list. The eye has nowhere to rest and nothing tells it what matters now.
3. **"OK" says nothing about what will happen.** A call card, a WhatsApp card and a note card all say OK. The owner's trust rests on "every action waits for your tap", but the tap itself does not say what it does. "Waiting for your OK" exists only for TalkBack.
4. **Waiting feels dead.** The model takes 2 to 5 s for an answer and up to 40 s on first load (**measured**, `ASSISTANT-SLICES.md`). Today that is a disabled text field saying "Thinking..." and a loading card at the very top of the list, above the conversation, where it pushes everything down and then vanishes.
5. **The title is "Today" and the brand is nowhere.** The name Bheem appears as a 12sp label above bubbles. The screen has no face, no status, no sign that it is private. The wallpaper-derived dynamic colour (Material You) gave a salmon palette on this phone that reads as a 1990s organiser, and it will look different on every phone, so no screenshot ever shows "Bheem".
6. **Everything is the same weight.** Bubble text, card text and section headings are all around 16 to 18sp in one colour. A brief with nine lines (`v1_brief.png`) is the same bubble as "Hi! How can I help?". Citations ("From: reminder 4 Nov; reminder 4 Nov; reminder 4 Nov") are a sentence rather than a count.
7. **Chat answers are labelled but not distinguished.** "Bheem, from the model, not your records" is a good sentence, but the bubble underneath is identical to a grounded answer. The wrong GST answer in `chat1.png` looks exactly as certain as the reminder list in `speak2.png`.
8. **The bottom bar is a plain form.** A rectangular outlined field and a Talk pill. It works, and hold-to-talk is right, but it looks like a search box.
9. **Settings is a list without groups** (`speak_settings.png`): the model status, Speak replies, history, diagnostics, licences and Erase sit in one column with equal dividers. Erase everything is one tap from the top of the page.

What is right and stays: hold Talk to speak, Send replacing Talk when there is text, the Stop button at the top of a streaming bubble, the per-bubble Read aloud, the "from the model, may be wrong" label, the two-step erase, health cards that explain the OEM battery problem, and the plain English everywhere.

## B. Principles

1. **One thread, and the card sits where it was born.** The conversation is the screen. A card appears directly under the Bheem turn that proposed it, and it stays there when it is done. Reason: the person then reads "I asked, Bheem proposed, I tapped, it happened" as one story, which is what an assistant is.
2. **The tap says what it does.** Every action button is a verb that names the effect: "Set reminder", "Open dialer", "Open WhatsApp". No bare OK. Reason: trust comes from predictability, and the one place the person decides is the button.
3. **Calm by default, warm when it needs you.** Most of the screen is quiet paper and teal. One warm colour (turmeric) is reserved for "waits for your tap". Reason: if only one thing is warm, the eye finds what needs a decision without a section heading.
4. **Honest waiting.** A wait is shown where the answer will appear, says how long it usually takes, and offers Stop. Reason: the model is slow in places and the owner said so; hiding it reads as broken, naming it reads as a phone doing real work.
5. **Grounded looks solid, the model looks tentative.** Answers from the owner's own records are filled bubbles with a citation count. Model answers are outlined bubbles with the model label. Reason: the label alone is not read; the shape is.
6. **Privacy is shown, not preached.** A single line in the header ("On this phone") and the fact that nothing ever asks for an account, a login or the network. Reason: a lecture makes people suspicious; a quiet fact repeated in the right place makes them relax.

## C. The home screen

Rename the screen from "Today" to the thread; the wordmark "Bheem" is the title. The conversation comes first and is the whole screen; cards are part of it. The three sections go.

**Order and structure, top to bottom.**

1. Header, 56dp: "Bheem" (titleLarge, 22sp, weight 600) on the left; a status line (labelMedium, onSurfaceVariant) to its right that says "On this phone" when idle, "Getting ready, about 25 s" while the model loads, "Listening" while talking; a 48dp settings icon button on the right.
2. Next strip, 40dp, only when a reminder is upcoming: alarm icon, "Next: call Arun, 5:00 PM today", a count badge "(3)" when more exist, chevron. Tap opens a bottom sheet "Upcoming" that lists every upcoming reminder (the current Upcoming section, as rows, not cards). This replaces "Upcoming reminders".
3. Health rows, 48dp each, only when needed, `surfaceContainerHigh`, icon + one line + a text action, in place of the tertiary cards. They collapse to one row when there are two or more ("2 things to fix, tap"). The model supply card (download, import) stays a card at the top of the thread because it is a real task with progress.
4. The thread, a `LazyColumn` with oldest at the top and newest at the bottom, every turn ever stored (the `turns` table already keeps them), scrolled to the newest turn on open and on every new turn. Day dividers "Today", "Yesterday", "Mon 2 Nov". Older turns load as the person scrolls up, 50 at a time.
5. A status strip above the input, 32dp, only while the model loads or a hand-off is open ("Getting ready, about 20 s" with a determinate bar).
6. The input bar, 72dp including padding: a pill text field and the Talk button.

**Cards next to their message.** `Turn.Plan` already carries proposal ids; a single action should too (add `proposalId` to the turn that announced it, or key the feed item by the turn that created it in `AppController`). Rendering: the Bheem bubble that announced the action gets square-ish bottom corners (6dp), and the card follows 4dp below it with the same left edge, so they read as one unit. When the card is done it collapses to one line in place (check icon, "Reminder set, Thu 5 Nov 9:00 AM", Undo) and the thread does not jump. A card that is still waiting after the person has scrolled away is reachable from a chip pinned under the header: "1 waiting", tap scrolls to it. This replaces "Needs you". "Recent activity" leaves the home screen entirely; it lives in Activity history, which already exists, and in the collapsed done cards in the thread.

**The empty and first-run state.** No "Nothing needs you". The thread opens with one Bheem turn: "Namaste. I am Bheem. I keep your reminders, notes and tasks on this phone, and I never do anything until you tap." Under it, four suggestion chips that fill the input when tapped: "Remind me tomorrow at 9 to call Arun", "Note: Amma's number is 98765 43210", "What is on tomorrow?", "My morning brief". The chips vanish after the first turn and come back only when the thread is empty. The first run adds one more Bheem turn after onboarding: "Nothing leaves this phone. There is no account and no cloud."

**Model loading and thinking.** Loading never appears as a card at the top. While the model loads, the header status and the strip above the input say "Getting ready, about N s" (N from `lastLoadSeconds`, bar fills by elapsed time, capped at 95 percent until done; indeterminate when N is unknown). The text field stays typeable; Send is disabled and the strip explains why. Thinking is a Bheem bubble in the thread where the answer will land: three dots that breathe (opacity 0.3 to 1.0, 1.2 s cycle), the word "Thinking" for TalkBack, a Stop button at the top right (keep it at the top; the bubble grows downward). After 6 s a second line appears: "Still on it. This phone's model takes a few seconds." When streaming starts the dots are replaced by text in the same bubble.

**Plans, briefs and chat answers look different.**

- A plan is one container card: a header line "Two steps. Each waits for your tap.", the steps as numbered compact cards inside, a footer with a filled "Do all" button and "Done 1 of 2" as the steps complete. Skipped steps appear as a muted line "Left out: ..." under the header.
- The brief is not a bubble. It is a day card: full width, `surfaceContainer`, headline "Your day, Wed 4 Nov" (headlineSmall), sections with a small icon each (alarm for due, warning for clashes, history for missed, flag for not followed up), rows of time plus text, and a footer "Composed 7:31 AM, from 3 reminders". Reason: the brief is the one moment Bheem speaks first; it should look like a page it prepared, not a reply.
- A chat answer is an outlined bubble (1dp `outline`, no fill) with a label row above: sparkle icon, "From the model, may be wrong". The caution line for health, money and law sits inside the bubble in `onSurfaceVariant`. Grounded answers are filled bubbles with a citation row "From 2 reminders" that expands on tap to the list with dates.
- A clarifying question is a filled Bheem bubble with a question mark icon and the input placeholder changes to "Reply with a time".
- A notice (refusal, nothing found) is a filled bubble in `surfaceContainerHigh` with an info icon, never red.

**Sizes.** Side gutter 16dp. Bheem bubbles and cards span from the left gutter to 24dp short of the right gutter (304dp on a 360dp phone). User bubbles are right-aligned, at most 280dp wide. Bubble padding 12dp horizontal, 10dp vertical. 4dp between turns of the same speaker, 12dp between speakers, 20dp around day dividers. The "Bheem" label (labelMedium) is shown once per run of Bheem turns, not on every bubble. Card inner padding 12dp; leading icon 20dp inside a 32dp tinted circle; action row 8dp gap, buttons 40dp tall with the 48dp minimum touch target Material 3 adds. Input field 56dp tall, Talk 56dp circle, 8dp between them. Chips 32dp tall.

**Wireframe, 360dp wide, light theme, a plan in progress.**

```
+--------------------------------------------------+
| Bheem               On this phone           [gear]|  56dp header
| [alarm] Next: call Arun, 5:00 PM today   (3)   > |  40dp Next strip
|                                     [1 waiting]  |  chip, only when a card is off screen
|                                                  |
|                 - - - - Today - - - -            |
|                                                  |
|                      +-------------------------+ |
|                      | what is on tomorrow     | |  user, primaryContainer
|                      +-------------------------+ |
| Bheem                                            |
| +------------------------------------+           |
| | Tomorrow, Thu 5 Nov                |           |  answer, surfaceContainer
| | 9:00 AM   call Arun                |           |
| | 12:00 PM  call Priya for lunch     |           |
| | From 2 reminders               v   |           |  citation row, expands
| +------------------------------------+           |
|                                                  |
|                  +-----------------------------+ |
|                  | remind me tomorrow at 9 to  | |
|                  | call Arun and add buy salt  | |
|                  +-----------------------------+ |
| +------------------------------------+           |
| | Two steps. Each waits for your tap.|           |  plan header
| | +--------------------------------+ |           |
| ||| [alarm] 1  Reminder            | |           |  turmeric rail = waiting
| ||| call Arun                      | |           |
| ||| Thu 5 Nov, 9:00 AM             | |           |
| ||| [ Set reminder ]    Dismiss    | |           |
| | +--------------------------------+ |           |
| | +--------------------------------+ |           |
| ||| [task] 2  Task                 | |           |
| ||| buy salt                       | |           |
| ||| [ Add task ]        Dismiss    | |           |
| | +--------------------------------+ |           |
| | [ Do all ]             0 of 2 done |           |
| +------------------------------------+           |
|                                                  |
| Getting ready, about 20 s  [=========----]       |  32dp, only while loading
| +---------------------------------+  +--------+  |
| |  Ask or tell Bheem              |  |  mic   |  |  56dp pill + 56dp Talk
| +---------------------------------+  +--------+  |
+--------------------------------------------------+
```

## D. Visual system

**Colour.** Turn dynamic colour off and ship a fixed Bheem palette; the brand must look the same on every phone and in every screenshot. Two families: deep teal ("still water": steady, dependable) for Bheem's own voice and actions, turmeric for anything that waits for the owner. Backgrounds are warm paper in light and warm charcoal in dark. All ratios below are **computed**; the lowest is 4.50:1 (outline text in light), everything used for body text is above 6.5:1 (AAA).

| Role | Light | Dark | Used for |
| :-- | :-- | :-- | :-- |
| background | #F8F4EE | #101214 | screen |
| surface | #FDFBF8 | #15171A | input bar, sheets |
| surfaceContainer | #F1ECE4 | #1F2226 | Bheem bubbles, cards, the brief |
| surfaceContainerHigh | #EAE4DA | #292D32 | notices, health rows, Next strip |
| onSurface | #1C1B19 | #E6E2DB | text |
| onSurfaceVariant | #56534D | #BCB7AE | labels, citations, hints |
| outline | #78746D | #8E8981 | chat bubble border, dividers |
| primary | #1E4D5C | #8ED0E0 | filled buttons, Talk, links, icons |
| onPrimary | #FFFFFF | #00353F | text on primary |
| primaryContainer | #CDE6EC | #0F3E4C | user bubbles, selected chips |
| onPrimaryContainer | #07303B | #CDE6EC | text in user bubbles |
| secondary | #3F5A38 | #A9C7A0 | done state text and check icon |
| secondaryContainer | #D6E3D0 | #2F4229 | collapsed done cards |
| onSecondaryContainer | #1F2A1C | #D6E3D0 | text on done cards |
| tertiary | #8A4500 | #FFB77E | waiting rail, "1 waiting" chip, L2 confirm |
| tertiaryContainer | #FFDCC2 | #6B3400 | waiting icon circle, L2 card tint |
| onTertiaryContainer | #331300 | #FFDCC2 | text on tertiaryContainer |
| error | #B3261E | #F2B8B5 | failed cards, Erase |
| errorContainer | #F9DEDC | #8C1D18 | failure line background |
| onErrorContainer | #410E0B | #F9DEDC | failure text |

Contrast (fg on bg), light: onSurface/surface 16.66, onSurface/surfaceContainer 14.64, onSurface/surfaceContainerHigh 13.61, onSurfaceVariant/surface 7.42, onSurfaceVariant/surfaceContainer 6.52, onPrimary/primary 9.25, primary/surface 8.95, primary/surfaceContainer 7.86, onPrimaryContainer/primaryContainer 10.79, tertiary/surface 6.93, tertiary/surfaceContainer 6.09, white/tertiary 7.16, onTertiaryContainer/tertiaryContainer 13.18, white/error 6.54, error/surface 6.33, onErrorContainer/errorContainer 12.77, outline/surface 4.50, secondary/surface 7.44, onSecondaryContainer/secondaryContainer 11.21. Dark: onSurface/surface 13.91, onSurface/surfaceContainer 12.37, onSurface/surfaceContainerHigh 10.73, onSurfaceVariant/surface 9.00, onSurfaceVariant/surfaceContainer 8.00, onPrimary/primary 7.75, primary/surface 10.47, primary/surfaceContainer 9.31, onPrimaryContainer/primaryContainer 8.89, tertiary/surface 10.54, tertiary/surfaceContainer 9.37, onTertiary/tertiary 8.05, onTertiaryContainer/tertiaryContainer 7.69, onError/error 7.66, error/surface 10.52, onErrorContainer/errorContainer 7.17, outline/surface 5.17, secondary/surface 9.72, onSecondaryContainer/secondaryContainer 8.16. A JVM test (`ColorContrastTest`) should assert every pair at or above 4.5 so a later tweak cannot silently break it.

**Typography.** No bundled font. Use the device's default sans-serif (`FontFamily.Default`, Roboto or the OEM face). Reasons: zero APK cost, Hindi and other scripts fall back to the system's Noto fonts for free, and the owner's phone font choice is respected. If a brand face is wanted later, Inter (OFL, about 300 KB for one variable file) is the candidate; decide after v0.01. Scale (Material 3 role: size/line height, weight, use):

- headlineSmall: 24/32, 600, the brief headline, onboarding title
- titleLarge: 22/28, 600, "Bheem" in the header, screen titles
- titleMedium: 16/24, 600, card type label, settings row title, plan header
- bodyLarge: 16/24, 400, bubble text, card detail
- bodyMedium: 14/20, 400, "what will happen" line, hints, health rows, settings detail
- labelLarge: 14/20, 600, buttons, chips
- labelMedium: 12/16, 500, the "Bheem" label, citation row, timestamps, status line
- labelSmall: 11/16, 500, step numbers only

Everything in sp. Bubbles have no maximum height; cards have no fixed height.

**Shape.** Bubbles 20dp, with the corner nearest the speaker at 6dp (bottom-left for Bheem, bottom-right for the owner); a bubble that is followed by a card has both bottom corners at 6dp. Cards 16dp; compact step cards inside a plan 12dp; chips 8dp; the input pill 28dp; the Talk button a full circle; buttons the Material 3 default pill.

**Elevation.** None. Tonal layers only (`surfaceContainer` over `background`), no shadows anywhere, in both themes. The one exception is the Upcoming bottom sheet, which uses the Material 3 default scrim.

**Iconography.** Material Symbols, rounded style, as vector drawables bundled under `res/drawable` (Apache 2.0, about 1 KB each). Do not add `material-icons-extended`; it adds several megabytes. The set: `alarm` (reminder), `task_alt` (task), `sticky_note_2` (note), `calendar_month` (calendar), `chat` (message), `call` (call), `directions` (route), `checklist` (plan), `auto_awesome` (from the model), `help` (clarify), `info` (notice), `lock` (privacy, onboarding), `mic`, `send`, `stop_circle`, `volume_up` (read aloud), `undo`, `check`, `warning`, `history`, `flag`, `settings`, `arrow_back`, `expand_more`. Icons are always next to a word, never alone, except mic, send and settings, which carry content descriptions.

**Motion.** Cheap and short. A new turn fades in and rises 8dp over 200 ms (`animateItem`). A card changing state crossfades over 200 ms. The thinking dots cycle over 1.2 s using one `infiniteTransition` on alpha. The Talk button scales to 0.96 while pressed over 100 ms and the listening level drives a `LinearProgressIndicator` as today. Scroll-to-newest animates only when the distance is under one screen; otherwise it jumps. Nothing else moves: no shared elements, no blur, no parallax. When the system animator scale is 0 (read `Settings.Global.ANIMATOR_DURATION_SCALE`, or `LocalInspectionMode`) or the font scale is above 1.3, the dots become three static dots and item animations are off.

**Card anatomy.** One composable, `ActionCard`, with a leading icon in a tinted circle, a title row (type label in titleMedium plus the step number inside a plan), the detail (bodyLarge), a "what will happen" line (bodyMedium, onSurfaceVariant), and an action row. `FeedItem` needs a `kind` field so the card does not parse its title; add it in `agent-core/feed/Feed.kt` next to `isTask`.

| Type | Icon | Detail shows | What will happen (microcopy) | Verb button | Risk |
| :-- | :-- | :-- | :-- | :-- | :-- |
| Reminder | alarm | text; day and time on a second line | "Rings on this phone at that time." | Set reminder | L0, undo |
| Task | task_alt | text | "Saved to your tasks on this phone." | Add task | L0, undo; later "Mark done" |
| Note | sticky_note_2 | text | "Saved to your notes on this phone." | Save note | L0, undo |
| Calendar | calendar_month | title; start on a second line | "Opens your calendar app with this filled in. Save it there." | Add to calendar | L1, hand-off |
| Message | chat | "To Ravi on WhatsApp" as title, the text as detail | "Opens WhatsApp with this text. Nothing is sent until you tap Send there." | Open WhatsApp / Open SMS | L1, hand-off |
| Call | call | name; number in full on a second line | "Opens the dialer with this number. It does not call." | Open dialer | L1, hand-off |
| Route | directions | destination | "Opens your maps app with this place." | Open maps | L1, hand-off |
| Plan | checklist | the steps as compact cards | "Each step waits for your tap. Do all taps them in order and stops at the first app that opens." | Do all | per step |

The waiting state is shown three ways at once: a 3dp `tertiary` rail on the card's left edge, the icon circle tinted `tertiaryContainer`, and the verb button filled in `primary`. TalkBack hears "Waits for your tap" as the state description (keep the existing merged semantics). An L2 card (payment, refused today but the anatomy must hold) uses `tertiaryContainer` as its whole background and the verb "Confirm and open UPI app". Done: the card collapses to a single 48dp row in `secondaryContainer`, check icon, "Reminder set for Thu 5 Nov, 9:00 AM" and an Undo text button; a task row adds "Mark done". Failed: the rail turns `error`, the "what will happen" line becomes "Did not work: reason" in `onErrorContainer` on `errorContainer`, with "Try again" filled and Dismiss. Working: the rail stays, the button shows a 16dp indeterminate indicator and "Working".

## E. Other screens

Only what changes, and why.

**Onboarding.** Keep one screen and the three promises, but give each promise an icon and a short heading so it scans in three seconds: lock, "Stays on your phone"; `touch_app`, "Waits for your tap"; `mic`, "Type it or say it". The button becomes "Meet Bheem" and leads straight to the thread with the greeting turn from section C. Reason: the words are already right; the shape of the page is what makes them read as a promise rather than a terms screen.

**Settings.** Group the rows under three headers, each a `titleMedium` in `primary`: "Bheem" (Language model, Speak replies), "Your data" (Activity history, Diagnostics), "About" (Open-source licences with the version). Erase everything moves to its own group at the bottom, below a 24dp gap, in `error`, with the hint text as now. Replace the "Back" text button with an `arrow_back` icon button (content description "Back"). Speak replies keeps the radio group exactly as reviewed (one `selectableGroup`, the engine name, the warning under Always); only the engine line moves above the choices so the owner sees what will speak before choosing. Reason: Erase at the top of a flat list is a mistake waiting to happen; groups make the page readable at 200 percent font scale.

**Activity history.** Keep the list and the merged TalkBack line. Add the type icon on the left, group by day with the same day dividers as the thread, and colour the outcome word: done in `secondary`, refused or failed in `error`, waiting in `tertiary`. Reason: the history is where the owner checks "what did Bheem actually do"; it should use the same vocabulary as the thread.

**Diagnostics.** Set the report in `FontFamily.Monospace` at bodyMedium and keep Share at the top. Add one line above the report: "Nothing here has left the phone." Reason: it is the only screen with a Share button, and the line says what sharing means.

**Erase.** Keep both dialogs and the checkbox. Make the final "Erase now" a filled button in `error` with `onError` text rather than a red text button, and say in the final dialog what is counted if the count is cheap to get ("12 notes, 4 tasks, 3 reminders"). Reason: the final button should look as heavy as what it does.

**What not to change anywhere.** The gate, the coordinator and the ledger; hold-to-talk and its content description "Hold to talk" (the phone driver finds it by that string); Send replacing Talk when there is text; Stop at the top of a streaming bubble; the wording and meaning of the model label; the notification texts; the two-step erase; the health rows' messages; no logo image and no bundled font in v0.01.

## F. Microcopy

The voice: a capable friend from the next desk, short sentences, plain English as spoken in Indian offices and homes, no exclamation marks, no emojis, never "oops".

1. Greeting (first run): "Namaste. I am Bheem. I keep your reminders, notes and tasks on this phone, and I never do anything until you tap."
2. Greeting (later opens, when the thread is empty): "Nothing waiting. Tell me what to remember, or ask what is on."
3. Empty Next strip (no upcoming reminder, shown in the status line instead of the strip): "No reminders ahead."
4. Thinking: "Thinking" then, after 6 s, "Still on it. This phone's model takes a few seconds."
5. Model loading: "Getting ready, about 20 s. Your first answer waits for this."
6. Refusal (gate): "I will not do that one. Payments are not something I handle." For a tainted source: "That came from outside, so I only saved it as text."
7. Nothing found: "I found nothing about the electrician in your notes. Want to save a note now?"
8. Error (hand-off failed): "The dialer did not open. Try again, or dial 98765 43210 yourself."
9. Undo: "Reminder removed. It will not ring." and for a note: "Note removed."
10. Clarify: "What time should I set that for? Say a time like 5 pm."

Two more that matter: the model label "From the model, may be wrong" (unchanged), and the privacy line "On this phone" in the header, which never changes to anything else because it is never untrue.

## G. Accessibility and testing

**TalkBack order on the home screen**, top to bottom, which is also the layout order so no `traversalIndex` tricks are needed: 1. heading "Bheem", status line; 2. settings button; 3. Next strip as one element: "Next: call Arun, 5:00 PM today, 3 upcoming, button"; 4. the "1 waiting" chip when present; 5. health rows, each one element with its action; 6. the thread in order, oldest first, each turn one element: "You: what is on tomorrow", "Bheem: Tomorrow, Thu 5 Nov, 9 AM call Arun, 12 PM call Priya for lunch, from 2 reminders" (citations inside the same element; the expand control is a separate button after it); a card as one element "Reminder. call Arun, Thursday 5 November, 9 AM. Rings on this phone at that time. Waits for your tap." followed by its buttons "Set reminder", "Dismiss"; a chat answer starts "From the model, may be wrong:"; 7. the status strip, marked `liveRegion = Polite`; 8. the text field "Ask or tell Bheem"; 9. Talk, "Hold to talk, button", with the existing toggle action for switch access. New Bheem turns and the thinking bubble are `liveRegion = Polite` so a screen reader hears the answer without hunting for it. Do not use `reverseLayout` on the `LazyColumn`; it inverts the reading order.

**Font scale.** Everything in sp, no fixed heights on anything that holds text, `FlowRow` for every button row (already in place), the Next strip and health rows grow to two lines, the header status line hides at scale 1.5 and above (the strip above the input still shows loading), chips wrap to two rows. At 200 percent a card with "Set reminder" and "Dismiss" must still fit on 360dp with both buttons visible; if not, the buttons stack.

**What to verify on the phone and how.** The project's driver is `scripts/phone/ui.py` (uiautomator dump, tap by text) and `scripts/phone/cases.py`. For each slice:

- Light and dark: `adb shell cmd uimode night yes` and `no`, screenshot each with `adb exec-out screencap -p`.
- Font scale: `adb shell settings put system font_scale 2.0` (and back to `1.0`), then run the card cases; `ui.texts()` must still list both verb and Dismiss on a waiting card.
- Reduced motion: `adb shell settings put global animator_duration_scale 0`, send a chat message, confirm the thinking bubble shows static dots (screenshot) and the answer still arrives.
- TalkBack order: enable with `adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService` and `accessibility_enabled 1`, then `adb shell uiautomator dump` and read the node order; listen on the phone for the first slice at least.
- Targets: in the dump, every clickable node's bounds are at least 48 by 48 px times density (on the g86, 2.75x, so 132 px).
- Driver updates: `card_ready` in `cases.py` looks for "OK"; it must look for the verb set, and `banner` must read the newest Bheem element, which still works because the label stays "Bheem".

## H. Build order

Seven slices, one a day, each ending with a debug build on the owner's phone and a run of the affected cases. The first is the biggest visible win and touches no theme.

1. **The thread is the screen.** Cards render under the turn that created them; the three sections go; the full thread with day dividers and scroll-to-newest; the Next strip with the Upcoming sheet; the "1 waiting" chip. Files: split `TodayScreen.kt` into `ThreadScreen.kt`, `Cards.kt`, `NextStrip.kt`; `AppController.kt` exposes cards keyed by turn; `Thread.kt` in `agent-core` gains the proposal id on the announcing turn; `strings.xml`; `cases.py` (`card_ready`, `clear_cards`). Owner sees: "remind me tomorrow at 9 to call Arun" and the card is right under Bheem's reply, the day's next reminder is one line at the top, nothing else on the screen. Exit: `c01`, `c07` to `c12` and the plan and clarify cases pass; a waiting card scrolled away is reached from the chip; JVM test for the turn-to-card mapping.
2. **The Bheem theme.** `BheemTheme.kt` with both colour schemes from the table, the typography scale, shapes; `MainActivity` stops calling `dynamicLightColorScheme`; the vector icon set under `res/drawable`; the header with wordmark and status line. Files: new `BheemTheme.kt`, `MainActivity.kt`, `res/drawable/*.xml`, `ColorContrastTest.kt`. Owner sees: teal and paper in light, charcoal in dark, the name Bheem at the top. Exit: screenshots in both themes; `ColorContrastTest` green.
3. **Card anatomy and verbs.** `ActionCard` with icon, rail, "what will happen" line and verb buttons; collapsed done rows; failed and working states; `FeedItem.kind`. Files: `Cards.kt`, `Feed.kt` (agent-core), `strings.xml`, `cases.py`. Owner sees: a call card says "Open dialer" and "It does not call." Exit: a Compose preview of all eight types from fixtures; the call, route, message and plan cases pass with the new verbs; TalkBack reads "Waits for your tap".
4. **Honest waiting.** The loading strip with a time estimate, the thinking bubble with dots and the 6 s line, Stop kept at the top, reduced-motion switch. Files: `ThreadScreen.kt`, `AppController.kt` (expose elapsed load time), `strings.xml`. Owner sees: after a force-stop, the strip counts up toward the usual load time, and a chat question shows breathing dots where the answer appears. Exit: **measured** on the phone: strip visible within 300 ms of Send after a cold start; dots static with animator scale 0; Stop case from `ASSISTANT-SLICES.md` still cancels.
5. **Input bar, Talk and the empty state.** Pill field, 56dp circular Talk with mic icon and the hold behaviour unchanged, listening state tints the bar and shows the level, suggestion chips, the greeting turn. Files: `InputBar.kt` (from `Bar`), `ThreadScreen.kt`, `strings.xml`. Owner sees: a fresh install opens with a greeting and four chips. Exit: `hold_talk` in `cases.py` still finds "Hold to talk"; chip tap fills the field; `c15` (voice) passes.
6. **Plans, briefs and chat treatment.** Plan container with numbered steps and "Done n of m"; the brief as a day card; chat answers outlined with the sparkle label; clarify and notice styles; citation row that expands. Files: `Cards.kt`, `BriefCard.kt`, `Bubbles.kt`, `strings.xml`. Owner sees: "give me my morning brief" produces a page-like card, the GST answer is visibly tentative. Exit: brief, plan, chat and clarify cases pass; the caution line still appears for health, money and law.
7. **Other screens and the accessibility pass.** Onboarding icons and headings, Settings groups and back icon, history icons and day groups, diagnostics monospace, erase button; then the TalkBack walk, 200 percent font scale screenshots, 48dp audit from the dump. Files: `SettingsScreens.kt`, `Onboarding.kt` (split out), `strings.xml`. Exit: the four `adb` checks in section G recorded in `docs/evidence/`, with any failure fixed in the same slice.

Each slice updates `cases.py` where it changes a string the driver taps, and no slice changes the gate, the executors or `SECURITY.md`.

## I. Risks and open questions, ranked

1. **Verb buttons break the phone driver and the owner's habit.** Every case taps "OK". Recommendation: do it anyway in slice 3, update `card_ready` to a verb set in the same change, and keep "Dismiss" unchanged so the clear-cards loop still works.
2. **Cards inside the thread can be missed.** A waiting card from this morning is ten turns up. Recommendation: the pinned "n waiting" chip, plus the brief's "not followed up" section which already lists cards waiting for more than a day.
3. **Fixed palette versus Material You.** Some owners like the wallpaper colour. Recommendation: fixed palette now; a "Use phone colours" switch in Settings only if someone asks, because the brand needs one face first.
4. **Full thread history costs memory and scroll time.** Months of turns in one `LazyColumn`. Recommendation: page 50 turns at a time from the `turns` table; measure open time on the g86 at 1,000 turns before slice 1 ships.
5. **"Do all" stops at a hand-off and the owner does not notice.** The plan footer "Done 1 of 2" helps. Recommendation: when the owner returns from the dialer, the plan card scrolls into view and the next step's rail pulses once (one 400 ms alpha cycle, skipped under reduced motion).
6. **Long verbs at 200 percent on 360dp.** "Add to calendar" and "Dismiss" may not fit one row. Recommendation: `FlowRow` stacks them; verify in slice 3 and shorten to "Calendar" only if it fails.
7. **The thinking bubble promises an answer that becomes a refusal.** Recommendation: the same bubble turns into the notice; never remove a bubble the person has seen.
8. **Hindi later.** No text in images, system font fallback, and `FlowRow` everywhere keep the door open; the day dividers and the "Next" strip use pattern-based `DateTimeFormatter` with `Locale.ENGLISH` today. Recommendation: leave as is, note it in `DIRECTION-NEXT.md`, and switch to the default locale when Hindi strings arrive.

Decisions for the owner before slice 1:

- **Does "Recent activity" leave the home screen?** Recommendation: yes; done cards stay collapsed in the thread and the full list is one tap away in Settings. Alternative: a "Today's activity" row at the bottom of the brief.
- **Thread direction.** Newest at the bottom like every messaging app, or newest at the top as today? Recommendation: bottom; the input is at the bottom and the eye should not travel.
- **The greeting word.** "Namaste" or plain "Hello"? Recommendation: "Namaste" in the first-run greeting only, "Hello" nowhere, no greeting on later opens.
- **Fixed palette.** Teal and turmeric as above, or does the owner want a different pair? Recommendation: ship these; change the two hex seeds in `BheemTheme.kt` if the owner dislikes them after a day of use, since the contrast test will tell at once whether the new pair still passes.
