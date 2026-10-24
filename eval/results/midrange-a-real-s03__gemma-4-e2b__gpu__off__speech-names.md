# Speech-to-text results

Device: midrange-a-real-s03 (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 4317 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma-audio-file+names | 34 | 0 | 9.0% | 4.7% | 0.0% | 3.6% | 7.7% | 79.4% | 79.4% | 1461 / 1853 | 2572 / 3622 | 0.58 |

## gemma-audio-file+names

| Condition | WER | Reference words |
| --- | --- | --- |
| far | 17.2% | 99 |
| quiet | 5.7% | 88 |
| noisy | 3.3% | 91 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S03 | 9.0% | 278 |
