# Stage 11 capture-to-card case list

The written list the v0.01 gate asks for (`docs/V001-PLAN.md`: "No crash in the capture-to-card path across a written test list"). Forty cases, each run on the phone with logcat kept for the case. Results and what was and was not run are in `docs/evidence/STAGE11-CASE-RESULTS.md`.

**Pass for every case:** no `FATAL EXCEPTION`, no `ANR in`, and no process death in the logcat of that case; nothing is scheduled or run before the tap on OK; a failure shows an honest message in plain words, not a blank screen or a card that pretends it worked. Each case also has its own check below.

**How a case is run:** S = scripted through adb (`scripts/phone/cases.py <id>`), O = the owner does it on the phone while a script keeps logcat, SO = scripted with one owner step (unlock, speak).

**Build under test:** the offline debug build. The gate also needs the list run on the signed release build, which needs the release key; that repeat is not done until then.

| Id | Group | Case | Run | Own check |
| :-- | :-- | :-- | :-- | :-- |
| C01 | Typed | "remind me to call Arun tomorrow at 5 pm" | S | Reminder card with the right time; no alarm until OK; after OK one alarm |
| C02 | Typed | "remind me in 30 minutes to check the oven" | S | Reminder card about 30 minutes ahead |
| C03 | Typed | "task buy milk" | S | Task card |
| C04 | Typed | "note the wifi password is on the fridge" | S | Note card |
| C05 | Typed | "schedule a meeting with Meena on Friday at 3 pm" | S | Calendar card, nothing in the calendar before OK |
| C06 | Typed | "tell Ravi I will be late" | S | Draft message card; nothing sent |
| C07 | Typed | A question about a saved note ("what did I note about the wifi") | S | Answer from the user's own records or an honest "not found" |
| C08 | Typed | Small talk ("hello there") | S | A plain reply, no card to approve |
| C09 | Typed | "pay Arun 500 rupees" | S | Refused as "payments come later"; no payment card |
| C10 | Typed | Empty input, spaces only, then a 2000 character input | S | No crash; no card for empty input; long input handled or refused in words |
| C11 | Model | Cold model: force-stop, open, type a free-form command | S | Card appears after loading; "getting ready" is shown while loading |
| C12 | Model | Warm model: a second free-form command straight after | S | Card in a few seconds, no second load |
| C13 | Model | A second command typed while the first is still loading | S | Both answered or the second is told to wait; no crash, no duplicate card |
| C14 | Model | Screen locked 2 s into a cold load | SO | After unlock the app is alive and the card appears or a clear message |
| C15 | Model | App sent to the background mid-load, brought back after 20 s | S | Load finishes or restarts; card appears |
| C16 | Model | App force-stopped mid-load, opened again | S | Starts normally; no stuck "getting ready" |
| C17 | Model | Model file moved away: "remind me ..." still works; a free-form command shows the model-needed card | S | Rules-only path works; honest card for the rest |
| C18 | Model | Model file truncated: the file is refused, not loaded | S | "wrong size" style message; no native crash; file restored afterwards |
| C19 | Network | Airplane mode on, typed reminder | S | Same card as online |
| C20 | Network | Airplane mode on, free-form command with the model | S | Works; nothing needs the network |
| C21 | Network | Airplane mode toggled while the model loads | S | Load unaffected |
| C22 | Storage | Free storage below 500 MB, typed command and OK | S | Card saved, or a plain message; no crash; filler file removed afterwards |
| C23 | Battery | Battery saver on, typed reminder and OK | S | Alarm scheduled; health banner honest if reminders may be late |
| C24 | Permission | Notifications permission revoked, create a reminder | S | The "Notifications for Bheem are off" banner shows; reminder card still works |
| C25 | Permission | Exact alarm permission revoked, create a reminder | S | The "may arrive late" banner shows |
| C26 | Permission | Microphone revoked, tap Talk | S | The "microphone is off" message; no crash |
| C27 | Permission | Microphone revoked while holding Talk | S | No crash; honest message or nothing heard |
| C28 | Permission | Device idle (Doze forced), reminder 2 minutes ahead | SO | The reminder notification appears (owner confirms) |
| C29 | Card | OK on a reminder card | S | One alarm; card moves to Upcoming |
| C30 | Card | Dismiss a card | S | No alarm; ledger shows it dismissed |
| C31 | Card | Edit a spoken command's text (the Edit button exists only on cards that came from voice) and send it again | O | New card from the edited text, old one gone |
| C32 | Card | Undo after OK | S | Alarm removed |
| C33 | Card | Two quick taps on OK | S | One alarm, not two |
| C34 | Card | Rotate the phone with a pending card | S | The card is still there |
| C35 | Card | App killed with a pending card, opened again | S | The card is still there; nothing ran |
| C36 | Card | Reboot with a pending reminder | SO | After unlock the alarm is armed again |
| C37 | Voice | Cold model, spoken reminder | O | "Heard" text and a card |
| C38 | Voice | Warm model, a 3 second spoken command | O | Card; release to card under 6 s (target) |
| C39 | Voice | Silent press and a very short press | O | Plain "could not hear" style message, no card |
| C40 | Voice | A very long press (over 30 s) and a cancel mid-hold | O | Recording stops by itself or cancels cleanly; no crash |
