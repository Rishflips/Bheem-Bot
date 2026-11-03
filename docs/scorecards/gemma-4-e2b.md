# Scorecard: gemma-4-e2b

Date 2026-11-03. Device label: scorecard-20261103. Prompt version: proposer-v3.8-line. Runtime: litert-lm. Licence (from the registry): Apache-2.0.
Every number here is measured from saved run files on the device above. Held-out rows are aggregates only; no held-out case is listed.
| Set | Backend | Prompt | Device | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |
| :-- | :-- | :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |
| assistant-dev | CPU | proposer-v3.8-line | scorecard-20261103 | 100 | 97.0% | 93.0% | 97.0% | 100.0% | 6631 | 2065 | 2117 | 11.4 | 1723 | 1 | n/a | yes |
| assistant-dev | GPU | proposer-v3.8-line | scorecard-20261103 | 100 | 97.0% | 92.0% | 97.0% | 100.0% | 40481 | 1024 | 1083 | 9.1 | 2203 | 1 | n/a | yes |
| english-full | CPU | proposer-v3.8-line | scorecard-20261103 | 101 | 84.2% | n/a | 84.2% | 100.0% | 521 | 2081 | 3030 | 11.2 | 1671 | 3 | 2.5% | yes |
| english-full | GPU | proposer-v3.8-line | scorecard-20261103 | 101 | 85.1% | n/a | 85.1% | 100.0% | 42437 | 1021 | 1415 | 9.2 | 2142 | 1 | 12.5% | yes |
| held-out | CPU | proposer-v3.8-line | scorecard-20261103 | 30 | 100.0% | 93.3% | 100.0% | 96.7% | 1020 | 2081 | 2114 | 11.1 | 1572 | 0 | n/a | yes |
| held-out | GPU | proposer-v3.8-line | scorecard-20261103 | 30 | 100.0% | 93.3% | 100.0% | 96.7% | 4843 | 1025 | 1082 | 9.1 | 2100 | 0 | n/a | yes |

## Swap rule: gemma-4-e2b against gemma-4-e2b

| Set | Backend | Check | Candidate | Primary | Result |
| :-- | :-- | :-- | --: | --: | :-- |
| assistant-dev | CPU | comparable | complete true/true, cases 100/100, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| assistant-dev | CPU | action | 97.0% | 97.0% | pass |
| assistant-dev | CPU | model alone | 93.0% | 93.0% | pass |
| assistant-dev | CPU | args | 97.0% | 97.0% | pass |
| assistant-dev | CPU | JSON validity | 100.0% | 100.0% | pass |
| assistant-dev | CPU | first token p95 | 2117 ms | 2060 ms | pass |
| assistant-dev | GPU | comparable | complete true/true, cases 100/100, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| assistant-dev | GPU | action | 97.0% | 97.0% | pass |
| assistant-dev | GPU | model alone | 92.0% | 92.0% | pass |
| assistant-dev | GPU | args | 97.0% | 97.0% | pass |
| assistant-dev | GPU | JSON validity | 100.0% | 100.0% | pass |
| assistant-dev | GPU | first token p95 | 1083 ms | 1066 ms | pass |
| held-out | CPU | comparable | complete true/true, cases 30/30, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| held-out | CPU | action | 100.0% | 100.0% | pass |
| held-out | CPU | model alone | 93.3% | 93.3% | pass |
| held-out | CPU | args | 100.0% | 100.0% | pass |
| held-out | CPU | JSON validity | 96.7% | 96.7% | pass |
| held-out | CPU | first token p95 | 2114 ms | 2072 ms | pass |
| held-out | GPU | comparable | complete true/true, cases 30/30, prompt proposer-v3.8-line/proposer-v3.8-line | same cases, same prompt, complete | pass |
| held-out | GPU | action | 100.0% | 100.0% | pass |
| held-out | GPU | model alone | 93.3% | 93.3% | pass |
| held-out | GPU | args | 100.0% | 100.0% | pass |
| held-out | GPU | JSON validity | 96.7% | 96.7% | pass |
| held-out | GPU | first token p95 | 1082 ms | 1070 ms | pass |

The candidate meets the relative rule.
