# Speech-to-text results

Device: midrange-a-real-s03 (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 4958 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma-audio-file+names+commands | 34 | 0 | 4.7% | 4.7% | 0.0% | 3.6% | 7.7% | 94.1% | 94.1% | 1482 / 1868 | 2694 / 3628 | 0.60 |

## gemma-audio-file+names+commands

| Condition | WER | Reference words |
| --- | --- | --- |
| far | 13.1% | 99 |
| quiet | 0.0% | 88 |
| noisy | 0.0% | 91 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S03 | 4.7% | 278 |
