# Scorecard: lfm2.5-1.2b-int4gpu

Date 2026-11-03. Device label: scorecard-20261103. Prompt version: proposer-v3.8-line. Runtime: not in the registry. Licence (from the registry): not in the registry; read the licence text before any use.
Every number here is measured from saved run files on the device above. Held-out rows are aggregates only; no held-out case is listed.
| Set | Backend | Prompt | Device | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |
| :-- | :-- | :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |
| assistant-dev | GPU | proposer-v3.8-line | scorecard-20261103 | 100 | 59.0% | 45.0% | 41.0% | 85.0% | 21344 | 2190 | 2208 | 11.8 | 1339 | 1 | n/a | yes |
| english-full | GPU | proposer-v3.8-line | scorecard-20261103 | 101 | 63.4% | n/a | 16.8% | 96.0% | 7508 | 2178 | 3863 | 12.0 | 1247 | 3 | 72.5% | yes |
| held-out | GPU | proposer-v3.8-line | scorecard-20261103 | 30 | 50.0% | 43.3% | 30.0% | 90.0% | 8051 | 2176 | 2226 | 11.8 | 1242 | 0 | n/a | yes |

## Swap rule: lfm2.5-1.2b-int4gpu against gemma-4-e2b

| Set | Backend | Check | Candidate | Primary | Result |
| :-- | :-- | :-- | --: | --: | :-- |
| assistant-dev | CPU | measured | not measured | measured | FAIL |
| assistant-dev | GPU | comparable | complete true/true, cases 100/100, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| assistant-dev | GPU | action | 59.0% | 97.0% | FAIL |
| assistant-dev | GPU | model alone | 45.0% | 92.0% | FAIL |
| assistant-dev | GPU | args | 41.0% | 97.0% | FAIL |
| assistant-dev | GPU | JSON validity | 85.0% | 100.0% | FAIL |
| assistant-dev | GPU | first token p95 | 2208 ms | 1066 ms | FAIL |
| held-out | CPU | measured | not measured | measured | FAIL |
| held-out | GPU | comparable | complete true/true, cases 30/30, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| held-out | GPU | action | 50.0% | 100.0% | FAIL |
| held-out | GPU | model alone | 43.3% | 93.3% | FAIL |
| held-out | GPU | args | 30.0% | 100.0% | FAIL |
| held-out | GPU | JSON validity | 90.0% | 96.7% | FAIL |
| held-out | GPU | first token p95 | 2226 ms | 1070 ms | FAIL |

The candidate does not meet the relative rule on the rows marked FAIL. A go needs a written reason naming each row it loses.
