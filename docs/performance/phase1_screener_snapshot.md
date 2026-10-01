# Phase 1 — Screener Snapshot

## 1. 기존 병목

사용자 요청마다 MySQL 가격 이력 로딩, 503종목 pandas 계산, yfinance/Finnhub enrichment를
동기 실행했다.

| Metric | p95 |
|---|---:|
| DB load | 16.245s |
| CPU | 5.349s |
| Enrichment | 6.035s |
| Total | 27.216s |

## 2. 변경 전후 구조

변경 전: `Frontend → Spring → Collector → MySQL/계산/외부 API → 응답`

변경 후:

```text
Collector scheduled/manual batch
  → price_history 1회 로드
  → 고정 technical metric 계산
  → fundamentals/news enrichment
  → 새 run bulk insert
  → COMPLETED publish

Frontend → Spring POST /api/screener/search → MySQL 최신 COMPLETED run → 응답
```

## 3. DB schema

- `screening_snapshot_runs`: `RUNNING/COMPLETED/FAILED`, 시작·완료 시각, snapshot 날짜, 건수, 오류
- `stock_screening_snapshots`: run/symbol당 한 행, 3/6/12개월 momentum, RSI 14,
  20일 변동성, SMA 50/100/200, fundamentals, news
- `(snapshot_run_id, symbol_id)` unique
- `(snapshot_run_id, momentum_6m)` 조회용 index

## 4. Batch lifecycle와 복구

계산 시작 전에 `RUNNING` run을 만든다. 모든 행은 `executemany`로 한 transaction에 저장하고
같은 transaction에서 run을 `COMPLETED`로 전환한다. 어떤 단계든 실패하면 새 run만 `FAILED`가
되며 기존 `COMPLETED` run과 그 snapshot은 삭제하지 않는다. 조회는 항상 id가 가장 큰
`COMPLETED` run만 사용한다.

기본 주기는 30분이며 `SCREENING_SNAPSHOT_INTERVAL_SECONDS`로 변경한다. 프로세스 내부 lock으로
동시 batch를 거부한다.

수동 실행:

```bash
curl -X POST http://127.0.0.1:8001/screening-snapshot/run
```

## 5. API

```http
POST /api/screener/search
Content-Type: application/json
```

```json
{
  "momentumPeriod": "SIX_MONTHS",
  "minMomentumPct": 10,
  "maxRsi": 70,
  "aboveSma200": true,
  "sort": { "field": "MOMENTUM_6M", "direction": "DESC" },
  "page": 0,
  "size": 20
}
```

정렬 필드는 enum을 SQL column allowlist로 변환한다. 클라이언트 문자열을 SQL에 직접 넣지
않는다. `GET /api/screener/momentum`은 deprecated 호환 adapter로 유지하지만 Collector를
호출하지 않는다.

## 6. 성능 계측

Batch 결과의 `timingsMs`:

- `snapshot.batch.db_load`
- `snapshot.batch.cpu`
- `snapshot.batch.enrichment`
- `snapshot.batch.persist`
- `snapshot.batch.total`

Spring Micrometer:

- `screener.snapshot.query`
- `screener.snapshot.total`

## 7. 테스트 결과

- Collector: `71 passed, 3 deselected`
- Spring ScreenerService: `3 passed`
- Spring 전체: `79 tests, 28 failed` — 로컬 Redis `localhost:6379` 미실행으로 context/Redis 테스트 실패
- Frontend ESLint: 통과
- MySQL/Flyway V28 및 Raspberry Pi API 성능: 아직 미검증

After 측정 전에는 값을 기록하지 않는다.

| Metric | Before p95 | After p50 | After p95 | After p99 |
|---|---:|---:|---:|---:|
| Screener request total | 27.216s | 미측정 | 미측정 | 미측정 |

## 8. Raspberry Pi migration/deploy 및 재측정

```bash
docker compose build collector backend
docker compose up -d mysql
docker compose up -d collector backend
curl -X POST http://127.0.0.1:8001/screening-snapshot/run
```

Flyway V28 적용과 batch `COMPLETED`를 확인한 뒤, 인증 토큰을 사용해 동일 검색 요청을 최소
20회 실행하고 p50/p95/p99를 기록한다. 동시에 다음 명령으로 자원을 표본화한다.

```bash
docker stats --no-stream marketboard-backend marketboard-mysql
```

목표는 p50 `<200ms`, p95 `<500ms`, p99 `<1s`다. 운영 재측정 전에는 달성으로 표시하지 않는다.
