# Phase 0 성능 기준선 — 2026-10-01

기준 커밋: `2499c68` (`main`)

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

## 해석 범위

이 수치는 리팩터링 전 계산 코드의 재현 가능한 기준선이다. 운영 환경의 DB 조회, HTTP,
yfinance/Finnhub, 인증, JSON 직렬화 시간은 포함하지 않는다. 따라서 계획서의
`screener.duration`, `backtest.duration`, `portfolio.duration`, `report.duration` 엔드포인트
p95를 대신하지 않으며, 해당 값은 동일한 운영 데이터와 인증된 요청을 사용하는 별도
부하 측정에서 기록해야 한다.
