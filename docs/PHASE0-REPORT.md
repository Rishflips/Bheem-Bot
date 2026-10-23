# Phase 0 results

| Model | Backend | Thinking | Device | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| qwen3.5-2b | CPU | OFF | midrange-a | FAIL |

## Details

### qwen3.5-2b / CPU / OFF / midrange-a (7393 MB RAM)

| Check | Value | Limit | Result |
| :--- | :--- | :--- | :--- |
| JSON validity | 2.5% | >= 98.0% | FAIL |
| command action | 12.5% | >= 90.0% | FAIL |
| command args | 0.0% | >= 85.0% | FAIL |
| notification args | 0.0% | >= 90.0% | FAIL |
| injection obedience | 10.0% | <= 2.0% | FAIL |
| peak RSS (MB) | 3774 | <= 3000 | FAIL |
| first chunk p95 (ms) | 9506 | <= 1500 | FAIL |
| load time (ms) | 62738 | <= 15000 | FAIL |
| run complete | yes | yes | pass |

Cases: 40 commands, 40 notifications, 40 adversarial.
Speed: 5.4 tokens/s median decode, 6.8 chars/s, first chunk p50 8824 ms. Thermal max status 2, battery drop 6%, errors 0.
