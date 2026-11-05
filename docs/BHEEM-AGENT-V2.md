# Bheem agent v2: one persona, memory first, real tool calls (draft 2026-11-05)

Status: proposal for the owner to approve. Nothing here is built yet. Numbers are marked measured or target.
Reference: the owner's Argon (Gemini) chat "Building On-Device Gemma Assistants", 5 Nov 2026. Model names it lists
(for example `unsloth/gemma-4-E4B-it-qat-mobile-GGUF`, `katanemo/Arch-Function-1.5B`) are unverified until we open the
Hugging Face page; treat them as leads.

## What the owner asked for

A lightweight personal assistant on the phone with Grok or Hermes energy: better memory, reasoning and knowledge, a bit of
proactiveness, good tool calling. In the very first chat it learns everything important about the owner and keeps it. Right
information matters more than speed. Reframing Bheem is allowed.

## Why v1 does not feel like that (measured on the phone, 4 and 5 Nov)

Every message passes a chain of fixed filters (small talk, device questions, records rules, planner, then chat). Each filter
answers in its own style, so the owner meets five different Bheems. "where is my location" fell through to the notes search
and got "I found nothing". Adding rules fixes one phrasing at a time and never gives one personality.

## The v2 shape

1. **One agent loop.** Every message goes to the model with the persona, the owner's facts, the last few turns and a small
   tool list. The model either answers or calls a tool; code runs the tool and hands the result back; the model answers in
   its own voice. Code keeps only exact fast paths (time, battery, sums) and the gate.
2. **Tools the model can call** (read tools run at once, write tools become a tap card, as today):
   `search_memory(query)`, `list_records(kind, day)`, `device_info()`, `remember_fact(text)`, `update_fact(old, new)`,
   `forget_fact(text)`, `create_reminder`, `save_note`, `create_task`, `add_event`, `draft_message`, `call`, `open_route`,
   `timer`, `torch`, `open_app`, `volume`. Calls are parsed and validated by code; an invalid call is retried once, then the
   model answers without it.
3. **First-chat interview.** On first open (and from Settings later) Bheem runs a short friendly interview: name and what to
   call you, city, work or study, key people (family, friends, boss), daily routine, food and other preferences, goals, the
   languages you use. It also offers to read your existing notes and suggest facts from them. At the end it shows everything
   it learned as one list; you keep all, edit or drop items with taps. Only kept items become facts.
4. **Memory.** Facts get a type (identity, person, place, preference, routine, goal) and an optional end date for short-lived
   things. A new fact that contradicts an old one becomes an `update_fact` card ("You said IndiGo before, now Air India.
   Replace?"). Each night the existing job suggests facts from the day's chat; nothing is used until you confirm.
   Retrieval stays keyword based first; on-device embeddings (EmbeddingGemma 300M, target under 200 MB) come later if
   keyword retrieval misses in the eval.
5. **Proactive, quietly.** Morning brief (exists), nudges for reminders and clashes, a follow-up on something you said you
   would do, and an occasional question to fill a memory gap. Default is silence; quiet hours; at most a few nudges a day
   (target). A small router model for background events (FunctionGemma 270M per the Argon chat) is a later experiment.
6. **Reasoning and knowledge, honestly.** A 2B to 4B model on a phone will not match Grok's knowledge or deep reasoning. We
   get closer by: trying Gemma 4 E4B (the owner's phone has 8 GB RAM, measured: moto g86 power, MT6878, Android 16), thinking
   mode on tool turns, and grounding answers in tools instead of the model's memory. Live web search stays deferred until the
   owner decides; the Argon chat's design (a web turn runs with no device or memory tools) is the plan when it comes.

## What does not change

The model only proposes; every write or phone action waits for a tap; untrusted text never reaches a tool call; core modules
make no network calls; held-out cases stay closed; new action types update `docs/SECURITY.md` and gate tests together.

## Slices (each ends with tests in the bheem sandbox and a check on the owner's phone)

1. Agent loop with tool calling, behind a setting, using the current E2B model. Done when the development conversations route
   through it and the owner's 10 everyday messages (to be written by the owner) get correct answers on the phone.
2. First-chat interview and the batch confirm screen.
3. Typed facts, update and forget cards, chat-derived suggestions.
4. E4B trial: load time, first text, answer time and RAM on the owner's phone (measured), then a choice.
5. Proactive nudges.
6. Embeddings, only if slice 1 to 3 evals show keyword retrieval missing.

## Open questions for the owner

- Ten real messages you would send Bheem on a normal day, and what a good answer looks like.
- Should old notes be offered as fact suggestions in the interview (recommended), or ignored?
- Web search: keep deferred, or plan it after slice 3?

## How the work is done

Opus designs and reviews each slice. Implementation can run through opencode on the bheem sandbox with the owner's model
credits (Gemini 3.1 Pro, Kimi, DeepSeek V4.1 Flash) once the owner signs opencode in to those providers.
