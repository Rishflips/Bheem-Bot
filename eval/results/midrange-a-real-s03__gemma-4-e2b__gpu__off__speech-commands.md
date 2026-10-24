# Speech-to-text results

Device: midrange-a-real-s03 (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 5018 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma-audio-file+commands | 34 | 0 | 6.1% | 10.9% | 0.0% | 17.9% | 7.7% | 94.1% | 88.2% | 1463 / 1854 | 2566 / 3668 | 0.57 |

## gemma-audio-file+commands

| Condition | WER | Reference words |
| --- | --- | --- |
| far | 13.1% | 99 |
| quiet | 3.4% | 88 |
| noisy | 1.1% | 91 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S03 | 6.1% | 278 |
