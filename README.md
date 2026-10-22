# Bheem

**Your phone's own agent. Private, proactive, and fully on-device.**

Bheem is an open-source personal agent for Android. It watches the signals your phone already receives (payment alerts, reminders you speak, things you share), works out what needs doing, and lines up one-tap actions for you. No cloud brain, no account, no data leaving the device.

> **Status: design phase.** There is no code yet. This repository currently holds the product and engineering design. See [`docs/ROADMAP.md`](docs/ROADMAP.md) for what comes first.

---

## What it is

- **A Today feed, not a chat box.** Open Bheem and see a few ready-to-approve cards: a bill due tomorrow, a dinner to split, a reminder you asked for by voice.
- **The Bar.** Hold to talk (Hindi, English, Hinglish), type, or snap a photo. Bheem turns it into a typed action and shows you before anything happens.
- **Proactive, not always-on.** Bheem wakes on events (a notification, a schedule), does its work, and goes back to sleep. No resident service draining the battery.
- **Local by construction.** The language model, speech, and memory all run on the phone. An `offline` build ships without the `INTERNET` permission so anyone can verify it.

## What it is not

A cloud agent can browse the web and drive other apps' screens. Bheem cannot, and does not pretend to. It acts through Android intents, its own encrypted data, and hand-offs (for example opening your UPI app with the payment pre-filled). The trade is smaller reach for privacy, speed, and reliability.

## Principles

1. **Local first.** Nothing leaves the device in the core product.
2. **Action over chat.** Cards and one-tap approvals beat a blank prompt box.
3. **The model proposes, code decides.** The language model can only choose from a fixed catalog of typed actions. A deterministic policy gate decides what runs and what needs your tap or fingerprint.
4. **Rules before models.** Bank and UPI alerts are templated, so deterministic parsers handle them first. The model covers the fuzzy cases.
5. **Reliability over breadth.** Fewer features, done well, with tests that prove it.
6. **Swappable models.** Models change monthly. The model layer is an interface, not a dependency.

## v1 scope

| Skill | What it does |
| :--- | :--- |
| **Money** | Zero-entry expense tracking from payment notifications, bill-due reminders, bill splits sent as a message with a payment link |
| **Tasks and Reminders** | Hinglish voice capture into reminders, tasks, and calendar events |
| **Ask** | Questions over your own data: "what did I spend on food last week?" |

Health-lite (medicine reminders, steps and sleep from Health Connect) follows in v1.1. iOS is out of scope for now.

## Documentation

| Doc | Contents |
| :--- | :--- |
| [`docs/PRODUCT.md`](docs/PRODUCT.md) | Experience, skills, non-goals |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Agent kernel, pipeline, memory, background execution, modules |
| [`docs/MODELS.md`](docs/MODELS.md) | Model layer, device tiers, candidates, evaluation protocol |
| [`docs/SECURITY.md`](docs/SECURITY.md) | Threat model, policy gate, storage, permissions, privacy |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phases, exit criteria, risks, open questions |
| [`docs/RESEARCH.md`](docs/RESEARCH.md) | What was verified, with sources, and what was not |
| [`docs/superpowers/plans/`](docs/superpowers/plans/2026-10-23-phase0-llm-benchmark.md) | Implementation plans. First: the Phase 0 LLM benchmark |

## Inspiration

Bheem borrows product ideas (approval cards, routines, portable packs, golden-set evals) from [OpenMausBot](https://github.com/milind-soni/OpenMausBot), an Apache-2.0 desktop project. It reuses none of its code: Bheem is native Android and fully on-device. Details in [`docs/RESEARCH.md`](docs/RESEARCH.md).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`AGENTS.md`](AGENTS.md). The easiest high-value contribution once code lands is a bank or UPI notification template pack.

## License

Apache License 2.0. See [`LICENSE`](LICENSE). Model weights keep their own licenses; see [`docs/MODELS.md`](docs/MODELS.md).
