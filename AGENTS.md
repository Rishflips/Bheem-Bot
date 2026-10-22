# Notes for contributors and coding agents

Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and [`docs/SECURITY.md`](docs/SECURITY.md) first.

## Boundaries that must hold

- The model proposes typed actions. It never executes anything. Only executors run actions, and only after the gate.
- Untrusted text (notifications, shared content) goes to the no-tools extraction call only.
- `agent-core` and `memory` do not depend on any model runtime. Skills depend on them, never on each other.
- Core modules make no network calls. Only the downloader may, and only in the `full` flavor.
- A new action type changes the security surface: update `docs/SECURITY.md` and add gate tests in the same change.

## Verification

- Verify behaviour on fixtures and emulators. Never against a real person's data, and never paste real notifications, numbers, or VPAs into code, tests, issues, or logs.
- Gate and validator changes are test-first. Model swaps must pass the eval harness in `eval/`.
- Do not claim something works until the relevant test or run shows it.

## Comments

Write a comment only for what the code cannot say: a license or attribution notice, a fact forced by an outside system (Android version behaviour, a vendor quirk, with the version), a public contract, a link to an upstream issue, or a security invariant. Do not narrate the next line or record history. If code needs a paragraph to explain a surprise, change the code: a clearer name, a type, a smaller function. A rule belongs in a type, a test, or a lint check.

## Docs

Mark every number as measured or as a target. If a fact comes from a model card or vendor page, say so.
