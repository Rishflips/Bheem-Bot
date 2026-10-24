# Speech-to-text results

Device: midrange-a-real-s03 (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 5252 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma-audio-file | 34 | 0 | 9.7% | 14.1% | 10.0% | 21.4% | 7.7% | 82.4% | 73.5% | 1478 / 1601 | 2674 / 4205 | 0.61 |

## gemma-audio-file

| Condition | WER | Reference words |
| --- | --- | --- |
| far | 16.2% | 99 |
| quiet | 10.2% | 88 |
| noisy | 2.2% | 91 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S03 | 9.7% | 278 |
