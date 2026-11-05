# Bheem paused (2026-11-05): read this first when the project resumes

The owner paused Bheem on 5 Nov 2026 to wait for stronger on-device models. Everything is on GitHub; nothing is merged.

## Where the code is

- **Branch `v2/agent`** (head `50788af`) holds all v2 work. It is not merged into `wip/personal-agent`, `phase0-benchmark`
  or `main`. CI on GitHub passes on it and keeps the full debug APK as an artifact for 7 days.
- Specs for every round are in `docs/slices/V2-*.md`; the plan is `docs/BHEEM-AGENT-V2.md`.
- `config/debug.keystore` is a throwaway debug key, committed on purpose (owner approved, public repo) so that sandbox and
  CI debug builds update each other in place. Release APKs never use it.

## What v2 does (built, tests and gate pass, installed and partly checked on the owner's phone)

1. One persona for every reply (`AgentLoop`), with two read-only tools over the owner's own records; SmallTalk removed.
2. First-chat interview with one "What I learned about you" plan card; resumes after restart; a non-name answer is not
   stored as the name.
3. Live memory: `Fact|` suggestions grounded in the owner's words (negation and slot checks), `FactSlots`, replace cards
   via an optional `replaces` argument enforced by the gate.
4. Quiet in-app proactive nudges (clashes, follow-ups, gap questions), daily cap, quiet hours.
5. Settings switch between Gemma 4 E2B (default) and Gemma 4 E4B, per-model files, "remove the other model".
6. Speed: plain chat skips the planner; the model stays loaded 15 minutes in the foreground; system prompt 700 characters
   (was 1492); history 4 turns; memory block 4 lines; default answer 110 tokens.

Reviews: Fable 5.1 (medium) did the final review of slices 1 to 4; its findings were fixed in `a4c5f01`.

## Measured on the owner's phone (moto g86 power, MT6878, 8 GB RAM, Android 16)

- Gemma 4 E4B: first text 7.4 to 9.2 s, answer 13.8 to 17.7 s, about 400 MB RAM free and 3 GB in swap. Too heavy for 8 GB.
- Gemma 4 E2B after speed round 1 (`route=chat`, GPU, warm): first text 2981 to 3032 ms, answer 6081 to 7813 ms.
- Speed round 2 (`50788af`) was installed but **not measured** before the pause.

## Honest limits found (the reason for the pause)

A 2B model on an 8 GB phone gives a warm, private assistant with memory and actions, but not Grok or Hermes level knowledge
or reasoning, and no current knowledge without the internet. Options noted for later: a much stronger small model, a
phone with more RAM, or a hybrid where hard questions go, with the owner's permission, to a cloud model while memory stays
on the phone.

## Not verified

- Speed round 2 timings; the full interview flow end to end on the phone; nudges over several days; the E4B timing with
  speed fixes (not planned for an 8 GB phone).

## Environment at pause

- The instabox sandbox "bheem" was **deleted** at the owner's request (with its clone, logs, opencode setup and the GitHub
  token stored there). A new sandbox needs: JDK 17, Android SDK 36 (build-tools 36.0.0), a clone, `~/ci.sh`, a fine-grained
  GitHub token (Contents read and write on this repo only), opencode with the owner's Ollama cloud key. The owner should
  revoke the old `bheem-sandbox` token on GitHub.
- The app and all its data (models, memory) were **uninstalled** from the owner's phone at the owner's request.
- Worker models used: `ollama-cloud/deepseek-v4.1-flash:1017` for implementation (fast, reliable); `ollama-cloud/kimi-k3`
  was too slow for a fix round. Workers need `.oc/ci.sh` inside the repo and an `opencode.json` with
  `"external_directory": "deny"`, or opencode aborts the run.

## Resume checklist

1. Read this file, `AGENTS.md`, `docs/BHEEM-AGENT-V2.md`.
2. Pick the model (check new small on-device models first; measure on the owner's phone before choosing).
3. Rebuild the sandbox, install from the CI artifact of `v2/agent`, measure speed round 2, then decide on merging v2.
