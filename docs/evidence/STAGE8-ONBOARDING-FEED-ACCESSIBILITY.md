# Stage 8 evidence: onboarding, Today feed, cards and accessibility

Phone: Moto g86 power (Android 16), debug build, 2026-10-27, one phone. Numbers are measured on it unless marked target.

## What was built

- **Onboarding in place.** On first run (and after Erase everything) one screen says what Bheem is, that nothing leaves the phone, and that every action waits for a tap. No permission is asked there.
- **Permissions when needed.** The notification permission is no longer requested at launch; it is asked when the first reminder card is approved (`MainActivity.approve`), once. The microphone is asked at the first hold (Stage 7). Exact alarms stay an inline card.
- **Today feed in three sections.** Needs you (waiting, working, failed), Upcoming reminders (done reminders whose time is still ahead, soonest first, with Undo inside its window), Recent activity. `FeedSections` and `LedgerLines` are in `agent-core` with tests. An upcoming reminder is kept in the feed even when more than 20 newer items exist. Dismissed and undone items are in no section.
- **Model status.** The existing download and loading cards stay; a failed model card now has Try again; Settings shows ready, idle, absent or failed, and the backend in words.
- **Settings.** Model status, Activity history (every proposal and what happened to it, including dismissed, undone and refused, in plain words), licences, version, Erase everything with a two-step confirm and an optional "also remove the language model".
- **Erase everything.** Cancels alarms and notifications, erases the encrypted database and its Keystore key (existing code), clears the preferences, then deletes everything under files, no_backup, code cache, shared_prefs, databases and the external files folder through `AppDataEraser`, except the model unless the box was ticked. A file that cannot be deleted stops the restart with a message instead of being ignored.
- **Strings.** Every string in a composable or notification is in `res/values/strings.xml`; `scripts/check-ui-strings.sh` (in CI) fails on a literal in `Text("`, `contentDescription = "`, `setContentTitle("` or `setContentText("`.
- **Accessibility.** Card title and detail are one merged element (title, then detail, then the actions); section and screen titles are headings; the Talk button and the 48 dp minimum size are set explicitly; the Talk label colour on the error background is `onError`; a failed card says "Did not work" in words as well as colour.

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

## Not done or not verified

- **TalkBack recording of set reminder, approve, undo: not made.** TalkBack was switched on but its welcome tutorial took the adb gestures and I could not drive it reliably; it was switched off and the accessibility settings restored.
- **Accessibility Scanner: not run** (not installed). The target-size script, the merged semantics and the screenshots are partial substitutes, not the scanner.
- Strings that come from code outside composables are still English literals: controller messages (`AppController`), the Ask answers, the reminder health hints (`OemHint`), the action descriptions in `agent-core`. Hindi needs these moved (listed for later).
- A reminder that passes its time while the app stays open moves from Upcoming to Recent only when the feed refreshes (any change, or the app resuming); there is no timer on purpose.
- The Keystore alias was removed by the existing `KeyVault.erase`, tested in Stage 2; this stage did not look at the alias between the erase and the restart.
- The licence text names LiteRT-LM and SQLCipher licences from my reading of their projects, not from a check of the shipped artifacts; confirm in Stage 10.
- The system font scale test did not include the Ledger and Licences screens at 200%.
