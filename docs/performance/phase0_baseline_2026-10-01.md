# Phase 0 성능 기준선 — 2026-10-01

기준 커밋: `5ec4ade` (`main`)

## 측정 조건

- 명령: `cd collector && uv run pytest tests/test_phase0_performance.py -m performance -s`
- 워밍업 3회 후 20회 측정
- Windows 11 (`10.0.26200`), AMD64
- Python 3.12.3, pandas 3.0.3
- `time.perf_counter_ns()`로 각 공개 계산 경로의 전체 실행 시간 측정
- DB 및 외부 API는 고정 데이터로 대체하여 CPU 계산 성능만 비교

| 측정 대상 | 고정 워크로드 | p50 | p95 | p99 | 평균 |
|---|---:|---:|---:|---:|---:|
| `collector.screener.cpu` | 503종목, 331 영업일, 상위 10종목 | 1058.659 ms | 1117.567 ms | 1143.662 ms | 1063.494 ms |
| `collector.backtest.cpu` | 10종목 + SPY, 2520 영업일, BUY_AND_HOLD | 12.370 ms | 13.827 ms | 13.979 ms | 12.441 ms |

테스트 결과: `2 passed in 28.95s`

## Raspberry Pi 고정 입력 측정

- 실행 위치: `marketboard-collector` 컨테이너
- Linux `6.14.0-1019-raspi`, aarch64
- Python 3.12.12, pandas 3.0.3
- 로컬 측정과 동일한 입력, 워밍업 3회, 측정 20회

| 측정 대상 | p50 | p95 | p99 | 평균 |
|---|---:|---:|---:|---:|
| `screener.db_load` | 0.003 ms | 0.003 ms | 0.004 ms | 0.003 ms |
| `screener.cpu` | 4991.226 ms | 5810.594 ms | 6818.505 ms | 5159.650 ms |
| `screener.enrichment` | 2.686 ms | 3.285 ms | 3.939 ms | 2.717 ms |
| `screener.total` | 4993.777 ms | 5813.737 ms | 6821.363 ms | 5162.397 ms |
| `collector.backtest.cpu` | 59.343 ms | 69.461 ms | 74.319 ms | 61.089 ms |

DB와 enrichment는 테스트 더블이므로 이 표에서는 CPU 계산 비교만 유효하다.

## Raspberry Pi 실제 DB·외부 API 측정

- 실행 위치: 실행 중인 `marketboard-collector`와 동일한 컨테이너의 격리된 `/tmp` 코드
- 실제 MySQL `price_history` 조회 및 실제 yfinance/Finnhub enrichment 포함
- 상위 10종목, 워밍업 3회 후 20회 측정
- 테스트 결과: `1 passed in 604.98s`

| 측정 대상 | p50 | p95 | p99 | 평균 |
|---|---:|---:|---:|---:|
| `screener.db_load` | 14937.797 ms | 16244.967 ms | 16381.273 ms | 15072.188 ms |
| `screener.cpu` | 4869.084 ms | 5349.081 ms | 6168.818 ms | 4960.238 ms |
| `screener.enrichment` | 5775.979 ms | 6035.172 ms | 6563.577 ms | 5791.851 ms |
| `screener.total` | 25796.448 ms | 27215.570 ms | 27216.391 ms | 25824.319 ms |

### Docker stats

테스트 동안 2초 간격으로 196회 수집했다. Docker CPU 100%는 CPU 코어 1개 사용량이다.

| 자원 | p50 | p95 | 최대 | 평균 |
|---|---:|---:|---:|---:|
| CPU | 102.50% | 122.20% | 259.83% | 92.12% |
| 메모리 | 400.0 MiB | 426.2 MiB | 436.9 MiB | 399.07 MiB |

## 병목 결론

실제 p95 `27.216초` 중 DB 로드가 `16.245초`, enrichment가 `6.035초`, CPU 계산이
`5.349초`다. 단일 미세 최적화보다 요청 경로에서 세 구간 전체를 제거하는 Snapshot
구조가 우선이다.

## 해석 범위

고정 입력 수치는 계산 코드의 재현 가능한 기준선이고, 실제 DB·외부 API 수치는 현재
운영 데이터 상태의 baseline이다. 두 측정 모두 Spring HTTP, 인증, JSON 직렬화 시간은
포함하지 않으므로 `screener.total`은 Collector 내부 총시간을 뜻한다.
