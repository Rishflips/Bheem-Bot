# Scorecard: gemma-4-e2b

Date 2026-11-03. Device label: v38, midrange-a-en-all-v35-line-cpu, midrange-a-en-all-v35-line-gpu. Prompt version: proposer-v3.8-line, proposer-v3.5-line. Runtime: litert-lm. Licence (from the registry): Apache-2.0.
Every number here is measured from saved run files on the device above. Held-out rows are aggregates only; no held-out case is listed.

| Set | Backend | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |
| :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |
| assistant-dev | CPU | 100 | 97.0% | 93.0% | 97.0% | 100.0% | 5716 | 2042 | 2060 | 11.5 | 1802 | 0 | n/a | yes |
| assistant-dev | GPU | 100 | 97.0% | 92.0% | 97.0% | 100.0% | 40920 | 1009 | 1066 | 9.2 | 2262 | 0 | n/a | yes |
| english-full | CPU | 101 | 99.0% | n/a | 97.0% | 100.0% | 1057 | 2480 | 3571 | 9.9 | 1550 | 2 | 2.5% | yes |
| english-full | GPU | 101 | 98.0% | n/a | 98.0% | 100.0% | 5090 | 1108 | 1583 | 8.5 | 2040 | 2 | 12.5% | yes |
| held-out | CPU | 30 | 100.0% | 93.3% | 100.0% | 96.7% | 565 | 2033 | 2072 | 11.5 | 1593 | 0 | n/a | yes |
| held-out | GPU | 30 | 100.0% | 93.3% | 100.0% | 96.7% | 4670 | 1016 | 1070 | 9.3 | 2078 | 0 | n/a | yes |
