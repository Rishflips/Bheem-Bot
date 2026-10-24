# Speech-to-text results

Device: midrange-a-speech-smoke (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 5307 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma-audio-file | 12 | 0 | 3.4% | 8.7% | 0.0% | 20.0% | 0.0% | 91.7% | 83.3% | 1441 / 2244 | 2751 / 3298 | 0.72 |
| gemma-audio-wav_bytes | 12 | 0 | 3.4% | 8.7% | 0.0% | 20.0% | 0.0% | 91.7% | 83.3% | 1462 / 1568 | 2646 / 2906 | 0.71 |
| gemma-audio-pcm_bytes | 12 | 12 | n/a | n/a | n/a | n/a | n/a | 0.0% | 0.0% | 0 / 0 | 0 / 0 | n/a |

## gemma-audio-file

| Condition | WER | Reference words |
| --- | --- | --- |
| quiet | 0.0% | 40 |
| noisy | 7.4% | 27 |
| far | 4.8% | 21 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S01 | 3.4% | 88 |

## gemma-audio-wav_bytes

| Condition | WER | Reference words |
| --- | --- | --- |
| quiet | 0.0% | 40 |
| noisy | 7.4% | 27 |
| far | 4.8% | 21 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S01 | 3.4% | 88 |

## gemma-audio-pcm_bytes

| Condition | WER | Reference words |
| --- | --- | --- |

| Speaker | WER | Reference words |
| --- | --- | --- |
