# Scorecard: lfm2.5-1.2b-int4gpu

Date 2026-11-03. Device label: midrange-a-l2. Prompt version: proposer-v3.7-line. Runtime: not in the registry. Licence (from the registry): not in the registry; read the licence text before any use.
Every number here is measured from saved run files on the device above. Held-out rows are aggregates only; no held-out case is listed.
| Set | Backend | Prompt | Device | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |
| :-- | :-- | :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |
| assistant-dev | GPU | proposer-v3.7-line | midrange-a-l2 | 38 | 68.4% | 42.1% | 52.6% | 78.9% | 19090 | 2173 | 2209 | 11.9 | 1302 | 0 | n/a | yes |

## Swap rule: lfm2.5-1.2b-int4gpu against gemma-4-e2b

| Set | Backend | Check | Candidate | Primary | Result |
| :-- | :-- | :-- | --: | --: | :-- |
| assistant-dev | CPU | measured | not measured | measured | FAIL |
| assistant-dev | GPU | comparable | complete true/true, cases 38/100, prompt proposer-v3.7-line/proposer-v3.8-line | same cases, same prompt, complete | FAIL |
| assistant-dev | GPU | action | 68.4% | 97.0% | FAIL |
| assistant-dev | GPU | model alone | 42.1% | 92.0% | FAIL |
| assistant-dev | GPU | args | 52.6% | 97.0% | FAIL |
| assistant-dev | GPU | JSON validity | 78.9% | 100.0% | FAIL |
| assistant-dev | GPU | first token p95 | 2209 ms | 1066 ms | FAIL |
| held-out | CPU | measured | not measured | measured | FAIL |
| held-out | GPU | measured | not measured | measured | FAIL |

The candidate does not meet the relative rule on the rows marked FAIL. A go needs a written reason naming each row it loses.
