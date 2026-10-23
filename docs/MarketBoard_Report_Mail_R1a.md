# R1a 거래일·출처 및 미국 세션 달력

- 구현/검증일: 2026-10-23 KST
- 연결: [계획](MarketBoard_Report_Mail_Plan.md), [R0 점검](MarketBoard_Report_Mail_R0.md)
- 범위: collector/백엔드 데이터 계약, 저장 및 기존 API 호환. 보고서 생성·메일 발송·새 화면은 R1b 이후다.

## 거래일 판별

`exchange-calendars`(잠금 버전 4.13.2)의 XNYS 달력으로 현재 시각까지 종료된 마지막 미국 주식 세션을 구한다. DST, 휴장, 조기 폐장을 달력의 UTC 폐장 시각으로 처리한다. 내부 `GET /market-calendar/completed-session`은 거래일, 폐장 시각, 판별 시각, 시간대, 달력 버전, 최근 세션 날짜를 반환한다. 달력 조회 실패 시 시장 폭 재집계를 건너뛰고 기존 자료를 유지한다.

SPX/IXIC/RUT와 11개 미국 섹터 ETF는 완료된 세션의 일봉만 사용한다. 금·유가·환율·금리·캐나다 지수·VIX에는 XNYS를 임의로 적용하지 않으며 `sessionStatus=UNVERIFIED`로 둔다. 완료된 세션이라는 판별은 공급자가 최종 정정까지 반영했음을 보장하지 않는다.

## 추가 계약과 호환

| 대상 | 추가 정보 | 의미 |
| --- | --- | --- |
| 지수 일봉 | tradingDate, fetchedAt, source, sourceSymbol, calendar, sessionStatus, adjustment | 공급자 현지 일봉 날짜와 수집 시각을 분리. 기존 ts/OHLCV 유지 |
| 섹터 | tradingDate, expectedTradingDate, fetchedAt, source, calendar, status, adjustment | READY는 수집 당시 마지막 완료 거래일과 일치함을 뜻함. STALE은 그보다 오래된 봉. 기준 봉이 없으면 해당 수익률은 null |
| 시장 폭 | tradingDate, source, calendar, calendarVersion, sessionClosedAt, eligibleCount, status | snapshotDate는 UTC 계산일로 유지. universeSize는 실제 집계 수, eligibleCount는 전체 후보 수. PARTIAL은 일부 후보 제외 |

섹터 기간 수익률은 1/5/21 **세션 전**의 정확한 날짜와 비교한다. 누락 봉 때문에 2거래일 수익률이 1일 수익률로 표시되지 않게 했다. 기간 수익률은 최신 수신 거래일을 기준으로 하며 expectedTradingDate와 별도 비교해야 한다.

시장 폭은 완료된 최신 세션과 바로 전 세션의 봉을 모두 확보한 종목만 집계한다. 미국 현지 자정이라는 기존 yfinance 일봉 라벨 규칙과 맞지 않는 timestamp는 추측해서 이동하지 않고 제외한다. 전 종목 제외 시 기존 스냅샷을 보존한다. 기존 신고가/신저가 계산 정의(확보된 최대 252개 종가 범위)는 이번 변경에서 유지했다. 전체 252세션 충족률과 고가/저가 기반 정의 보강은 별도 과제다.

`V47__market_breadth_provenance.sql`은 nullable 컬럼만 추가한다. 예전 스냅샷/JSON/Redis 자료에는 출처·거래일을 지어내지 않으며 상태는 UNVERIFIED로 읽는다. price_history에는 공급자 메타데이터가 없어 시장 폭 출처를 `MarketBoard price_history (provider metadata unavailable)`로 명시한다. 원천 수집 시각도 추정하지 않는다. READY/PARTIAL은 날짜 정렬·집계 범위 상태이며 공급자 출처까지 검증했다는 의미가 아니다.

## 검증

- Python 14개: DST 시작/종료, 성금요일, 독립기념일 대체 휴장, 연초 휴장, 주말, 조기 폐장 직전/정각, naive 시각 거절, 장중 봉 제외, 비미국 자산 미확인, 오래된 섹터/자료 없음, 비교 거래일 누락.
- Java 12개: 시장 폭 날짜 정렬/낡은 종목 제외/알 수 없는 봉 라벨 제외/달력 장애/직전 봉 누락, JSON·기존 캐시 호환, 지수·섹터 저장 조회 및 캐시 테스트.
- 로컬 Docker collector/backend 빌드, MySQL V46→V47 적용 및 재시작 후 47개 migration 검증 성공.
- 로컬 달력 API: 2026-10-23T09:25Z 판별 시 완료 거래일 2026-10-22, 폐장 20:00Z.
- 백엔드 저장 후 재조회: SPX 127봉과 섹터 11개 모두 거래일·출처·수집 시각 확보. 최신 기준일 2026-10-22, 섹터 READY.
- 기존 시장 폭 API: 날짜/출처 null, 상태 UNVERIFIED로 보존되는 것 확인. 인증된 재집계 API로 실제 DB 집계를 강제 실행하지 않았으며 새 집계 경로는 단위 테스트로 검증했다.

운영 배포, 실메일, 브라우저 UI 검증은 수행하지 않았다. 다음 R1b는 이 계약을 사용한 불변 시황 보고서 저장·조회이며 보고서 발행 시점에는 expectedTradingDate를 다시 조회해 오래된 캐시의 READY 상태를 최신성으로 오해하지 않아야 한다.
