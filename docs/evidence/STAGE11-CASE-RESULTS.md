# Stage 11 evidence: capture-to-card case results (in progress)

Date 2026-11-03. One phone (Moto g86 power, Android 16), offline debug build, Gemma 4 E2B on the graphics chip. Cases are in `docs/STAGE11-CASE-LIST.md`; the scripted ones are run by `scripts/phone/cases.py` (adb, logcat cleared before each case and scanned for `FATAL EXCEPTION`, `ANR in` and process death). Raw results: `eval/results/stage11-cases.json` (committed).

## Summary (measured)

- **31 of 40 cases pass, 1 fails, 8 are not run.** No case produced a crash, ANR or native fault in its logcat.
- **Not run (need the owner or a signed build):** C14 (screen lock mid-load: the phone has a screen lock that adb cannot dismiss), C28 (Doze, needs the screen lock too), C31 (Edit exists only on spoken cards), C36 (reboot, needs unlock), C37 to C40 (voice).
- **Not the release gate yet.** The gate wants this list on the signed release build; this is the debug build. The release key is the owner's.

## Findings

1. **Fixed: a message could be invisible.** Messages such as "I could not do that: payments come later", "I can't help with that yet" and "I found nothing about that in your notes and tasks" are added at the top of the Today list. When the list was already showing cards the screen kept the old position, so the message sat above the visible area and the user saw nothing (screenshot right after sending a payment request, then the message found by scrolling up). `TodayScreen.kt` now scrolls to the top when a message appears; C07, C08 and C09 read the banner text on screen and pass with the fix. The old build was shown failing by screenshot, not by the stricter script check.
2. **Open, owner's call: a quick double tap on OK undoes the reminder (C33).** After the first tap the card is replaced by one with Undo in the same place, so the second tap hits Undo (the history shows "You undid it"). No alarm is left and the card says it was undone, so nothing runs without consent, but a user who double taps ends up without the reminder. A short delay before Undo accepts a tap would fix it.
3. **The earlier checks were too loose in places.** C02 to C06 only show that the right kind of card appears with the right text; they do not check the card's time or fields beyond that. C24 and C25 now check that the warning banner is absent before the permission change and present after it (the first versions would have passed on a banner that was already there). C26 now checks the real press: with the microphone refused, Android shows its permission dialog first, then Bheem shows "The microphone is off for Bheem, so talking does not work."
4. **Test setup note:** the exact-alarm permission needs `cmd appops set --uid` to take effect; `appops set` on the package did nothing.

## Results

| Case | What | Result | Observed |
| :-- | :-- | :-- | :-- |
| C01 | "remind me to call Arun tomorrow at 5 pm" | pass | alarms 5 -> 6; card text: ['call Arun, Wed 4 Nov, 5:00 PM'] |
| C02 | "remind me in 30 minutes to check the oven" | pass | Today / Settings / Notifications for Bheem are off, so reminders cannot appear. / Turn on notifications / Reminders may arrive late until you allow... |
| C03 | "task buy milk" | pass | Needs you / Task / buy milk / OK / Dismiss / Upcoming reminders / Reminder / make up at morning 6 AM, Wed 4 Nov, 6:00 AM / Done / Reminder / remind... |
| C04 | "note the wifi password is on the fridge" | pass | Today / Settings / Notifications for Bheem are off, so reminders cannot appear. / Turn on notifications / Reminders may arrive late until you allow... |
| C05 | "schedule a meeting with Meena on Friday at 3 pm" | pass | Today / Settings / Notifications for Bheem are off, so reminders cannot appear. / Turn on notifications / Reminders may arrive late until you allow... |
| C06 | "tell Ravi I will be late" | pass | Today / Settings / Notifications for Bheem are off, so reminders cannot appear. / Turn on notifications / Reminders may arrive late until you allow... |
| C07 | A question about a saved note ("what did I note about the wifi") | pass | banner: I found nothing about that in your notes and tasks. |
| C08 | Small talk ("hello there") | pass | banner: I can't help with that yet. I can set reminders, tasks, notes, calendar events and draft messages. |
| C09 | "pay Arun 500 rupees" | pass | banner: I could not do that: payments come later. |
| C10 | Empty input, spaces only, then a 2000 character input | pass | alive=True; after long input: Today / Settings / Notifications for Bheem are off, so reminders cannot appear. / Turn on notifications / Reminders m... |
| C11 | Cold model: force-stop, open, type a free-form command | pass | card after 23 s |
| C12 | Warm model: a second free-form command straight after | pass | card after 15 s (includes UI polling) |
| C13 | A second command typed while the first is still loading | pass | loading note shown=True; a second command could be typed=False; cards waiting=1; alive=True |
| C14 | Screen locked 2 s into a cold load | not run | not run: the phone has a screen lock and did not unlock from adb |
| C15 | App sent to the background mid-load, brought back after 20 s | pass | Today / Settings / Needs you / Reminder / water the plants, Sun 8 Nov, 7:00 AM / OK / Dismiss / Upcoming reminders / Reminder / make up at morning ... |
| C16 | App force-stopped mid-load, opened again | pass | restarted cleanly and answered after a kill mid-load |
| C17 | Model file moved away: "remind me ..." still works; a free-form command shows the model-needed card | pass | rules path card=True; free-form: The model is not on this phone yet. Until the model is ready I can still do: remind me to ... at ..., note ..., ta... |
| C18 | Model file truncated: the file is refused, not loaded | pass | alive=True; restored=True; screen: The model is not on this phone yet. Until the model is ready I can still do: remind me to ... at ..., note ..., ... |
| C19 | Airplane mode on, typed reminder | pass | Today / Settings / Needs you / Reminder / call the plumber, Wed 4 Nov, 8:00 AM / OK / Dismiss / Upcoming reminders / Reminder / make up at morning ... |
| C20 | Airplane mode on, free-form command with the model | pass | Today / Settings / Needs you / Reminder / send the report, Thu 5 Nov, 4:00 PM / OK / Dismiss / Upcoming reminders / Reminder / make up at morning 6... |
| C21 | Airplane mode toggled while the model loads | pass | card=True |
| C22 | Free storage below 500 MB, typed command and OK | pass | free 342 MB during the test; card=True; alarms 8 -> 9; alive=True; banner=None |
| C23 | Battery saver on, typed reminder and OK | pass | alarms 6 -> 7; notes: ['Exact alarms are off for Bheem, so this reminder may arrive late. Allow exact alarms in settings.', 'Exact alarms are off f... |
| C24 | Notifications permission revoked, create a reminder | pass | banner before revoke=False, after revoke=True, card=True |
| C25 | Exact alarm permission revoked, create a reminder | pass | banner when allowed=False, when denied=True (the per-card warning is checked by eye in the screenshots, not here) |
| C26 | Microphone revoked, tap Talk | pass | system dialog shown=False (it is only offered until the user refuses twice); alive=True; a press after refusing shows: The microphone is off for Bh... |
| C27 | Microphone revoked while holding Talk | pass | app reopened cleanly after the revoke (Android restarts the app on a revoke): True |
| C28 | Device idle (Doze forced), reminder 2 minutes ahead | not run |  |
| C29 | OK on a reminder card | pass | alarms 6 -> 7 |
| C30 | Dismiss a card | pass | alarms 6 -> 6 |
| C31 | Edit a spoken command's text (the Edit button exists only on cards that came from voice) and send it again | not run |  |
| C32 | Undo after OK | pass | alarms 6 -> 7 -> 6; undo tapped=True |
| C33 | Two quick taps on OK | **FAIL** | alarms 6 -> 6 |
| C34 | Rotate the phone with a pending card | pass | landscape card=True, portrait card=True |
| C35 | App killed with a pending card, opened again | pass | card back=True; alarms unchanged=True |
| C36 | Reboot with a pending reminder | not run |  |
| C37 | Cold model, spoken reminder | not run |  |
| C38 | Warm model, a 3 second spoken command | not run |  |
| C39 | Silent press and a very short press | not run |  |
| C40 | A very long press (over 30 s) and a cancel mid-hold | not run |  |

## Not verified

- Release build, other phones, other Android versions, a second runtime of the list on a clean install.
- C11 to C16 ran on the graphics chip only; a CPU-only run (the fallback) was not made.
- C22 filled storage to about 342 MB free; a completely full phone was not tried.
- Typed text only for C01 to C13; the voice path is covered only by the earlier Stage 7 and Stage 13 runs.
