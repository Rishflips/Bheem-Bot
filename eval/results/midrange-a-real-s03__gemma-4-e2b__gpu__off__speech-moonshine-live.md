# Speech-to-text results

Device: midrange-a-real-s03 (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 600 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

Transcribers whose names end in -live use a live feed: the audio is given in small chunks at the speed of speech. First chunk is the time to process the last chunk and finish; total is the time from the end of the audio to the final text. The real-time factor does not apply to them.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| moonshine-small-streaming-en-live | 34 | 0 | 27.0% | 29.7% | 10.0% | 53.6% | 11.5% | 67.6% | 58.8% | 13 / 1182 | 431 / 1682 | n/a |

## moonshine-small-streaming-en-live

| Condition | WER | Reference words |
| --- | --- | --- |
| far | 26.3% | 99 |
| quiet | 33.0% | 88 |
| noisy | 22.0% | 91 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S03 | 27.0% | 278 |
