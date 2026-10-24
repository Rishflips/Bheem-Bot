# Speech-to-text results

Device: midrange-a-speech-smoke (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 3839 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma-audio-file+names | 12 | 0 | 1.1% | 0.0% | 0.0% | 0.0% | 0.0% | 91.7% | 91.7% | 1447 / 2180 | 2515 / 3137 | 0.71 |

## gemma-audio-file+names

| Condition | WER | Reference words |
| --- | --- | --- |
| quiet | 0.0% | 40 |
| noisy | 3.7% | 27 |
| far | 0.0% | 21 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S01 | 1.1% | 88 |
