# MarketBoard 리팩터링 계획

> 대상 레포지토리: `parkjunss/marketboard`
> 기준 브랜치: `main`
> 목적: AI Agent Workflow 도입 전에 리포트, 포트폴리오, 백테스트, 종목 스크리너의 기능 정확성·성능·UX·구조를 우선 개선한다.

---

## 1. 리팩터링 목표

MarketBoard의 핵심 투자 기능은 현재 동작 자체는 가능하지만, 일부 기능에서 다음 문제가 확인된다.

- 스크리너 실행 시간이 길다.
- 백테스트가 요청 스레드에서 동기 실행된다.
- 포트폴리오 초기 로딩 과정에 중복 조회가 있다.
- 포트폴리오가 거래 이력보다 현재 포지션 스냅샷 중심으로 설계돼 있다.
- 리포트 생성 시 여러 독립 데이터 조회를 순차 실행한다.
- 리포트 생성에서 idempotency 체크 이전에 무거운 데이터 수집이 발생한다.
- 주요 프론트 페이지가 하나의 `page.tsx`에 과도하게 많은 책임을 가진다.
- 기능별 UI 구조가 달라 향후 AI Research Workspace로 통합하기 어렵다.

최종 목표는 다음과 같다.

```text
Market Data / Financial / Portfolio / Backtest / Screener
                    ↓
          빠르고 안정적인 Domain API
                    ↓
               AI Tool Layer
                    ↓
          AI Research Agent Workflow
```

AI를 먼저 추가하지 않고, Agent가 호출할 Tool 자체를 먼저 정상화한다.

---

# 2. 전체 우선순위

## P0

1. Screener 성능 및 구조 재설계
2. Portfolio 조회 구조 및 데이터 모델 개선
3. Backtest 비동기화 및 재현성 개선
4. Report 병렬화 및 생성 흐름 개선

## P1

5. 공통 프론트 구조 리팩터링
6. 공통 로딩/에러/빈 상태 UX 통일
7. 성능 메트릭 수집 및 Grafana 대시보드 추가

## P2

8. AI Tool Layer
9. AI Research Agent
10. 자연어 Screener / Portfolio Analysis / Backtest Orchestration

---

# 3. Phase 0 — 기준 성능 측정

리팩터링 전후를 비교할 수 있도록 먼저 성능 측정을 추가한다.

## Micrometer Metrics

추가 후보:

```text
screener.duration
backtest.duration
report.duration
portfolio.duration

collector.screener.duration
collector.backtest.duration

external.yfinance.duration
external.finnhub.duration
```

권장 태그:

```text
status=success|failure
strategy=BUY_AND_HOLD|SMA_CROSSOVER|...
cache=hit|miss
endpoint=...
```

Grafana에서는 최소 다음 지표를 확인한다.

- p50
- p95
- p99
- 요청 수
- 오류율
- 외부 API 호출 횟수

## 완료 기준

리팩터링 전 실제 수치를 기록한다.

예:

```text
Screener p95: 18.3s
Backtest p95: 2.8s
Portfolio initial load p95: 1.2s
Report generation p95: 3.4s
```

이후 각 Phase 완료 시 동일 조건에서 재측정한다.

---

# 4. Phase 1 — Screener 재설계

## 현재 구조

```text
Frontend
   ↓
GET /api/screener/momentum
   ↓
Spring ScreenerService
   ↓
Collector
   ↓
S&P500 price_history 조회
   ↓
약 500종목 Python 계산
   ↓
후보 선정
   ↓
최대 20종목 live enrichment
   ├─ yfinance
   └─ News / Sentiment
   ↓
Result
```

현재 프론트 코드에서도 스크리너를 약 `15~30초`가 걸리는 기능으로 전제하고 있다.

### 현재 문제

- 사용자 요청 시점마다 전체 유니버스 계산
- 사용자 요청 시점마다 일부 외부 API 호출
- Spring `ScreenerService`가 collector pass-through 역할에 가까움
- 동일 조건 재조회 시에도 결과 재사용이 없음
- 필터보다 특정 모멘텀 전략 실행에 강하게 결합됨
- 향후 자연어 Screener Tool로 사용하기 어려움

---

## 목표 구조

```text
Scheduled Batch
      ↓
S&P500 Universe
      ↓
Price / Momentum / RSI / SMA / Volatility
      ↓
Fundamental Snapshot
      ↓
MySQL
      ↓
Optional Redis Cache
```

사용자 요청:

```text
Filter Request
     ↓
Spring
     ↓
DB Query
     ↓
Result
```

핵심 원칙:

> 계산은 배치 시점에 하고, 사용자 요청 시점에는 조회만 한다.

---

## 신규 Snapshot 설계 예시

```text
stock_screening_snapshot
────────────────────────────
id
symbol_id
snapshot_date

price

momentum_3m
momentum_6m
momentum_12m

volatility_20d
rsi_14

sma_50
sma_100
sma_200
above_sma_200

market_cap
revenue_ttm
revenue_growth
roe
profit_margin
trailing_pe

news_sentiment
news_count

updated_at
```

추천 Index:

```text
(snapshot_date)
(snapshot_date, momentum_6m)
(snapshot_date, rsi_14)
(snapshot_date, trailing_pe)
(snapshot_date, market_cap)
(snapshot_date, revenue_growth)
```

---

## API 변경 권장

기존:

```http
GET /api/screener/momentum
```

추가:

```http
POST /api/screener/search
```

예:

```json
{
  "universe": "SP500",
  "filters": {
    "momentum6m": {
      "min": 10
    },
    "rsi14": {
      "max": 65
    },
    "marketCap": {
      "min": 10000000000
    },
    "revenueGrowth": {
      "min": 5
    }
  },
  "sort": {
    "field": "momentum6m",
    "direction": "DESC"
  },
  "page": 0,
  "size": 50
}
```

Java DTO 후보:

```text
ScreenerCriteria
ScreenerFilter
ScreenerSort
ScreenerResult
ScreenerCandidate
```

---

## Backend 작업 목록

- [x] `stock_screening_snapshot` Flyway migration
- [x] Snapshot Entity / Repository 추가
- [x] S&P500 batch 계산 로직 추가
- [x] Fundamentals snapshot 저장
- [x] Momentum / RSI / SMA / Volatility 계산 사전 수행
- [x] `ScreenerCriteria` 설계
- [x] 동적 filtering 구현
- [x] pagination
- [x] sorting
- [x] 기존 live screener legacy 처리
- [ ] Redis cache 선택 적용
- [x] Screener 성능 metric 추가

---

## Frontend 재설계

현재 구조:

```text
Template
↓
Form
↓
Form
↓
Run
↓
15~30초 대기
↓
Result
```

목표:

```text
┌─────────────────────────────────────────────┐
│ Stock Screener                 503 stocks   │
├─────────────────────────────────────────────┤
│ Presets                                     │
│ Momentum | Value | Growth | Oversold        │
├──────────────┬──────────────────────────────┤
│ Filters      │ Results                      │
│              │                              │
│ Market       │ Ticker Price PER RSI ...     │
│ Sector       │ NVDA                         │
│ Market Cap   │ MSFT                         │
│ PER          │ ...                          │
│ RSI          │                              │
│ Growth       │                              │
│ Momentum     │                              │
├──────────────┴──────────────────────────────┤
│ Pagination                                  │
└─────────────────────────────────────────────┘
```

### Frontend 작업

- [ ] 좌측 Filter Panel
- [ ] Preset
- [ ] Result Table
- [ ] Column Sort
- [ ] Pagination
- [ ] URL Query Param과 Filter 동기화
- [ ] debounce 기반 자동 조회
- [ ] Skeleton UI
- [ ] Empty State
- [ ] Error State
- [ ] 종목 상세 페이지 바로가기
- [ ] 필터 초기화 버튼

### 목표 성능

```text
p95 < 500ms
```

---

# 5. Phase 2 — Portfolio 재설계

## 현재 데이터 모델

현재는 사실상 다음 구조다.

```text
Portfolio
   ↓
PortfolioPosition
    ├─ quantity
    └─ avgCost
```

즉 거래 이력보다 현재 상태 스냅샷이 Source of Truth다.

이 구조에서는 다음 기능이 불편하다.

- 실현손익
- 매수/매도 이력
- 기간별 수익률
- 평균단가 계산 이력
- 배당
- 수수료
- 세금
- 현금 잔고

---

## 목표 모델

```text
Portfolio
   │
   ├── PortfolioTransaction
   │      ├── symbol
   │      ├── BUY / SELL
   │      ├── quantity
   │      ├── price
   │      ├── fee
   │      └── tradedAt
   │
   └── PortfolioPosition
          └── Projection / Cache
```

핵심 원칙:

```text
Transaction = Source of Truth
Position = Derived State
```

초기 리팩터링에서는 기존 `PortfolioPosition`을 유지하되 projection 역할로 바꿔도 된다.

---

## Portfolio 로딩 문제

현재:

```text
GET /api/portfolios
```

에서 각 포트폴리오마다:

```text
positions 조회
→ quoteService.resolvePrices()
→ summary 생성
```

이 반복된다.

프론트는 이후 선택된 포트폴리오에 대해 다시:

```text
GET /api/portfolios/{id}/positions
```

를 호출한다.

즉 초기 페이지 로드에서 동일 포트폴리오의 position 데이터가 중복 조회될 수 있다.

---

## API 재설계

### 포트폴리오 목록

```http
GET /api/portfolios
```

응답은 summary 중심:

```json
[
  {
    "id": 1,
    "name": "장기투자",
    "positionCount": 8,
    "totalMarketValue": 125000,
    "totalReturnPct": 14.8
  }
]
```

### 포트폴리오 상세

```http
GET /api/portfolios/{id}
```

```json
{
  "summary": {},
  "positions": [],
  "allocation": [],
  "performance": {},
  "recentTransactions": []
}
```

---

## Backend 작업 목록

- [ ] `portfolio_transactions` 테이블 추가
- [ ] `TransactionType` enum
- [ ] BUY / SELL 등록
- [ ] 평균단가 계산 정책 확정
- [ ] Position projection 구현
- [ ] Realized PnL 계산
- [ ] Unrealized PnL 계산
- [ ] Bulk Quote Resolution 유지/확장
- [ ] 포트폴리오 목록 query 단순화
- [ ] `GET /portfolios/{id}` aggregate API
- [ ] Allocation 계산
- [ ] Performance 시계열
- [ ] Transaction 조회 API
- [ ] Portfolio metric 추가

---

## Frontend 구조 분리

현재:

```text
frontend/src/app/(app)/portfolio/page.tsx
약 22KB
```

한 파일이 다음을 모두 담당한다.

- API 호출
- Portfolio CRUD
- Position CRUD
- 수정 상태
- 삭제 상태
- optimistic locking UI
- form
- summary
- table
- price source
- error handling

권장:

```text
portfolio/
├── page.tsx
├── components/
│   ├── PortfolioHeader.tsx
│   ├── PortfolioSelector.tsx
│   ├── PortfolioSummaryCards.tsx
│   ├── PortfolioHoldingsTable.tsx
│   ├── PortfolioAllocationChart.tsx
│   ├── PortfolioPerformanceChart.tsx
│   ├── PortfolioTransactionForm.tsx
│   ├── PortfolioTransactionTable.tsx
│   └── PositionEditDialog.tsx
│
└── hooks/
    ├── usePortfolios.ts
    ├── usePortfolioDetail.ts
    └── usePortfolioMutations.ts
```

---

## Portfolio UI 목표

```text
┌───────────────────────────────────────────────┐
│ Portfolio                                     │
├───────────────┬──────────────┬────────────────┤
│ Total Value   │ Day P/L      │ Total Return   │
│ $128,420      │ +1.42%       │ +17.8%         │
├───────────────┴──────────────┴────────────────┤
│ Portfolio Value Chart                         │
├───────────────────────────┬───────────────────┤
│ Holdings                  │ Allocation        │
│ NVDA                      │ Tech 46%          │
│ MSFT                      │ Finance 21%       │
├───────────────────────────┴───────────────────┤
│ Transactions                                  │
└───────────────────────────────────────────────┘
```

---

# 6. Phase 3 — Backtest 개선

## 현재 장점

현재 collector의 backtest는 대부분 MySQL `price_history`를 사용한다.

즉 여러 종목 가격을 매번 yfinance에서 새로 받는 구조는 아니다.

현재 주요 전략:

```text
BUY_AND_HOLD
SMA_CROSSOVER
PERIODIC_REBALANCE
VOLATILITY_TARGET
```

가격 쿼리 또한 `symbol_id` 기준으로 개선돼 있다.

---

## 핵심 문제 1 — 동기 실행

현재:

```text
POST /api/backtest/runs
      ↓
Spring Request Thread
      ↓
Collector HTTP
      ↓
MySQL
      ↓
Pandas
      ↓
Result
```

Spring 코드 주석에서도 동기 실행임을 명확히 적어두고 있다.

전략이 복잡해지면 Request Thread 점유 시간이 계속 증가한다.

---

## 목표 구조

```text
POST /api/backtest/runs
      ↓
202 Accepted
      ↓
QUEUED
      ↓
RUNNING
      ↓
COMPLETED / FAILED
```

응답 예:

```json
{
  "id": 42,
  "status": "QUEUED"
}
```

초기에는 Kafka까지 도입할 필요는 없다.

권장:

```text
Spring @Async
+
Bounded Executor
```

또는 현재 collector 구조를 유지하면서 비동기 job만 추가한다.

---

## 핵심 문제 2 — VIX 외부 API 의존성

`VOLATILITY_TARGET` 전략은 현재:

```python
yf.Ticker("^VIX").history(...)
```

를 사용한다.

다른 데이터가 DB 기반이어도 이 전략은 실행할 때 외부 API 상황에 영향을 받는다.

### 변경

```text
^VIX daily data
→ 정기 수집
→ price_history 또는 index history 저장
→ Backtest는 DB만 사용
```

목표:

```text
Backtest = 100% Reproducible DB Calculation
```

---

## 핵심 문제 3 — 실제 계산 기간

현재 `_load_closes()`:

```python
wide = wide.dropna()
```

여러 종목이 있을 경우 모든 종목의 가격이 존재하는 날짜만 남는다.

예:

```text
사용자 요청:
2015-01-01 ~ 2026-01-01

실제 계산:
2020-05-12 ~ 2025-12-31
```

가 될 가능성이 있다.

응답에 실제 계산 기간을 명시해야 한다.

```json
{
  "requestedStartDate": "2015-01-01",
  "requestedEndDate": "2026-01-01",
  "actualStartDate": "2020-05-12",
  "actualEndDate": "2025-12-31",
  "tradingDays": 1411
}
```

---

## 추가 지표 권장

현재:

- Total Return
- CAGR
- MDD
- Volatility
- Sharpe Ratio

추가:

- [ ] Sortino Ratio
- [ ] Calmar Ratio
- [ ] Best Year
- [ ] Worst Year
- [ ] Best Month
- [ ] Worst Month
- [ ] Positive Month Ratio
- [ ] Drawdown Duration
- [ ] Recovery Date
- [ ] Drawdown Curve
- [ ] Monthly Returns
- [ ] Annual Returns

---

## 결과 캐시

같은 전략이 반복 실행될 수 있으므로 config hash 사용.

예:

```text
SHA-256(
 ticker list
 + startDate
 + endDate
 + strategy
 + strategy params
)
```

동일한 hash + 동일 데이터 버전이면 기존 결과 재사용.

---

## Backend 작업 목록

- [ ] Backtest async job
- [ ] QUEUED / RUNNING / COMPLETED / FAILED 상태 정리
- [ ] `202 Accepted`
- [ ] Polling API
- [ ] VIX DB 적재
- [ ] yfinance 의존성 제거
- [ ] 실제 계산 기간 반환
- [ ] Drawdown Curve
- [ ] Monthly / Annual Return
- [ ] 추가 리스크 metric
- [ ] Config Hash
- [ ] Result Cache
- [ ] Backtest metric

---

# 7. Phase 4 — Report → Research 구조 개선

## 현재 구조

`ReportService.capture()`에서 다음을 순차 실행한다.

```text
Price
↓
History
↓
Financial
↓
Analysis
```

하지만 대부분 독립 작업이다.

---

## 문제 1 — 순차 호출

목표:

```text
           ┌─ Price
           ├─ History
Request ───┼─ Financial
           └─ Analysis
                ↓
               Join
```

Java 21 업그레이드 시 Virtual Thread를 고려할 수 있다.

현재 Java 17을 유지한다면 `CompletableFuture + 전용 Executor`로 처리 가능하다.

---

## 문제 2 — Idempotency 위치

현재 흐름:

```text
capture()
↓
idempotency.execute()
↓
save()
```

동일 Idempotency-Key로 재요청하더라도 무거운 capture 작업이 먼저 수행된다.

### 변경

```text
idempotency.execute()
    ↓
capture()
    ↓
save()
```

코드 방향:

```java
return idempotency.execute(
    user.id(),
    "stock-report-create",
    key,
    new CreateRequest(ticker),
    ReportService.Detail.class,
    () -> {
        var payload = reports.capture(ticker);
        return reports.save(user.id(), payload);
    }
);
```

---

## Report UX 변경

현재 저장된 Report ID 중심:

```text
/reports/{id}
```

장기적으로는:

```text
/research/{ticker}
```

또는:

```text
/symbols/{ticker}/research
```

형태가 더 자연스럽다.

화면 예:

```text
NVDA Research
────────────────────────

Overview
Price
Fundamentals
Technical
Risk
News
Options
Market Context

────────────────────────
Saved Snapshots

10/01 21:10
09/28 12:40
09/22 09:20
```

---

## Data Model 권장

```text
ResearchSnapshot
├── MarketData
├── Technical
├── Fundamentals
├── Risk
├── News
├── Options
└── FreshnessMetadata
```

---

## Backend 작업 목록

- [ ] `capture()` 병렬화
- [ ] idempotency scope 수정
- [ ] Section DTO 분리
- [ ] freshness metadata 공통화
- [ ] partial result 상태 명확화
- [ ] Report history API 정리
- [ ] Research route 설계
- [ ] Report metric

---

# 8. Phase 5 — 공통 Frontend 구조 리팩터링

현재 주요 파일 크기:

```text
Portfolio page.tsx  약 22KB
Backtest page.tsx   약 24KB
Screener page.tsx   약 14KB
```

페이지 한 파일에 과도한 책임이 있다.

---

## 권장 구조

```text
src/features/
├── screener/
│   ├── api.ts
│   ├── types.ts
│   ├── hooks.ts
│   └── components/
│
├── portfolio/
│   ├── api.ts
│   ├── types.ts
│   ├── hooks.ts
│   └── components/
│
├── backtest/
│   ├── api.ts
│   ├── types.ts
│   ├── hooks.ts
│   └── components/
│
└── research/
    ├── api.ts
    ├── types.ts
    ├── hooks.ts
    └── components/
```

App Router는 route wiring에 집중한다.

---

## Data Fetching

현재 직접:

```text
refreshPositions()
refreshPortfolios()
Promise.all(...)
```

등을 관리한다.

TanStack Query 도입을 검토한다.

예:

```text
queryClient.invalidateQueries({
  queryKey: ['portfolio', id]
})
```

장점:

- 캐시
- 요청 중복 제거
- stale 관리
- mutation 후 invalidation
- loading/error state 단순화

---

# 9. 공통 디자인 시스템

4개 기능을 각각 독립 UI로 만들기보다 하나의 `Analysis Workspace` 패턴을 사용한다.

```text
Header
↓
Summary Cards
↓
Filter / Configuration
↓
Main Visualization
↓
Data Table
↓
Warnings / Insights
↓
History
```

적용 대상:

- Screener
- Portfolio
- Backtest
- Research

---

# 10. Phase 6 — AI Agent 도입

도메인 기능 정상화 이후 다음 Tool을 만든다.

```text
ScreenerTool
PortfolioTool
BacktestTool
ResearchTool
```

예:

```text
User:
"최근 모멘텀이 강하고 PER 30 이하인 반도체 기업을 찾아서
NVDA와 비교하고 5년 백테스트 해줘."
```

Workflow:

```text
Natural Language
       ↓
Screener Tool
       ↓
Candidate Selection
       ↓
Research Tool
       ↓
Backtest Tool
       ↓
AI Synthesis
```

이 단계에서 Agent가 계산 로직을 직접 수행하지 않도록 한다.

```text
Financial calculation → Java/Python
Backtest calculation  → Python
Screener filtering    → DB
Portfolio metrics     → Java

LLM
→ routing
→ interpretation
→ explanation
→ synthesis
```

---

# 11. P0 실제 수정 대상 파일

## Screener

```text
marketboardBackend/src/main/java/org/juns/marketboardbackend/screener/
├── ScreenerService.java
└── ScreenerController.java

collector/app/screener.py

frontend/src/app/(app)/screener/page.tsx
```

주요 수정:

- live run 제거 방향
- snapshot 기반 filtering
- DB pagination
- Filter UI 재설계

---

## Portfolio

```text
marketboardBackend/src/main/java/org/juns/marketboardbackend/portfolio/
├── PortfolioService.java
├── PortfolioController.java
├── Portfolio.java
└── PortfolioPosition.java

frontend/src/app/(app)/portfolio/page.tsx
```

주요 수정:

- Transaction 도입
- 목록 조회 단순화
- Aggregate Detail API
- Frontend component 분리

---

## Backtest

```text
marketboardBackend/src/main/java/org/juns/marketboardbackend/backtest/
├── BacktestService.java
├── BacktestController.java
└── BacktestRun.java

collector/app/backtest.py

frontend/src/app/(app)/backtest/page.tsx
```

주요 수정:

- async job
- VIX DB화
- actual range
- metrics 확대
- frontend component 분리

---

## Report

```text
marketboardBackend/src/main/java/org/juns/marketboardbackend/report/
├── ReportService.java
└── ReportController.java

frontend/src/app/(app)/reports/[id]/page.tsx
```

주요 수정:

- parallel capture
- idempotency 수정
- Research Workspace로 전환

---

# 12. PR 분리 전략

한 번에 전부 변경하지 않는다.

## PR 1 — Screener Performance Foundation

```text
- Snapshot schema
- Batch calculation
- DB filtering
- Performance metrics
```

## PR 2 — Screener UI

```text
- Filter panel
- Result table
- Pagination
- Preset
```

## PR 3 — Portfolio Query Refactor

```text
- 목록 조회 단순화
- 상세 aggregate API
- 중복 조회 제거
```

## PR 4 — Portfolio Transaction Model

```text
- Transaction
- Position projection
- Realized PnL
```

## PR 5 — Portfolio UI

```text
- Summary
- Holdings
- Allocation
- Transactions
```

## PR 6 — Backtest Async

```text
- Job model
- Async execution
- Polling
```

## PR 7 — Backtest Accuracy / Metrics

```text
- VIX DB
- Actual date range
- Drawdown
- Monthly/Annual return
```

## PR 8 — Report Performance

```text
- Parallel data fetch
- Idempotency fix
- Metrics
```

## PR 9 — Research Workspace

```text
- Report UI redesign
- History
- Unified Research DTO
```

## PR 10 — AI Foundation

```text
- Spring AI
- Tool adapters
- AI metrics
- Model router
```

---

# 13. 완료 기준

## Screener

- [ ] 사용자 요청 시 전체 S&P500 재계산 없음
- [ ] 사용자 요청 시 yfinance 반복 호출 없음
- [ ] pagination 동작
- [ ] sorting 동작
- [ ] 필터 URL 복원 가능
- [ ] p95 500ms 수준 목표
- [ ] Grafana 성능 비교 가능

## Portfolio

- [ ] 목록 조회 중복 제거
- [ ] 포트폴리오 Detail API 1회로 주요 화면 구성 가능
- [ ] BUY/SELL transaction 저장 가능
- [ ] realized/unrealized PnL 분리
- [ ] allocation 표시
- [ ] performance chart 표시

## Backtest

- [ ] POST 요청이 장시간 blocking되지 않음
- [ ] Job 상태 조회 가능
- [ ] 계산 중 외부 가격 API 의존성 없음
- [ ] actual date range 표시
- [ ] drawdown curve 제공
- [ ] monthly / annual result 제공
- [ ] 동일 config cache 가능

## Report / Research

- [ ] 데이터 조회 병렬 실행
- [ ] Idempotency 전에 무거운 capture 실행하지 않음
- [ ] partial data 상태 표시
- [ ] freshness 통일
- [ ] 과거 snapshot 조회 가능

## Frontend

- [ ] page.tsx 책임 축소
- [ ] feature별 component/hook 분리
- [ ] loading/error/empty state 통일
- [ ] 공통 Analysis Workspace 디자인 적용

---

# 14. 최종 목표 아키텍처

```text
                        MarketBoard
                            │
           ┌────────────────┼────────────────┐
           │                │                │
        Market          Portfolio         Research
         Data               │                │
           │                │                │
           ├──────── Screener API ───────────┤
           │                │                │
           ├──────── Backtest API ───────────┤
           │                │                │
           └──────── Domain Services ────────┘
                            │
                         AI Tools
                            │
                   AI Workflow Engine
                            │
                 Model Routing / Cache
                            │
                       AI Research
```

핵심 방향은 다음 한 문장으로 정리할 수 있다.

> **MarketBoard의 투자 도메인 기능을 먼저 빠르고 결정론적인 Tool로 만든 뒤, AI는 그 Tool들을 조합하고 해석하는 Orchestration Layer로만 사용한다.**
