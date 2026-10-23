# Phase 0 diagnostics

Evidence for one finding, measured on a Moto g86 power 5G (MediaTek MT6878, 7.4 GB RAM, Android 16) with LiteRT-LM 0.18.1 on CPU. These are not benchmark results. Every file is a run of `scripts/bench-device.ps1 -RepeatFirst N`, which sends the **same** command N times to one loaded engine with greedy decoding (temperature 0, top-k 1), so every answer should be identical.

| File | What it shows |
| :--- | :--- |
| `qwen3.5-2b-same-command-x15-drifts.json` | Qwen3.5-2B (hybrid model) gives 9 different answers to 15 identical requests. The first 6 are valid reminders; later ones become wrong actions, loops, or echoed input. |
| `qwen3.5-2b-fresh-engine-x4-stable.json` | Same model, but a new engine is built for every request: 4 identical answers. So the drift is state carried between conversations inside one engine. |
| `qwen3-1.7b-same-command-x15-stable.json` | Qwen3-1.7B (plain attention) gives 15 identical answers on one engine. |

Settings ruled out (each reproduced the identical drift): thinking mode on or off, the benchmark flag, the engine cache directory, neutral repetition penalties, speculative decoding off, startup prefill of the system prompt off, and the lower-level Session API instead of Conversation.

Leading explanation (not proven): Qwen3.5 mixes 18 Gated DeltaNet (linear-attention) layers with 6 attention layers, and the runtime does not reset the recurrent state between conversations. See [`docs/MODELS.md`](../../docs/MODELS.md).
