# Scorecard: lfm2.5-1.2b-int8

Date 2026-11-03. Device label: midrange-a-l2. Prompt version: proposer-v3.7-line. Runtime: not in the registry. Licence (from the registry): not in the registry; read the licence text before any use.
Every number here is measured from saved run files on the device above. Held-out rows are aggregates only; no held-out case is listed.

| Set | Backend | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |
| :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |
| assistant-dev | CPU | 38 | 68.4% | 34.2% | 60.5% | 78.9% | 5425 | 2545 | 2938 | 8.4 | 1839 | 1 | n/a | yes |

## Swap rule: lfm2.5-1.2b-int8 against gemma-4-e2b

| Set | Backend | Check | Candidate | Primary | Result |
| :-- | :-- | :-- | --: | --: | :-- |
| assistant-dev | CPU | action | 68.4% | 97.0% | FAIL |
| assistant-dev | CPU | model alone | 34.2% | 93.0% | FAIL |
| assistant-dev | CPU | args | 60.5% | 97.0% | FAIL |
| assistant-dev | CPU | first token p95 | 2938 ms | 2060 ms | FAIL |
| assistant-dev | GPU | measured | not measured | measured | FAIL |
| english-full | CPU | measured | not measured | measured | FAIL |
| english-full | GPU | measured | not measured | measured | FAIL |
| held-out | CPU | measured | not measured | measured | FAIL |
| held-out | GPU | measured | not measured | measured | FAIL |

The candidate does not meet the relative rule on the rows marked FAIL. A go needs a written reason naming each row it loses.
