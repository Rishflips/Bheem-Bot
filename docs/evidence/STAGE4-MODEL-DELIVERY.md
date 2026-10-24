# Stage 4 evidence: model delivery, build flavors and rules-only mode

Phone: Moto g86 power (Android 16), debug builds, 2026-10-25, home Wi-Fi (unmetered). All numbers are measured on this one phone and connection unless marked target.

JVM tests: 606 run across the modules, 0 failures (measured, after the review fixes). The downloader tests use real HTTP against a local `HttpServer` that logs every request's `Range` header. `scripts/check-tests-ran.py` (run in CI) fails if a class declares more `@Test` methods than JUnit ran.

## On the phone

| Check | Result |
| :-- | :-- |
| Fresh state, no model anywhere, set a reminder | "remind me to send the invoice at 5pm" became a reminder card ("send the invoice, Sun 25 Oct, 5:00 PM") with no model on the phone, through the same gate |
| Free-form request with no model | Answered with a message that the model is not ready and which commands still work (the first build still showed the file path in this message; the wording was fixed and covered by a JVM test) |
| Download from Hugging Face, `full` flavor | Started from the "Download on Wi-Fi" button; about 146 MB after 15 s; the request log shows a 302 from `huggingface.co` then a 200 from the CDN host |
| Download killed at about 40% (`am force-stop` at 1,059,698,561 of 2,588,147,712 bytes) | The `.part` file stayed (1,061,599,105 bytes). After reopening and tapping the button again the log shows `range=1061599105 -> 302` then `range=1061599105 -> 206`: the Range header survived the redirect and the transfer continued from that byte |
| Finished download | The file appeared under its final name at exactly 2,588,147,712 bytes, the `.part` file was gone, and the card disappeared |
| Free-form request with the downloaded model | "set a reminder for next Monday at 8 AM to submit the timesheet" gave a card "submit the timesheet, Mon 26 Oct, 8:00 AM" about 35 s after sending (first load of the new path, including the hash check) |
| `offline` flavor, cleared app, model placed in the external folder | The app copied it into private storage, deleted the external copy, and `sha256sum` on the phone gives `181938105e0eefd1...f9a63c`, equal to the pinned value |
| `offline` package permissions | `dumpsys package` lists no `INTERNET` and no network-state permission |

## Review

An Opus review (high effort) of the first version gave PASS WITH CHANGES. Fixed afterwards, each with a test written first:

- Four tests were silently skipped by JUnit because the function returned a value (`fun x() = runBlocking { ... assertFailsWith }`). They now return Unit, and the new CI check compares declared and executed tests. One of them was the only test of the over-size guard in `ModelFileWriter`; it passes now, and deleting the guard makes it fail.
- Cancel then start could put two writers on one `.part` file. A new run now waits for the cancelled one to finish, stale runs cannot change the state, and `finish()` checks the length on disk as well as the count.
- A model file of the wrong size, or one the host rejects, no longer leaves the app with a hidden download card: the supply deletes it and asks again.
- Coming back to the app no longer replaces a Paused or Failed card, no longer re-copies a sideload that failed its check, and the import uses its own `.import` file instead of the download's `.part`.
- Rules reminders with a date the resolver does not know are left to the model (message) instead of becoming today or tomorrow. Rules proposals carry model id `rules`.
- The https-only redirect rule and the 206/416 edge cases now have wiring-level tests; connections are closed on failures; the retry count resets whenever a try made progress; a wrong-length full answer no longer throws away good progress.
- A host hook that throws no longer hangs the caller; the host tells its owner when it rejects a file.
- The request trace is debug-only; the network-use script also catches `DownloadManager` and `WebView` and no longer hides grep errors.

Known and not changed: with `dataSync`, Android 15 and later cap foreground time (about 6 hours per day, a target to verify) and `onTimeout` stops the service while the download goes on without it; the 2.6 GB sideload import has no foreground service; if the abort lands after the native load returned, the engine can stay loaded until the next acquire.

After the review fixes the `full` build was installed again on the phone with the model in place: no download card, "note the locker number is 27" gave a note card, and "remind me on Christmas at 8am to buy gifts" went to the model and gave "buy gifts, Fri 25 Dec, 8:00 AM" (the rules path would have declined it).

## Build checks

- `:app:verifyManifests` checks the merged manifest of all four variants. Mutation check: removing `tools:node="remove"` and declaring the permission in the offline source set made the offline check fail with "declares INTERNET".
- `scripts/check-network-use.sh` passes on the repository; planting `import java.net.URL` in `models` made it fail.

## Not done

- The download was not tested on mobile data, with battery saver on, with the screen off for a long time, or in Doze. Those paths are covered only by `DownloadPolicy` unit tests (a pure table).
- A corrupted download (wrong hash) is covered by JVM tests against a local server (deleted, fetched again once, then `ChecksumFailed`), not on the phone.
- Free-space refusal is covered by a JVM test only.
- Download speed (about 10 MB/s here) depends on this Wi-Fi and the CDN. The 3 GB free-space figure in the direction document is a target; the code asks for the missing bytes plus 512 MiB (target).
- Cancelling a download stops at the next 64 KiB chunk or at the 15 s read timeout, whichever comes first, because a blocked socket read is not interruptible.
- No wake lock is held. A long download with the screen off was not measured.
- Clearing app data on the phone (`pm clear`, used to test the first run) also deleted the benchmark model files that sat in the app's external folder (LFM2.5, MiniCPM5, Moonshine). They were leftovers from Phase 0 and are not needed by the app.
