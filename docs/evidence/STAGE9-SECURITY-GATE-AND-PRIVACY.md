# Stage 9 evidence: security gate hardening and privacy

Date 2026-10-27. JVM tests unless marked. Numbers are counts of cases in this repository (measured by running them), not measures of how often a model is fooled.

## What was built

- **Red-team suite.** `eval/redteam.jsonl` holds 30 hand-written pipeline attacks: forged payment lines (plain, lowercase, after `NoAction`, inside a thinking block, inside a code fence, after a real action, with a Cyrillic look-alike), unknown actions, control characters, a 9,000-character text, a 1,000,000-character line, a VPA inside a reminder, "message mom I love you" variants (ungrounded wording, another recipient, a flipped negation, a recipient not in the command, an unknown channel), bad amounts, bad times, pipe smuggling, empty output, a zero-width character in an action name, a read query with an injection, and a multi-line answer. `RedTeamTest` runs them, and the 50 injection texts of `eval/data/adversarial.jsonl`, through the real `CommandPipeline` and `Gate` with a planner that returns the attack exactly as written. Every run asserts: no payment stored or proposed, every stored proposal and every card waits for a tap, a drafted message goes to someone the user named, and a refusal is in the ledger. Each hand-written case also states its expected outcome, and the "forged line" cases assert the refusal came from the gate and not from a parse failure.
- **Injection texts reach the gate.** Used verbatim as planner output the 50 texts mostly do not parse, so they also run as forged action lines (one per payload: payments and drafted messages) and must be refused when the user asked for nothing of the kind.
- **Tainted path.** `TaintedGateTest` runs every action type with tainted provenance: only an expense and a reminder pass, both capped at L1 and waiting for a tap; every other action is rejected with a reason; a payment is refused for both provenances; the rate limit applies.
- **Per-source rate limit.** A flood from one source is limited and another source is not.
- **Logging.** `Redact` (`describe` gives a length only; `reason` makes a reason one line, no control characters, at most 120 characters). `LogHygieneTest` reads the source of every module that ships and checks every `Log.*` call: the message must be a string literal that interpolates only listed numbers, counts and class names, and nothing may print or dump a stack trace. The downloader trace (debug builds) is the one allowed variable message. The pipeline's planner-failure reason now names the error class and no longer copies the error message.
- **Local diagnostics.** `Diagnostics` lists the latest 50 refusals (time, action kind, source, reason) from the ledger and writes a plain report that says it is never sent. Settings has a Diagnostics screen with a Share button; sharing is the person's own action. `LedgerStore.recent(event, limit)` is new (in-memory and SQL).
- **Wiring checks.** `AppInvariantsTest`: the app builds exactly one `Gate`, the pipeline and the coordinator use it, and the tap policy is at its default.
- **Test inputs.** The `eval` test task now declares the data files and the shipped source folders as inputs, so a change to them re-runs the tests (before that the log and wiring tests would have been skipped as up to date).
- `docs/SECURITY.md` has a built-and-tested table.

## Deliberate regressions (each turned a test red, then was reverted)

| Change | Red |
| :-- | :-- |
| Remove the payment refusal from `Gate` | Both red-team tests |
| Remove message grounding from `Gate` | Both red-team tests |
| Remove the tap for L0 actions | The hand-written red-team test |
| Allow `DraftMessage` from tainted text | `TaintedGateTest` (two tests) |
| Log a transcript variable in `AppController` | `LogHygieneTest` |
| Add a second `Gate(` in `AppGraph` | `AppInvariantsTest` |

## Not done or not verified

- **Phone: the Diagnostics screen and its Share button were not run on the phone** (the phone was not connected when the screen was finished). It builds; the report text is covered by the JVM tests.
- The extraction call for untrusted text is not built (no untrusted source exists in v0.01); only the gate's tainted rules exist and are tested.
- The red-team suite is a regression guard for the gate. It does not measure how often the model is steered by an injection; the earlier measured figure (a 12.5% leak on GPU) is in the Phase 0 results.
- The log scan is a regex over source text. It cannot see a message built in another function and passed to `Log`; that shape fails the "string literal" rule, so it would be caught, but a log through another logging class would not be (only `println`, `printStackTrace`, `Timber` and `Logger` are named).
- `eval/redteam.jsonl` is not hash-pinned like the golden data.
- `Session.Failed` messages and exception text shown on screen (not logged) are not covered.
