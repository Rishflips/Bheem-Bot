# Speech-to-text results

Device: midrange-a-real-s03 (moto g86 power 5G, MT6878, 7393 MB RAM, SDK 36).
Model load: 590 ms. Run complete: yes.

The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.

Transcribers whose names end in -live use a live feed: the audio is given in small chunks at the speed of speech. First chunk is the time to process the last chunk and finish; total is the time from the end of the audio to the final text. The real-time factor does not apply to them.

| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| moonshine-small-streaming-en+names-live | 34 | 0 | 22.3% | 17.2% | 10.0% | 25.0% | 11.5% | 70.6% | 61.8% | 9 / 1172 | 424 / 1414 | n/a |

## moonshine-small-streaming-en+names-live

| Condition | WER | Reference words |
| --- | --- | --- |
| far | 21.2% | 99 |
| quiet | 27.3% | 88 |
| noisy | 18.7% | 91 |

| Speaker | WER | Reference words |
| --- | --- | --- |
| S03 | 22.3% | 278 |
