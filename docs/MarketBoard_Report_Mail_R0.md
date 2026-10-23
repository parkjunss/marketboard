# 보고서·메일 R0 사전 검증

- 점검일: 2026-10-23 (KST)
- 대상: 현재 소스 및 로컬 Docker 백엔드 `127.0.0.1:18081`
- 연결: [보고서·메일 계획](MarketBoard_Report_Mail_Plan.md)
- 판정: 소스/읽기 API 점검 완료. 운영 동의·메일 제공자·생성 비용 검증은 미완료이며 R0 전체 완료로 간주하지 않는다.

## 확보된 기반과 결손

| 항목 | 확인 근거 | R1/R2에 필요한 조치 |
| --- | --- | --- |
| 지수·거시 일봉 | `collector/app/market_indices.py`, `MarketIndexController`: yfinance 일봉과 UTC ts, OHLCV 제공 | ts는 봉의 라벨이며 마감 확정 시각이 아님. 거래소 세션 종료와 수집 완료를 별도 검증. 통화·단위·출처·조정 정책 저장 |
| 섹터 수익률 | `SectorPerformance`: slug/name 및 1/5/21거래봉 수익률만 제공 | 각 ETF의 기준 거래일·수집 시각 추가. 서로 다른 날짜의 수익률을 동일 마감으로 요약하지 않음 |
| 섹터 보존 | `SectorPerformanceService`: 최신 행 덮어쓰기, 실패 시 이전 값 유지 | 보고서 발행 시 불변 payload 복사. 최신 조회 성공을 최신 데이터로 해석하지 않음 |
| 시장 폭 | `MarketBreadthService`: snapshotDate에 `LocalDate.now()` 사용 | 계산일과 원천 거래일 분리. 기존 snapshotDate를 미국장 거래일로 사용하지 않음 |
| 심리 | fear-greed timestamp, put/call history timestamp 존재 | 관측·수집 시각의 의미를 소스별로 구분. 거래일 불명인 값은 보조 지표로 표시 |
| 달력 | `collector/pyproject.toml`에 거래소 달력 직접 의존성 없음 | 미국 세션 달력 도입 및 DST·휴장·조기 폐장 테스트. 주말 제외만으로 대체하지 않음 |
| 개인 보고서 | `ReportService`: 계정별 저장/조회, schemaVersion=1, 부분 실패, 별도 capture/save | 기존 payload 호환 유지. 현재는 모든 자료가 없을 때만 저장 거절하므로 신규 발행 기준과 구분 |
| 메일 | `DailyReportMailer`: 매일 08:00 Asia/Seoul, 뉴스 10개 즉시 발송 | 저장 보고서 참조·발송 이력·중복 방지·휴장 정책 필요. 알림 생성은 전달 성공이 아님 |
| 구독 | User.dailyReportEnabled 및 프로필 변경 경로, Google email_verified 검사 | 기존 동의 범위/시점/정책 버전과 주소 인증 보존 여부 확인. 새 종목 구독으로 자동 전환 금지 |
| SMTP | `MailService`: SimpleMailMessage, application.yaml 환경변수 설정 | 접수와 전달 구분, 반송/신고 처리, 운영 발신 도메인 검증 필요 |

## 읽기 API 측정

각 경로 1회, 로컬의 현재 캐시/DB 상태에서 측정했다. 외부 공급자 호출 비용·콜드 캐시·보고서 생성 전체 시간·부하 성능을 의미하지 않는다. 계정 데이터와 뉴스 본문은 기록하지 않았다.

| 경로 (`/api` 이하) | 결과 | 시간(초) | 확인 |
| --- | --- | --- | --- |
| `/market-indices` | 200 | 0.362 | 정적 지수 목록 |
| `/market-indices/SPX/history` | 200 | 0.064 | 127개 봉, 마지막 ts=2026-10-22T04:00:00Z |
| `/market-indices/sectors/performance` | 200 | 0.095 | 기준일 필드 없음 |
| `/market-breadth` | 200 | 0.280 | snapshotDate=2026-10-23, computedAt=2026-10-23T08:23:42Z |
| `/market-sentiment/fear-greed` | 200 | 0.450 | timestamp=2026-10-23T08:58:16+00:00 |
| `/market-sentiment/put-call-ratio` | 200 | 0.227 | 마지막 history timestamp=2026-10-23T08:53:40.985208660Z |
| `/news` | 200 | 0.083 | 응답 62,268 bytes |

최초 지수 조회에서 잘못된 slug `sp500`은 404였다. 실제 목록/소스에 정의된 식별자는 `SPX`다. 데이터 시점은 수신값을 기록한 것이며 공급자 정확성을 독립 검증한 결과가 아니다.

## 다음 구현 단위

1. **R1a 시점 계약**: collector에서 지수/섹터의 원천 거래일과 출처를 보존하고, 시장 폭의 계산일과 거래일을 분리한다. 미국 거래소 달력으로 직전 완료 세션을 결정한다. 기존 화면/API 호환을 유지한다.
2. **R1b 저장·조회**: 시장 보고서 전용 테이블과 생성 멱등키를 추가한다. 필수 지수 미확보는 발행 보류, 보조 지표 누락은 PARTIAL로 저장한다. 목록/상세와 날짜·버전 표시를 연결한다.
3. **R2/R3 이후**: 개인 보고서 확장 후 구독·outbox를 연결한다. 신규 메일 스케줄은 테스트 검증과 전환 정책이 준비되기 전 활성화하지 않는다.

## 아직 확보하지 않은 증거

- 인증된 테스트 계정으로 종목 보고서 생성 시간/외부 호출량 측정. 이번 점검은 쓰기 API를 실행하지 않았다.
- 실제 SMTP 설정·발신 도메인 SPF/DKIM/DMARC·반송 webhook·제공자 비용 및 멱등성 지원. 비밀 환경변수는 출력하지 않았다.
- 기존 사용자 동의 원문/시점, 공개 데이터 재배포 조건, 운영 보존 기간.
- 실메일 전달·운영 배포·신규 거래소 달력 검증. 이번 점검에서는 메일을 발송하지 않았다.
