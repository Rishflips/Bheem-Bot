# Stage 3 evidence: the model host on the phone

Phone: Moto g86 power (Android 16), debug build, 2026-10-25. All numbers are measured on this one phone unless marked target.

JVM tests: 37 in `models/host` (host state machine on virtual time, governor table, registry, streaming hash, state store) plus the planner and parser tests in `agent-core`.

On the phone:

| Check | Result |
| :-- | :-- |
| Hash check of the 2.59 GB file on the first use after an install | The "Checking the model file" card was visible at the first 5 s poll and gone by the next (under 10 s) |
| Model load time, from the host's own record (`lastLoadMillis`) | 41.4 s on the first load after a fresh install, then 4.6 s and 6.3 s on later loads (the fast ones reuse the engine cache) |
| App sent to the background 2 s after sending a command, cold model | The foreground service `ModelLoadService` was reported running (`dumpsys activity services`), the process stayed alive, and the card was in the feed when the app came back; four trials, all cards present |
| Memory (`dumpsys meminfo`, total PSS) | 124 to 135 MB with no model; 2,224 to 2,327 MB with the model loaded. Idle unload measured from the moment the answer card appeared: still 2,317 MB at 174 s, 323 MB at 184 s, so it unloads about 180 s after the last request, as the timer says. (An earlier reading of "about 150 s" counted from my first memory reading, taken about 30 s after the request finished.) |
| A load made 200 s long on purpose (debug-only switch `no_backup/debug-slow-load-ms`), app sent to the background 2 s after sending | The foreground service ran until about 182 s and was gone by 187 s, the process stayed alive, the host aborted the load, and the card in the app said "the model took too long to load; try again" |
| Model file replaced with a 1 MiB dummy of the same name | Refused within 5 s with "the model file has the wrong size (expected 2588147712 bytes, found 1048576)"; the real file was moved aside and restored afterwards |
| Same-size file with different content | Covered by a JVM test only (a wrong hash is refused before the engine is touched); not run on the phone |

Found while testing on the phone and fixed: a new card inserted above the first visible card was off screen (the list now scrolls to the newest), and a cancelled request was reported as "planner failed: cancelled" (cancellation is now rethrown by the pipeline and the coordinator).

Not done on the phone: ten cold loads with the screen locked (the background trials above are the substitute), the thermal and battery-saver paths, and the GPU-failure fallback (the GPU loaded every time here; the fallback is covered by a JVM test).
