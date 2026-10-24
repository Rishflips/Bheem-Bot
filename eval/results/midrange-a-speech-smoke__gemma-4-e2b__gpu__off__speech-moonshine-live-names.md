# Speech-to-text results

Device: midrange-a-speech-smoke (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 664 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

Transcribers whose names end in -live use a live feed: the audio is given in small chunks at the speed of speech. First chunk is the time to process the last chunk and finish; total is the time from the end of the audio to the final text. The real-time factor does not apply to them.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| moonshine-small-streaming-en+names-live | 12 | 0 | 1.1% | 4.3% | 0.0% | 10.0% | 0.0% | 100.0% | 91.7% | 5 / 926 | 254 / 1330 | n/a |

## moonshine-small-streaming-en+names-live

| Condition | WER | Reference words |
| --- | --- | --- |
| quiet | 0.0% | 40 |
| noisy | 3.7% | 27 |
| far | 0.0% | 21 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S01 | 1.1% | 88 |
