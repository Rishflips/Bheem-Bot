# Stage 8 evidence: onboarding, Today feed, cards and accessibility

Phone: Moto g86 power (Android 16), debug build, 2026-10-27, one phone. Numbers are measured on it unless marked target.

## What was built

- **Onboarding in place.** On first run (and after Erase everything) one screen says what Bheem is, that nothing leaves the phone, and that every action waits for a tap. No permission is asked there.
- **Permissions when needed.** The notification permission is no longer requested at launch; it is asked when the first reminder card is approved (`MainActivity.approve`), once. The microphone is asked at the first hold (Stage 7). The reminder problem cards (notifications off, exact alarms, battery) are hidden until the person has approved a reminder or has an upcoming one (`ReminderHealth.shown`).
- **Today feed in three sections.** Needs you (waiting, working, failed), Upcoming reminders (done reminders whose time is still ahead, soonest first, with Undo inside its window), Recent activity. `FeedSections` and `LedgerLines` are in `agent-core` with tests. Every recent reminder is read by its own query (`FeedSource.rowsFor`, `todayRows`), so an upcoming reminder stays in the feed however many newer rows there are (a test with 150 newer rows through a 100-row limit). Dismissed and undone items are in no section.
- **Model status.** The existing download and loading cards stay; a failed model card now has Try again; Settings shows ready, idle, absent or failed, and the backend in words.
- **Settings.** Model status, Activity history (every proposal and what happened to it, including dismissed, undone and refused, in plain words), licences, version, Erase everything with a two-step confirm and an optional "also remove the language model".
- **Erase everything.** Cancels alarms and notifications, erases the encrypted database and its Keystore key (existing code), clears the preferences, then deletes everything under files, no_backup, code cache, shared_prefs, databases and the external files folder through `AppDataEraser`, except the model unless the box was ticked. A file that cannot be deleted stops the restart with a message instead of being ignored.
- **Strings.** The text in composables and notifications is in `res/values/strings.xml`; `scripts/check-ui-strings.sh` (in CI) fails on the literal shapes it can grep for (`Text("`, `contentDescription = "`, `setContentTitle("`, `setContentText("`, a notification channel name, `text = "...`). A string joined in code is not caught; the review looks for those (a few remain: the ledger row joins, the size format).
- **Accessibility.** Card title and detail are one merged element (title, then detail, then the actions); section and screen titles are headings; the Talk button and the 48 dp minimum size are set explicitly; the Talk label colour on the error background is `onError`; a failed card says "Did not work" and the reason in words as well as colour; a waiting card announces "Waiting for your OK" as its state; the Talk control is one button node with a click action to start and stop.

## Tests (JVM)

`FeedSectionsTest` (sections, upcoming sorting and cap, dismissed and undone excluded, unreadable time, every outcome of the ledger in plain words), `AppDataEraserTest` (roots stay, model kept, missing root, an undeletable file is reported). The Compose screens have no automated UI test in this stage.

## On the phone (measured)

| Check | Result |
| :-- | :-- |
| Onboarding on first run | Shown; "Get started" goes to Today |
| Notification permission timing | With the permission revoked, approving the first reminder showed the system prompt; no prompt at launch. After Allow the card moved to Upcoming reminders with Undo and the "notifications off" card went away |
| Today sections | Needs you, Upcoming reminders and Recent activity appeared as described |
| Activity history | Listed reminders waiting, dismissed (by voice), done and others, newest first, with the time |
| Touch targets | `scripts/phone/targets.py` (uiautomator dump): no clickable element under 48 dp on Today or Settings (density 450) |
| Text size 200% | System font scale 2.0: Today and Settings wrap without clipping; other screens not checked |
| Light and dark | Today checked in both |
| Erase, model kept | Onboarding shown again, 0 pending alarms, files left: the model, a new empty database and a new key (both created by the restart) |
| Erase, model removed | Files left: a new empty database and a new key; the Today screen offered "Download on Wi-Fi" |
| First launch to first card | 71.8 s from `am start` (onboarding tap, a 2.6 GB sideloaded model imported and verified, a typed reminder). One run. The target is 90 s |

After the erase the app opens a new database and key at once, so "no files" holds at the moment of erasing (the JVM test) but a restarted app always has those two.

## After the Opus review

The review (PASS WITH CHANGES, no Critical findings) led to these changes.

- Reminder problem cards no longer appear before a reminder exists (they were the system-settings shortcuts the plan warned about).
- The approval that waits for the notification prompt is kept in the saved instance state and applied by id; a second tap while the prompt is up is ignored; the "already done" refusal no longer shows a message.
- Erase: the erasing screen shows first, a marker file lets a half-finished erase resume at the next launch, errors end in a screen with Try again, and cache and external cache are erased too.
- Settings rows are clickable buttons (they were announced as unchecked toggles); the "also remove the model" box resets when the dialog opens again; the text no longer promises a download in the offline flavor.
- The status cards (problems, messages, model, download) are list items above the sections, so the input bar stays on screen when they are long.
- The Talk control has a click action and a Button role (one node, checked in a uiautomator dump: class Button, clickable); the done row and the action rows wrap.
- The activity history shows a loading state, a note when 300 items are shown, and plain words for questions ("Question", "Answered from your notes").
- Onboarding says "Bheem sends nothing off your phone". The string check was widened.

Phone recheck of the changed flows (measured): after Erase everything and Get started the Today screen shows no problem card; after typing a reminder and approving it, the battery card appears under Upcoming reminders; no `erase-pending` file remains after an erase; the Talk node is a single Button.

Deferred from the review (not fixed): `refreshFeed` can finish after a newer feed pass and show older rows (send it through the same stream); the eraser follows symlinks and deletes directories without the injected delete; a fallback sideload folder is deleted even when "keep" is chosen if the external folder is missing; plural forms for the notification and file counts; the licence texts are not shipped inside the app (needed for the release, Stage 10); `targets.py` skips non-clickable nodes and reads the physical density; rotation and process death while the notification prompt is up were not tried on the phone; the erase resume path was not exercised (no way to stop the app mid-erase here).

## Not done or not verified

- **TalkBack recording of set reminder, approve, undo: not made.** TalkBack was switched on but its welcome tutorial took the adb gestures and I could not drive it reliably; it was switched off and the accessibility settings restored.
- **Accessibility Scanner: not run** (not installed). The target-size script, the merged semantics and the screenshots are partial substitutes, not the scanner.
- Strings that come from code outside composables are still English literals: controller messages (`AppController`), the Ask answers, the reminder health hints (`OemHint`), the action descriptions in `agent-core`. Hindi needs these moved (listed for later).
- A reminder that passes its time while the app stays open moves from Upcoming to Recent only when the feed refreshes (any change, or the app resuming); there is no timer on purpose.
- The Keystore alias was removed by the existing `KeyVault.erase`, tested in Stage 2; this stage did not look at the alias between the erase and the restart.
- The licence text names LiteRT-LM and SQLCipher licences from my reading of their projects, not from a check of the shipped artifacts; confirm in Stage 10.
- The system font scale test did not include the Ledger and Licences screens at 200%.
