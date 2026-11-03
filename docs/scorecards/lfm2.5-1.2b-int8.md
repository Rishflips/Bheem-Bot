# Scorecard: lfm2.5-1.2b-int8

Date 2026-11-03. Device label: scorecard-20261103. Prompt version: proposer-v3.8-line. Runtime: not in the registry. Licence (from the registry): not in the registry; read the licence text before any use.
Every number here is measured from saved run files on the device above. Held-out rows are aggregates only; no held-out case is listed.
| Set | Backend | Prompt | Device | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |
| :-- | :-- | :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |
| assistant-dev | CPU | proposer-v3.8-line | scorecard-20261103 | 100 | 61.0% | 41.0% | 47.0% | 89.0% | 4767 | 2595 | 2808 | 8.4 | 1854 | 2 | n/a | yes |
| english-full | CPU | proposer-v3.8-line | scorecard-20261103 | 101 | 56.4% | n/a | 40.6% | 98.0% | 398 | 2806 | 3103 | 8.4 | 1852 | 5 | 72.5% | yes |
| held-out | CPU | proposer-v3.8-line | scorecard-20261103 | 30 | 53.3% | 46.7% | 30.0% | 93.3% | 824 | 2564 | 2986 | 8.4 | 1705 | 0 | n/a | yes |

## Swap rule: lfm2.5-1.2b-int8 against gemma-4-e2b

| Set | Backend | Check | Candidate | Primary | Result |
| :-- | :-- | :-- | --: | --: | :-- |
| assistant-dev | CPU | comparable | complete true/true, cases 100/100, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| assistant-dev | CPU | action | 61.0% | 97.0% | FAIL |
| assistant-dev | CPU | model alone | 41.0% | 93.0% | FAIL |
| assistant-dev | CPU | args | 47.0% | 97.0% | FAIL |
| assistant-dev | CPU | JSON validity | 89.0% | 100.0% | FAIL |
| assistant-dev | CPU | first token p95 | 2808 ms | 2060 ms | FAIL |
| assistant-dev | GPU | measured | not measured | measured | FAIL |
| held-out | CPU | comparable | complete true/true, cases 30/30, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| held-out | CPU | action | 53.3% | 100.0% | FAIL |
| held-out | CPU | model alone | 46.7% | 93.3% | FAIL |
| held-out | CPU | args | 30.0% | 100.0% | FAIL |
| held-out | CPU | JSON validity | 93.3% | 96.7% | FAIL |
| held-out | CPU | first token p95 | 2986 ms | 2072 ms | FAIL |
| held-out | GPU | measured | not measured | measured | FAIL |

The candidate does not meet the relative rule on the rows marked FAIL. A go needs a written reason naming each row it loses.
