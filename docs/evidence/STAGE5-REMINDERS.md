# Stage 5 evidence: reminders and notifications reliability

Phone: Moto g86 power (Android 16), debug `full` build, 2026-10-25. All numbers are measured on this one phone. Times are the phone clock (IST).

JVM tests: 629 across the modules after the stage 5 code, 0 failures (measured). 27 instrumented storage tests pass on the phone (22 earlier plus 5 for the reminder store).

## On the phone

| Check | Result |
| :-- | :-- |
| Undo cancels the alarm | `dumpsys alarm` showed one pending Bheem alarm after approving; after Undo it showed none |
| Equal text and time do not collide | Two "twin test" reminders at 02:43 gave two pending alarms and two separate notifications |
| Exact permission revoked while three alarms were pending | The system converted them: flags `0x3` (alarm clock) became `0x20` (inexact, allow while idle) with windows of about 3m48s to 15m. The 03:05 reminder fired at 03:06:52, inside its window. After the permission was granted again the flags were `0x3` again (the rearm receiver may have done this; not proven which component did) |
| Reminder created while exact alarms are off | The ledger entry carries the note "may arrive late" and the card shows it (JVM tests for the note and the feed; the card itself was not photographed) |
| Force-stop | `am force-stop` removed every Bheem alarm (0 pending). About a minute later a vendor component ("aitune") started the app on its own, `rearm` armed the alarm again, and the 02:57 reminder fired at 02:57:00. The "force-stopped and not started again" case could not be isolated on this phone |
| Reboot, first attempt | Bug found. After the unlock Android started Bheem for the rearm broadcast at 03:13:18 and killed it at 03:13:30; the receiver was waiting up to 8 s for the whole app graph, so nothing was re-armed and two reminders were lost |
| Fix | Receivers now open the database through `SharedStorage` and never wait for the app graph. After a reinstall (package-replaced broadcast) the log shows `rearm: 2 armed, 0 missed, 69 ms` |
| Reboot, reminder due after the unlock | A reminder due at 03:20 fired at 03:20:00.046 after a reboot and an unlock at 03:17:49 |
| Reboot, reminder that fell due while locked | "missed three" was due at 03:24; the PIN was entered at 03:28:28; the log shows `rearm: 1 armed, 1 missed` and the phone showed one notification "You missed a reminder". The reminder due at 03:29 fired at 03:29:02 |
| Light Doze | A reminder due at 03:39 fired at 03:39:02 while `deviceidle get light` was IDLE (deep was still in the quick-doze delay) |
| Deep Doze | See "Deep Doze" below |

## Deep Doze

`dumpsys deviceidle force-idle` alone never reached deep idle on this phone: it stops at `QUICK_DOZE_DELAY`. What worked: fake-unplug the battery (`dumpsys battery unplug`), screen off, wait about 10 minutes, then `dumpsys deviceidle step deep` once. The first three tries put the reminder too close to the delay, so it fired in light Doze. The fourth try created a reminder due at 04:22; the screen went off at 04:04:39 and `get deep` read IDLE at 04:13:04. The reminder ("deep doze six") fired at 04:22:00.064, on time, but the test is not valid: the log shows a fingerprint wake of the screen at 04:15:06 (WAKE_REASON_BIOMETRIC), so deep idle had ended before the alarm and `get deep` read ACTIVE at 04:21:40. The deep Doze criterion is therefore not proven. A second try on 2026-10-26 (reminder due 21:41) was also ended by a fingerprint wake at 21:17:17. The owner waived this check on 2026-10-26; it stays listed as not done. Reminders did fire in light Doze (03:39:02) and with the screen off in the quick-doze delay.

## Review

An Opus review (high effort) gave PASS WITH CHANGES. Fixed afterwards, each with a test written first unless marked:

- A failure while re-arming could crash the app at every start (the rearm ran inside a result handler that does not catch exceptions) and could abort the loop for later rows. Rearm now arms row by row, reports rows it could not arm, and never throws; the receivers catch and log only the exception class.
- Erase and the shared database: a receiver could reopen the database between close and delete. Erase now runs close, delete and clear under one lock (`Shared`, tested with two threads) and cancels all alarms first.
- The catch-up notice only listed the rows one call moved, and two calls overwrote each other. The notice now lists every unseen missed row; rows become seen when the app is opened.
- The 2 minute grace window turned late reminders into "missed" whenever a rearm ran before delivery. Only a boot marks short overdue rows missed; other triggers arm an overdue row to fire now (cap 10, none older than 24 hours).
- `RearmReceiver` is exported; it now ignores every action except the five system ones, and `SECURITY.md` describes it.
- Smaller fixes: dismissing a failed reminder cancels what it armed; a refused exact call falls back to inexact; a failed second approval removes the old alarm; `reschedule` only touches scheduled rows; cancel no longer rewrites a live PendingIntent; the reminder text is no longer put in the alarm extras (the legacy branch was removed); the catch-up notice is private on the lock screen; docs restored (a heading had been lost).
- Tests added for the grace boundary (exactly 120 s), catch-up text for 2 and 3 rows, daylight-saving gap and overlap, the store ordering and guards (instrumented), the trigger check, cancel-all, cancel by proposal.

Not changed, listed: the Android 12 to 15 behaviour after a revoke was not tested (only Android 16 was; with the new rules a rearm at app start re-arms the rows as inexact); `AndroidAlarmPort` and the receivers have no JVM test (the app module has no unit test source set; they were checked on the phone); Undo after a reminder fired shows "could not undo".

## Not done

- Deep Doze (waived by the owner, see above).
- Opus review of stage 5 (see the Review section once it has run).
- Not tested on the phone: timezone change, the opening of the app from a tapped reminder notification, the lock-screen privacy of the notification text, the battery hint card on a Xiaomi, Samsung or Realme phone, the notifications-off card.
- The phone cannot show the "missed after force-stop" case because a vendor component restarts the app.
- A reminder whose text the model wrote as "remind me after boot three" kept the words "remind me" in its text; this comes from the model output, not the scheduler.
