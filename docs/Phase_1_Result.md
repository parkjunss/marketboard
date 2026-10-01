## Phase 1 Result

Raspberry Pi 운영 환경에서 동일 Screener 사용자 요청 경로를 측정했다.

### Before — Request-time computation

| Metric | p95 |
|---|---:|
| DB load | 16.245 s |
| CPU | 5.349 s |
| Enrichment | 6.035 s |
| Total | 27.216 s |

### After — Snapshot query

50 requests after 5 warm-up requests.

| Metric | Result |
|---|---:|
| min | 28.944 ms |
| avg | 45.667 ms |
| p50 | 46.194 ms |
| p95 | 59.052 ms |
| p99 | 70.220 ms |
| max | 78.089 ms |

p95 latency:

27.216 s → 59.052 ms

Approximately 461x faster and 99.78% lower p95 latency.

The expensive DB history loading, whole-universe indicator calculation,
and external enrichment were not removed. They were moved from the
user request path to the background snapshot batch.