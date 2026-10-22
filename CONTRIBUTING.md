# Contributing to Bheem

Bheem is in the design phase, so the most useful contributions right now are review and research.

## Right now

- Read [`docs/`](docs/) and open an issue where something is wrong, unclear, or unrealistic.
- Add verified facts or corrections to [`docs/RESEARCH.md`](docs/RESEARCH.md), with a source link.
- If you have a mid-range Android phone (₹15,000–₹25,000), you can help with the Phase 0 benchmark. See [`docs/MODELS.md`](docs/MODELS.md).

## Once code lands

- **Notification template packs.** Each pack describes how one bank or UPI app words its alerts, with fixtures that prove the parser. Fixtures must be **anonymised**: replace names, account numbers, and VPAs, and never include OTPs.
- **Hinglish test cases.** Short voice-style commands with the expected typed action.
- **Skills and fixes.** Open an issue first for anything that adds a new action type; new actions change the security surface (see [`docs/SECURITY.md`](docs/SECURITY.md)).

## Ground rules

- No real personal data in issues, fixtures, or logs.
- No network calls in the core modules. Anything that needs the network lives in the downloader module and must be documented.
- Every behaviour change ships with a test. Model swaps must pass the eval harness.
- Claims in docs must be verifiable. Mark targets as targets.
