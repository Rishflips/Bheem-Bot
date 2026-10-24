# Stage 4 evidence: model delivery, build flavors and rules-only mode

Phone: Moto g86 power (Android 16), debug builds, 2026-10-25, home Wi-Fi (unmetered). All numbers are measured on this one phone and connection unless marked target.

JVM tests added: 18 in `downloader` (real HTTP against a local `HttpServer` that logs every request's `Range` header), 9 in `ModelFileWriterTest`, 10 in `ModelSupplyTest`, 3 registry tests, 9 in `RulesPlannerTest`, plus the host abort change.

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
