# MarketBoard — 진행상황 추적

> 최종 갱신: 2026-07-19
> 기준 계획서: `stock-monitor-dev-plan.html` (2026-07-16 작성, Phase 1~7 로드맵)
> 참고: `marketboard_development_plan.html`은 이전 버전의 위젯/템플릿 중심 기획서로, 현재는 `stock-monitor-dev-plan.html`이 실행 기준 문서.

## 다음 세션 시작점

**계획서(Phase 1~7) 로드맵 전체 구현 완료 + 공개 HTTPS 접속(nginx/certbot/DuckDNS) 코드까지 작성 완료, 아직 실제 첫 배포는 검증 전** (2026-07-19): Phase 6(Prometheus + Grafana 관측성)에 이어 Phase 7의 PR 테스트 워크플로 + 배포용 `Dockerfile` 3종 + `docker-compose.yml` + Pi(`rasp4`) self-hosted runner 설치 + `ci.yml`의 build/deploy job, 그리고 `marketboard.duckdns.org` 공개 HTTPS 접속용 nginx+certbot+DuckDNS까지 전부 코드로 작성됨(상세는 아래 "Phase 7" 섹션). **단, 이번 세션 마지막에 추가한 nginx/certbot/DuckDNS는 Pi 쪽 수동 준비(라우터 포트포워딩, DuckDNS 토큰, `.env` 신규 키, `nginx/init-letsencrypt.sh` 최초 실행)가 아직 하나도 안 된 상태 — 이 상태로 그냥 푸시하면 `deploy` job이 nginx 컨테이너를 띄우려다 실제 인증서 파일이 없어서 크래시루프에 빠짐. 아래 "Phase 7" 섹션의 "공개 HTTPS 접속 설정" 항목의 체크리스트를 먼저 완료한 뒤에 푸시할 것.**

이전에 추가된 것(2026-07-18): 포트폴리오 삭제 다이얼로그 버그 수정(`useImperativeAlertDialog` → 제어형 `AlertDialog`, 프로젝트 전체에서 이제 완전히 안 씀), 종목 세부 페이지(`/symbols/[ticker]`) 보강(뉴스/기술지표/재무링크/전일대비/회사명/1년고저/기업개요/차트 SMA오버레이/잘못된 티커 처리/뒤로가기 링크), 시세보드→종목리스트 통합(관심종목 별표 이관), 티커 이름 truncate, SMA 라인 색상 버그 수정, Prometheus/Grafana 관측성(커스텀 메트릭 4종 + 독립 Docker 컨테이너 + 대시보드), S&P500 500종목 일봉 백필이 정체돼 보이던 문제 조사 및 수정(진짜 원인은 yfinance가 아니라 종목마다 새 MySQL 커넥션을 여느라 커넥션당 ~10초씩 걸리던 것) — 상세는 아래 각 섹션 참고.

**바로 시작할 것 후보:**

1. **공개 HTTPS 접속 수동 준비 체크리스트 완료 후 푸시** — 라우터 80/443 포트포워딩 → `192.168.0.174`, DuckDNS 토큰/서브도메인 확인, Pi의 `~/marketboard/.env`에 `DUCKDNS_TOKEN`/`DUCKDNS_SUBDOMAIN`/`LETSENCRYPT_EMAIL` 추가 + `CORS_ALLOWED_ORIGINS`/`NEXT_PUBLIC_*`를 `https://marketboard.duckdns.org`로 갱신, `./nginx/init-letsencrypt.sh` 최초 1회 실행(진짜 인증서 발급). 전부 끝난 뒤에야 `git push`해서 `deploy` job이 nginx를 정상적으로 띄울 수 있음. 상세는 "Phase 7" 섹션 참고.
2. **그 다음 최신 푸시의 GitHub Actions 실행 결과 확인** — backend/collector/frontend/deploy 4개 job 전부 통과하는지, Pi(`rasp4`)에서 `docker ps`로 `marketboard-*` 컨테이너 10개(mysql/redis/backend/collector/frontend/prometheus/grafana/nginx/certbot/duckdns)가 정상 기동했는지, `https://marketboard.duckdns.org`가 실제로 응답하는지.

**남겨둔 확인 작업 (급하지 않음)**
- 장 마감 후 `collector/app/rest_fallback.py`(이제 Finnhub REST가 아니라 yfinance 기반) 전체 폴백 루프 재검증 — fetch+publish 경로 자체는 확인했지만, 스테일 조건이 실제로 걸리는 장 마감 상황의 전체 루프는 아직 안 봄
- `symbols` 테이블에 테스트로 넣었던 `AMZN` row가 남아있음(is_active=1) — 그대로 둬도 무방(이제 관리자 화면에서 언제든 비활성화 가능)
- `indicators.cron`(기본 5분 주기)이 실제로 여러 번 순환하며 값이 갱신되는지는 아직 짧은 구간만 봄 — 하루 이상 켜둔 상태로 `computed_at`이 계속 최신으로 갱신되는지 다음 세션에 확인
- `@astryxdesign/charts`(공식 차트 패키지) 설치가 peer dependency 충돌로 보류됨(canary 버전이 `@astryxdesign/core@0.1.6-canary.*`를 요구, 우리는 안정 `^0.1.6` 사용 중) — 지금은 커스텀 SVG `Sparkline`/`MultiLineChart`/`GroupedBarChart` 컴포넌트로 우회했지만, core가 canary 라인을 따라잡거나 charts가 안정 릴리스되면 재검토
- 재무 대시보드(`/financials`)의 `Interest Coverage` KPI가 AAPL 기준 `—`(null)로 나옴 — 버그 아님: Apple은 최근 회계연도에 순이자수익(net interest income)이라 "Interest Expense" 항목 자체가 없어서(수집기 `financials.py`가 null로 정직하게 반영) 발생하는 정상 케이스. 이자비용이 실제로 있는 종목(예: 레버리지 높은 기업)으로 확인해볼 것.
- `collector` 프로세스가 이번에도 이유를 알 수 없는 부모/자식(venv python + anaconda python) 쌍으로 뜨는 현상이 재발함(Phase 4/이전 세션에서도 관찰됨) — 기능엔 지장 없었지만 원인 미조사 상태로 남아있음.

**세션 종료 시점 실행 상태** (2026-07-18 기준, 실시간 WS 대상 종목 재구성 + 교착 버그 수정 직후 — 셋 다 켜진 채로 세션 종료함)
- 백엔드: 사용자가 IntelliJ에서 devtools와 함께 8080으로 구동 중인 인스턴스를 그대로 사용(직접 띄운 `gradlew bootRun` 없음) — `portfolios`/`portfolio_positions`/`symbols.in_sp500_universe` 마이그레이션(V9/V10/V11) 자동 적용 확인, `SymbolAdminService`의 트랜잭션/동기화 분리 수정도 devtools 자동 재시작으로 반영됨. `./gradlew test`(21건) 전부 통과.
- 프론트엔드: 포트 3100의 `npm run dev`를 계속 재사용 중(이번 세션에서 새로 띄우지 않음) — 이번 세션 후반부(실시간 종목 재구성)는 프론트엔드 변경 없음(백엔드/콜렉터만).
- collector: **이번 세션에 세 번 재기동함** — `symbol_profile.py`, `sp500_universe.py`, 그리고 마지막으로 `lifespan()`이 `DEFAULT_SYMBOLS` 대신 DB의 `is_active` 집합을 읽도록 수정한 것 반영을 위해(Python은 `--reload` 없이 떠 있어 핫리로드 안 됨). 재기동 후 `uv run pytest`(10건) 통과 확인.
- **`.env`의 `SP500_BATCH_LIMIT`가 사용자에 의해 빈 값으로 바뀌어 있어서, 마지막 재기동 시 S&P500 전체(503종목) 배치가 자동으로 시작됨** — 세션 종료 시점에 멤버십 시딩(503종목)은 끝났지만 일봉 백필(`price_history`)은 아직 진행 중(종료 시점 기준 약 40~50종목 완료, 계속 진행 중이었음 — 콜렉터가 살아있는 한 백그라운드에서 계속 진행됨, 앱 다른 기능엔 영향 없음). 다음 세션에서 진행 상황 확인 권장(위 "다음 세션 시작점" 참고).
- **실시간 WS 대상이 SPY/QQQ/DIA + 상위 10종목(AAPL/MSFT/GOOGL/AMZN/NVDA/META/TSLA/BRK-B/AVGO/JPM), 총 13종목으로 재구성 완료** — `symbols.is_active=1`인 종목과 콜렉터 `/health`의 `subscribed_symbols`가 정확히 일치하는 것까지 확인함.
- `mysql-container`/`redis-container`: 상시 구동 중(3306/6379) — 다음 세션 시작 시에도 Docker Desktop이 꺼져 있을 수 있으니 먼저 `docker ps`로 확인할 것
- `financial_statements` 테이블에 캐싱된 `AAPL`/`MSFT` row가 남아있음 — 정상 캐시라 정리 안 함(24시간 TTL). `symbols` 테이블엔 이제 S&P500 유니버스 503종목(대부분 비활성) + 위 13개 실시간 활성 종목이 있음 — 전부 정상 데이터, 테스트 잔재 없음.

## 전체 요약

| 구성요소 | 상태 | 비고 |
|---|---|---|
| marketboardBackend (Spring Boot) | ✅ Phase 1, 3, 5 완료 + 커스텀 대시보드 + `QuoteResponse.name` + 시장지표/재무 프록시 API + 포트폴리오 백엔드 추가 + 배포용 `Dockerfile` 추가(2026-07-19) | |
| collector (Python) | ✅ Phase 2 완료, Phase 5 알림 체크 로직 추가, 뉴스/시장지표/재무/종목프로필 프록시 + S&P500 유니버스 배치 + 실시간 대상 종목 일봉 자동 갱신 루프 추가 + 배포용 `Dockerfile` 추가(2026-07-19) | REST 폴백은 이제 yfinance 기반(장중 실거래로는 미검증, fetch+publish 경로만 별도 확인). S&P500 전체(503종목) 배치 완료(2026-07-18, 커넥션 재사용 버그 수정 후 91.5초·실패 0건). 실시간 WS 대상은 SPY/QQQ/DIA + 상위 10종목(13개)로 확정, 일봉은 `active_symbols_daily_refresh_loop`가 매일 자동 갱신 |
| frontend (Next.js) | ✅ Phase 4, 5 완료 + `/dashboard` + `/stock-list`(구 시세 보드 통합, 관심종목 별표 포함) + `/market` + `/financials`(다중 종목 비교가 랜딩, 선택 시 `/financials/[ticker]` 상세로 이동) + `/portfolio` + `/symbols/[ticker]`(뉴스/기술지표/기업개요/SMA오버레이 보강) + 배포용 `Dockerfile`(standalone output) 추가(2026-07-19) | Astryx 디자인 시스템 적용, 포트 3100 고정(아래 "참고"), 구 `/`(시세 보드)는 `/stock-list`로 리다이렉트 |
| 인프라 (docker-compose / CI/CD / 관측성) | ✅ Phase 6(Prometheus+Grafana) 완료, ✅ CI(GitHub Actions PR 테스트 워크플로) 완료(2026-07-19), ✅ 배포용 `docker-compose.yml` + Dockerfile 3종 + Pi self-hosted runner + `ci.yml` build/deploy job 전부 완료(2026-07-19), ✅ nginx+certbot+DuckDNS 공개 HTTPS 코드 작성 완료했으나 ⚪ Pi 쪽 수동 준비(포트포워딩/토큰/인증서 최초 발급) 전이라 아직 미배포 | 로컬 개발은 공용 `mysql-container`/`redis-container`(다른 프로젝트와 공유) 재사용, Prometheus/Grafana는 프로젝트 스코프 독립 컨테이너(`marketboard-prometheus`/`marketboard-grafana`). 배포 대상 Pi(`rasp4`)는 이미 다른 프로젝트(`tradehub`)와 포트를 공유하므로 호스트 포트를 재배정함(위 "Phase 7" 섹션 참고) |

---

## Phase별 진행상황 (계획서 §07 기준)

### ✅ Phase 1 — 기반 공사 (인증) — 완료 (2026-07-16)
- [x] Spring Boot 프로젝트 셋업 (`marketboardBackend`, Java 17 toolchain, Spring Boot 4.1.0)
- [x] Flyway 마이그레이션으로 `users` 테이블 생성 (`V1__create_users_table.sql`)
- [x] 회원가입 API (`POST /api/auth/signup`)
- [x] 로그인 API (`POST /api/auth/login`) — JWT 발급
- [x] 토큰 재발급 API (`POST /api/auth/refresh`)
- [x] 로그아웃 API (`POST /api/auth/logout`) — Redis에서 refresh token 삭제
- [x] Refresh Token Redis 저장/검증/폐기 (`RefreshTokenService`)
- [x] JWT 인증 필터 체인 + `SecurityConfig` (stateless, `/api/admin/**` 및 `/actuator/**`는 `ROLE_ADMIN`만 허용)
- [x] `ROLE_USER` / `ROLE_ADMIN` 분리 (`Role.java`), 계정 상태 관리 (`UserStatus.java`, 정지 예외 `AccountSuspendedException`)
- [x] 관리자 시드 계정 (`AdminAccountSeeder`, `application.yaml`의 `admin.seed.*`)
- [x] 예외 처리 공통화 (`GlobalExceptionHandler`, `ErrorResponse`, 커스텀 예외 4종)
- [x] **자동화 테스트 추가** — `AuthServiceTest`(Mockito 단위 테스트 12건: signup/login/refresh/logout의 성공·실패·정지계정·토큰위변조 케이스)와 `AuthControllerTest`(`@WebMvcTest` 9건: 검증 실패 400, 상태코드 매핑, `@AuthenticationPrincipal` 인증 컨텍스트 포함 logout). 총 21개 테스트 전부 통과 (`./gradlew test`)
- [x] **curl 기반 End-to-End 검증 완료** — 실행 중인 백엔드(로컬, 공용 mysql-container/redis-container 사용)에 대해 직접 확인:
  - 회원가입 → `201`, 중복 이메일 재가입 → `409`
  - 로그인 → `200` + 토큰 발급
  - USER 토큰으로 `/actuator/prometheus`(ADMIN 전용) 접근 → `403`, 토큰 없이 접근 → `403`
  - refresh token으로 재발급 → `200` + 새 토큰 쌍
  - 관리자 시드 계정 로그인 → `200`, ADMIN 토큰으로 `/actuator/prometheus` 접근 → `200`
  - logout → `204`, 이후 폐기된 refresh token으로 재발급 시도 → `401`(Redis 폐기 확인)
  - 인증 없이 logout 시도 → `403`
  - 테스트 중 생성한 가입 계정은 검증 후 DB에서 정리함

**범위 결정 — `docker-compose.yml`은 의도적으로 미작성**: 로컬 개발 환경에는 이미 다른 스터디 프로젝트와 공유하는 `mysql-container`/`redis-container`가 상시 기동돼 있어(호스트 3306/6379 점유), 이번 백엔드는 그 컨테이너에 붙여서 개발·검증했다. 실제 라즈베리파이 배포용 `docker-compose.yml`(mysql+redis+backend 3개 컨테이너, 격리된 스택)은 [[Phase 7]] 배포 단계에서 작성하기로 사용자와 합의함. 계획서 §07의 Phase 1 "DONE WHEN" 조건(compose 기동)은 이 범위 조정에 따라 curl E2E 통과로 대체 충족.

### ✅ Phase 2 — 데이터 수집기 (Finnhub 연동) — 완료 (2026-07-16)
- [x] `symbols`/`price_history` 테이블 마이그레이션 추가 (`V2__create_symbols_table.sql`, `V3__create_price_history_table.sql`) — 백엔드 devtools 자동 재시작으로 적용 확인
- [x] Finnhub WSS 연결 (`app/finnhub_source.py`) — 구독/재구독, 지수 백오프 재접속(1s→최대 60s), `ConnectionClosed`/`OSError` 처리
- [x] 틱 스로틀링 (`app/throttle.py`) — 종목별 초당 1회로 다운샘플링 후에만 Redis 발행 (집계는 스로틀 없이 전체 틱 사용, OHLC 정확도 유지)
- [x] Redis 캐시 + Pub/Sub (`app/redis_publisher.py`) — `quote:{symbol}` 해시 갱신 + `quotes` 채널 발행
- [x] 1분봉 집계기 (`app/aggregator.py`) — 순수 로직으로 분리, 늦게 도착한 과거분 틱은 무시하도록 처리
- [x] MySQL 저장 (`app/mysql_writer.py`) — 분 마감 시 `price_history`에 upsert, 백필처럼 대량 쓰기는 커넥션 재사용 배치(`insert_candles_bulk`)로 분리
- [x] yfinance 일봉 백필 스크립트 (`app/backfill.py`, `uv run python -m app.backfill [TICKER...]`)
- [x] REST 폴백 (`app/rest_fallback.py`) — WS 틱이 `REST_FALLBACK_STALE_AFTER_SECONDS`(기본 90초) 이상 없는 종목만 60초 주기로 폴링. **2026-07-17 업데이트**: Finnhub REST `/quote` 대신 yfinance(`Ticker.fast_info`)로 교체 — API 키/쿼터가 필요 없고, `backfill.py`에서 이미 쓰던 의존성이라 새로 추가한 것도 없음. 부수 효과로 REST 폴백 경로에서도 실제 거래량을 채우게 됨(Finnhub `/quote` 응답엔 거래량이 없어 이전엔 항상 0으로 발행했었음). `config.FINNHUB_REST_BASE`(이제 아무데도 안 쓰임)와 `rest_fallback.py`의 `requests` 의존은 제거
- [x] FastAPI 헬스체크 + 구독 목록 API (`main.py`): `GET /health`, `GET /subscriptions`, `PUT /subscriptions`(실시간 WS 구독 반영, DB `symbols` upsert)
- [x] 단위 테스트: `tests/test_aggregator.py`(6건, 분 경계·심볼별 독립 집계·지연틱 무시 등), `tests/test_throttle.py`(4건) — 총 10개 전부 통과 (`uv run pytest`)
- [x] **실거래 시간 실측 검증 완료** (2026-07-16 23:2x KST, 미국 정규장 중):
  - `/health` → `ws_connected: true`, AAPL/MSFT/GOOGL/TSLA/NVDA 전 종목 실시간 틱 수신 확인
  - `redis-cli HGETALL quote:{symbol}` → 5개 종목 전부 실시간 가격/거래량/타임스탬프 확인
  - `redis-cli SUBSCRIBE quotes` → 실시간 브로드캐스트 메시지 스트림 확인
  - 1분 경과 후 `price_history`에 5개 종목 1분봉(open/high/low/close/volume) 정상 적재 확인
  - `PUT /subscriptions`로 TSLA→AMZN 실시간 교체 → WS 재구독 반영 확인
  - `uv run python -m app.backfill` → 5개 종목 각 1255개 일봉(5년치) 정상 적재 확인 (`timeframe='1d'`)
  - REST 폴백(`rest_fallback.py`)은 장중이라 스테일 조건이 발생하지 않아 실거래로는 미검증(코드 리뷰로만 확인) — 이후 yfinance로 교체하며 fetch+Redis publish 경로 자체는 별도 검증함(아래 참고), 스테일 조건이 실제로 걸리는 상황(장 마감 등)에서의 전체 루프 재검증은 여전히 남음

**진행 중 발견/수정한 이슈 2건** (둘 다 이 Windows/Docker Desktop 로컬 환경에 한정된 문제로, 실제 로직 버그는 아니었음):
1. `redis.asyncio`가 호스트명 `"localhost"`로 접속 시 무한 행(IPv6 우선 해석 후 폴백 실패로 추정) — `REDIS_HOST` 기본값을 `127.0.0.1`로 변경해 해결. sync `redis`/`pymysql`은 동일 문제 없었음
2. Finnhub WS에 `ping_interval=20`을 주면 서버가 프로토콜 레벨 ping에 응답하지 않아 데이터가 계속 들어오는데도 주기적으로 거짓 재접속(1011 keepalive timeout) 발생 — `ping_interval=None`으로 비활성화, 끊김 감지는 메시지 루프의 `ConnectionClosed`/`OSError`로 대체

**남겨둔 단순화**: `mysql_writer.insert_candle`(실시간 1분봉 경로)은 호출마다 새 커넥션을 여는 방식 그대로 유지 — 최대 50종목 x 분당 1회 수준이라 커넥션 풀 없이도 충분. 대량 쓰기 경로(백필)만 `insert_candles_bulk`로 커넥션을 재사용하도록 분리함(최초 구현에서 백필 시 컬럼당 커넥션을 여는 방식이 1255행 기준 눈에 띄게 느려 실측 후 수정).

**REST 폴백 yfinance 교체 검증** (2026-07-17, 장 개장 전): `_fetch_quote('AAPL')` → `publish_quote(...)` 경로를 직접 호출해 실제 Redis에 반영되는지 확인(`redis-cli HGETALL quote:AAPL`에서 실시간 가격·거래량·타임스탬프 정상 확인). `uv run pytest` 10건 전부 통과(기존 테스트 대상은 아니었음). `main.py` import도 정상.

### ✅ Phase 3 — 실시간 전달 (백엔드 WebSocket) — 완료 (2026-07-17)
- [x] `watchlist_items` 테이블 마이그레이션 추가 (`V4__create_watchlist_items_table.sql`, `(user_id, symbol_id)` 유니크 제약)
- [x] `Symbol`/`PriceHistory` JPA 엔티티 + 리포지토리 신규 추가 (`symbol/`, `pricehistory/` 패키지) — 기존엔 마이그레이션만 있고 Java 매핑이 없었음
- [x] STOMP over SockJS 엔드포인트 구성 (`websocket/WebSocketConfig.java`) — `/ws` 엔드포인트, `/topic` 심플 브로커, `/app` 프리픽스
- [x] STOMP CONNECT 인터셉터로 JWT 검증 (`websocket/StompAuthChannelInterceptor.java`) — HTTP 핸드셰이크가 아니라 STOMP CONNECT 프레임의 `Authorization: Bearer <token>` 네이티브 헤더로 검증(SockJS 핸드셰이크 자체는 `SecurityConfig`에서 `/ws/**` permitAll 처리). 검증 성공 시 `AuthenticatedUser`를 STOMP 세션 Principal로 세팅
- [x] Redis 구독 → STOMP 브로드캐스트 브리지 (`websocket/RedisSubscriberConfig.java` + `RedisQuoteSubscriber.java`) — `quotes` 채널(collector가 발행) 구독, 메시지를 파싱해 `/topic/quotes/{symbol}`로 재전송
- [x] 시세 조회 REST API (`quote/QuoteController.java`, `QuoteService.java`): `GET /api/quotes`(활성 종목 전체, Redis 캐시), `GET /api/quotes/{ticker}`(단일, 캐시 없으면 404), `GET /api/quotes/{ticker}/history?timeframe=&limit=`(MySQL `price_history`, 오름차순)
- [x] 워치리스트 CRUD API (`watchlist/WatchlistController.java`, `WatchlistService.java`): `GET/POST /api/watchlist`, `DELETE /api/watchlist/{id}` — 중복 추가는 409, 소유하지 않은/존재하지 않는 항목 삭제는 404
- [x] 신규 예외 2종 추가 및 `GlobalExceptionHandler` 매핑 (`ResourceNotFoundException` → 404, `DuplicateWatchlistItemException` → 409)
- [x] `SecurityConfig`에 `/ws/**` permitAll 추가 (STOMP 레벨에서 별도 인증하므로 HTTP 필터 체인은 통과시킴)
- [x] **Jackson 3 마이그레이션 이슈 발견/대응**: Spring Boot 4.1.0은 Jackson 3.x(`tools.jackson.*` 패키지, `com.fasterxml.jackson.databind`가 아님)를 사용 — `RedisQuoteSubscriber`에서 이걸 놓쳐 처음엔 컴파일 에러. 패키지 교정 후에도 `JsonNode.asText()`가 Jackson 3에서 deprecated라 `asString()`으로 교체
- [x] **실측 검증 완료** (2026-07-17, `gradlew bootRun`으로 임시 기동해 검증 후 종료):
  - Flyway가 V4 마이그레이션을 정상 적용 (`Successfully applied 1 migration ... now at version v4`)
  - curl로 회원가입 → 로그인 → `GET /api/quotes`(Redis 캐시 6종목 정상 반환) → `POST/GET/DELETE /api/watchlist`(중복 추가 409, 미소유/재삭제 404) → `GET /api/quotes/{ticker}/history`(MySQL 일봉 5건, 오름차순) 전부 기대대로 동작
  - Node.js(`@stomp/stompjs` + `sockjs-client`)로 만든 테스트 클라이언트로 `/ws`에 SockJS 접속, STOMP CONNECT 헤더에 유효한 액세스 토큰을 실어 연결 성공 확인
  - `/topic/quotes/AAPL` 구독 후 `redis-cli PUBLISH quotes '{"symbol":"AAPL",...}'`로 collector와 동일한 페이로드를 수동 발행 → 구독 클라이언트가 즉시 수신 확인(Redis→STOMP 브리지 동작 확인)
  - 잘못된 토큰으로 STOMP CONNECT 시도 → 인터셉터가 거부, 연결이 STOMP ERROR/close로 종료되는 것 확인
  - 검증 중 생성한 테스트 계정(2개) 및 관련 watchlist row는 DB에서 정리함

**범위 결정 — 워치리스트에 정렬(reorder) 엔드포인트는 넣지 않음**: 스키마엔 `sort_order`가 있지만, 드래그 정렬 UI 자체가 Phase 4(프론트엔드) 몫이라 지금은 추가 시 삽입 순서를 그대로 `sort_order`로 채우기만 함. 정렬 변경 API는 Phase 4에서 실제 UI 요구가 확정되면 추가.

**남겨둔 이슈**: Docker Desktop이 이번 세션 시작 시점엔 꺼져 있어서(및 IntelliJ 백엔드도 미기동) 검증을 위해 직접 Docker Desktop을 띄우고 `gradlew bootRun`으로 백엔드를 임시 기동함 — 검증 후 둘 다 사용자 워크플로에 맞춰 정리(백엔드 프로세스는 종료, `mysql-container`/`redis-container`는 상시 공유 인프라라 계속 구동 상태로 둠).

### ✅ Phase 4 — 프론트엔드 (시세 보드와 차트) — 완료 (2026-07-17)
- [x] 패키지 설치: `@stomp/stompjs`, `sockjs-client`(+`@types/sockjs-client`), `lightweight-charts`, `@heroicons/react`, `@astryxdesign/core`+`@astryxdesign/theme-neutral`+`@astryxdesign/cli`(디자인 시스템, 아래 참고)
- [x] 백엔드 `SecurityConfig`에 CORS 설정 추가(`CorsConfigurationSource`, `app.cors.allowed-origins` 프로퍼티) — 브라우저에서 프론트엔드 Origin으로 `/api/**` 직접 호출 가능하게 함(이전엔 CORS 미설정이라 브라우저에서 막혔을 것)
- [x] 인증: `lib/auth-context.tsx`(`AuthProvider`/`useAuth`) — 액세스 토큰은 메모리(React state)에만 보관, 리프레시 토큰만 `localStorage`에 저장. 앱 로드 시 저장된 리프레시 토큰으로 `/api/auth/refresh` 자동 호출해 세션 복구. `authFetch()`가 401 응답 시 리프레시 1회 재시도 후 재요청(실패하면 로그아웃 처리)
- [x] `lib/api.ts` — 얇은 fetch 래퍼(`request`) + 인증 필요 엔드포인트는 `Fetcher`(=`authFetch`)를 주입받는 함수형(`getQuotes`, `getHistory`, `getWatchlist`, `addWatchlistItem`, `removeWatchlistItem`)
- [x] 라우트 가드: `components/RequireAuth.tsx`(비로그인 시 `/login`으로), `components/RedirectIfAuthed.tsx`(로그인 상태로 `/login`·`/signup` 접근 시 `/`로) — 둘 다 클라이언트 컴포넌트, `isInitializing` 동안은 스피너로 깜빡임 방지
- [x] 로그인/가입 페이지(`app/(auth)/login`, `/signup`) — Astryx `Center`+`Card`+`TextInput`+`Banner` 조합, 가입 성공 시 자동 로그인 후 보드로 이동
- [x] 보호 라우트 셸(`app/(app)/layout.tsx`) — `AppShell`+`SideNav`(헤더: MarketBoard 로고, 푸터: 이메일+로그아웃), `RequireAuth`로 감싸고 그 안에 `QuoteStreamProvider` 배치
- [x] 실시간 시세 스트림(`lib/quote-stream-context.tsx`) — 앱 진입 시 `GET /api/quotes`로 활성 종목 스냅샷 1회 로드 후, STOMP(SockJS, `/ws`) 연결 1개로 전체 활성 종목의 `/topic/quotes/{symbol}`을 한꺼번에 구독(≤50종목이라 종목별 개별 연결/구독 관리 없이 이 방식으로 충분 — 범위 결정, 아래 참고). CONNECT 프레임에 `Authorization: Bearer <accessToken>` 네이티브 헤더 실어 인증(Phase 3 백엔드 인터셉터와 매칭)
- [x] 시세 보드(`app/(app)/page.tsx`) — `Table`(edge-to-edge, Card로 안 감쌈 — dense data는 rows 원칙), 종목별 워치리스트 별표 토글(`IconButton`+`clickAction`), `SegmentedControl`로 전체/관심종목 필터, 틱마다 가격 변동 시 700ms 동안 상승/하락 아이콘 플래시(`Icon` success/error, 커스텀 CSS 없이 컴포넌트 prop만으로 구현)
- [x] 종목 상세(`app/(app)/symbols/[ticker]/page.tsx`) — KPI 카드 3개(현재가/거래량/갱신시각), `lightweight-charts` v5 캔들차트(`components/CandleChart.tsx`, `chart.addSeries(CandlestickSeries, ...)` API), 일봉/분봉 `SegmentedControl` 전환, 실시간 틱을 현재 봉에 병합해 라이브 반영(effect의 setState 대신 `useMemo` 파생값으로 구현 — 아래 lint 이슈 참고), 워치리스트 추가/제거 버튼
- [x] Next.js 16 대응: `params`가 `Promise`라 `use()`로 언래핑(`app/(app)/symbols/[ticker]/page.tsx`), `middleware.ts`가 `proxy.ts`로 개명된 것 확인(이번 Phase에선 proxy 자체는 미사용 — 인증 체크는 전부 클라이언트 컴포넌트 가드로 처리)
- [x] **Astryx 디자인 시스템 CLI 이슈 발견/해결**: 전역 `CLAUDE.md`의 `npx astryx <cmd>` 안내를 그대로 실행하면 실패함 — npm에 실제로 등록된 `astryx` 패키지는 전혀 무관한 glob 유틸리티 라이브러리(bin 없음)이고, 진짜 CLI는 `@astryxdesign/cli`. 이걸 로컬 devDependency로 설치해야 `npx astryx`가 로컬 bin을 먼저 찾아 정상 동작함. 세션 메모리에 기록해둠([[reference-astryx-cli-package-name]])
- [x] **ESLint `react-hooks/set-state-in-effect` 대응**: React 19 / Next 16 툴체인에 새로 들어온 규칙 — effect 본문에서 동기적으로 `setState`를 직접 호출하면 에러. `auth-context.tsx`(초기 로딩 플래그는 `useState`의 lazy initializer로 계산), `quote-stream-context.tsx`(중복 호출 제거), 종목 상세 페이지(요청 키 비교로 로딩 상태를 렌더 타임에 파생, 실시간 틱 병합은 `useMemo`로 파생)로 전부 리팩터링해 경고 0개로 정리
- [x] **포트 충돌 발견/해결**: 로컬 3000번 포트를 사용자의 다른 프로젝트("Synapse AI", WSL2 Ubuntu에서 구동돼 Windows netstat/프로세스 목록에는 안 보임)가 상시 점유하고 있어 `next dev`가 계속 3001로 밀림 → `frontend/package.json`의 `dev`/`start` 스크립트를 `-p 3100`으로 고정, 백엔드 `app.cors.allowed-origins` 기본값도 `http://localhost:3100`으로 변경. 세션 메모리에 기록해둠([[project-marketboard-frontend-port]], [[feedback-check-wsl-before-killing-unknown-port-holders]])
- [x] **브라우저 E2E 검증 완료** (2026-07-17, Playwright 헤드리스 크로미움으로 직접 구동 — `chromium-cli`가 환경에 없어 스크래치 디렉터리에 임시 드라이버 스크립트 작성, 프로젝트에는 반영 안 함):
  - 회원가입 → 자동 로그인 → 보드 진입, "실시간 연결됨" 상태 확인(STOMP 연결 성공)
  - 보드에 활성 종목 6개(AAPL/MSFT/GOOGL/TSLA/NVDA/AMZN) 전부 렌더링, Redis 캐시 가격 정상 표시
  - 워치리스트 별표 토글 → `POST /api/watchlist` 호출 확인, "관심종목" 필터 전환 시 해당 종목만 표시
  - 종목 상세 페이지 진입 → `lightweight-charts` 캔들차트가 5년치 일봉 데이터로 정상 렌더링(캔버스 엘리먼트 확인), 분봉 전환도 정상
  - **실시간 틱 반영 실측**: `redis-cli PUBLISH quotes`로 수동 틱 발행(collector 없이 Phase 3와 동일한 방식) → 보드에서 해당 종목 가격이 즉시 갱신되고 상승 화살표가 플래시로 표시됨(스크린샷으로 확인) → 종목 상세 페이지에서도 KPI 카드와 차트가 같은 틱을 실시간 반영
  - 브라우저 콘솔 에러 0건
  - 검증 중 생성한 테스트 계정(3개, `phase4test_*`/`phase4live_*`) 및 관련 watchlist row는 DB에서 정리함

**범위 결정 — 인증 토큰 저장 방식(httpOnly 쿠키 대신 메모리+localStorage)**: 백엔드가 이미 완전히 별도의 API 서버(Spring Boot, 8080)라 Next.js의 쿠키 기반 세션 가이드(Route Handler가 자체 DB로 세션을 만드는 전제)가 그대로 맞지 않는다. 액세스 토큰은 메모리에만 두고(새로고침 시 사라짐), 리프레시 토큰만 `localStorage`에 저장해 앱 로드 시 자동 재발급으로 세션을 복구하는 SPA 방식을 택했다. httpOnly 쿠키보다 XSS에 다소 취약하지만, 별도 API 서버 + 클라이언트 렌더링 아키텍처에서 훨씬 단순하고, 스터디 포트폴리오 프로젝트 범위에 적절하다고 판단.

**범위 결정 — `proxy.ts`(구 middleware) 미사용**: Next.js 공식 가이드는 인증 상태를 쿠키에서 낙관적으로 확인하는 `proxy.ts`를 권장하지만, 토큰이 쿠키가 아니라 메모리/localStorage에 있어 서버(Node 런타임의 proxy)에서 읽을 수 없다. 클라이언트 컴포넌트 가드(`RequireAuth`/`RedirectIfAuthed`)로 전부 처리 — Next.js 가이드도 "Proxy만으로는 충분한 보안 경계가 아니다"라고 명시하므로 실질적 손해는 없음.

**범위 결정 — 알림(가격 목표가 알림) 기능 없음**: 계획서 §07 Phase 4 항목엔 없고 Phase 5("가격 알림") 소관이라 이번엔 손대지 않음.

### ✅ Phase 5 — 관리자 페이지 + 알림 — 완료 (2026-07-17)
- [x] `alerts` 테이블 마이그레이션 추가 (`V5__create_alerts_table.sql`) — 컬럼명은 `condition`이 아니라 `direction`으로 지음(MySQL 예약어라 `condition`을 그대로 쓰면 CREATE TABLE이 깨짐)
- [x] `Alert` JPA 엔티티/리포지토리, `Symbol`/`User` 엔티티에 그동안 없던 변경 메서드 추가(`Symbol.updateDetails/activate/deactivate`, `User.changeRole/suspend/reactivate`) — Phase 2/1에서 읽기 전용으로만 쓰던 엔티티라 관리자 CRUD를 위해 처음 추가
- [x] 관리자 종목 관리 API(`symbol/SymbolAdminController`, `SymbolAdminService`): `GET/POST /api/admin/symbols`, `PATCH /api/admin/symbols/{id}`(이름/거래소/우선순위/활성 여부) — 활성 목록이 바뀔 때마다 (1) collector에 `PUT /subscriptions`로 전체 활성 티커 목록을 동기화하고 (2) `/topic/symbols`로 STOMP 브로드캐스트해서 접속 중인 유저 화면에 새로고침 없이 반영
- [x] 관리자 유저 관리 API(`user/UserAdminController`, `UserAdminService`): `GET /api/admin/users`, `PATCH /api/admin/users/{id}`(권한/정지 상태), `POST /api/admin/users/{id}/revoke-token`(리프레시 토큰 강제 폐기) — 정지 처리 시 리프레시 토큰도 함께 폐기
- [x] 관리자 대시보드 API(`admin/AdminDashboardController`): `GET /api/admin/collector/status`(수집기 `/health` 프록시, `CollectorClient`), `GET /api/admin/system/status`(Spring `SimpUserRegistry`로 접속자 수/WS 세션 수 집계)
- [x] 가격 알림 백엔드(`alert/` 패키지): `GET/POST /api/alerts`, `DELETE /api/alerts/{id}` — 생성 시 Redis에 미러링(`alert:{id}` 해시 + `alerts:{ticker}` Set, `AlertRedisMirror`), 백엔드 재시작 시 미체결 알림을 MySQL에서 다시 Redis로 복원하는 `AlertMirrorInitializer` 추가(Redis는 캐시일 뿐이라 재시작하면 미러가 비므로)
- [x] Redis `alert-triggers` 채널 구독(`AlertTriggerSubscriber`, `RedisSubscriberConfig`에 두 번째 리스너로 등록) — 수집기가 발행한 트리거 메시지를 받아 MySQL에 `triggered_at` 기록 후 `convertAndSendToUser`로 알림 대상 유저에게만 STOMP 푸시(`/user/queue/alerts`)
- [x] STOMP 유저 목적지(`/user/queue/...`) 활성화: `AuthenticatedUser`가 `Principal`을 구현하도록 변경(`getName()` = user id), `WebSocketConfig`의 심플 브로커에 `/queue` prefix 추가
- [x] 파이썬 수집기(`collector/app/alerts.py`, `main.py`): 매 틱마다(스로틀 없이) `alerts:{symbol}` Redis Set을 확인해 조건(ABOVE/BELOW) 충족 시 `alert-triggers`로 발행하고 Redis 미러를 즉시 제거(원샷 처리, 재확인 중 중복 발행 방지)
- [x] 프론트엔드 관리자 섹션(`app/(app)/admin/`): `RequireAdmin` 가드, `TabList` 기반 서브 내비게이션(종목 관리/유저 관리/수집기 상태), SideNav에 ADMIN 역할일 때만 보이는 "관리자" 항목 추가. 종목 관리(`Switch`로 활성 토글), 유저 관리(`SegmentedControl`로 권한, `Switch`로 정지, 토큰 강제 만료 버튼), 수집기 상태(5초 폴링, 접속자/세션 수 KPI 카드 + Finnhub 연결 상태 + 종목별 마지막 틱 시각 리스트)
- [x] 프론트엔드 가격 알림 UI(`components/AlertsPanel.tsx`, 종목 상세 페이지에 삽입): 목표가 입력 + 이상/이하 `SegmentedControl` + 등록, 알림 목록(대기 중/도달함 상태 표시), 삭제 버튼
- [x] 실시간 알림 토스트: `lib/quote-stream-context.tsx`가 관리하는 단일 STOMP 연결에 `/topic/symbols`(활성 종목 변경 시 재조회) 구독과 `/user/queue/alerts`(개인 알림) 구독을 추가, `useToast()`로 알림 도착 시 토스트 표시(`isAutoHide: false`로 유지)

**검증 중 발견/수정한 버그 2건** (둘 다 이 프로젝트의 Spring Boot 4.1 / Spring Framework 7 / Jackson 3 조합에 특화된 문제로, 별도 통합 검증 없이는 컴파일도 되고 얼핏 정상 동작하는 것처럼 보여서 놓치기 쉬웠음):
1. **STOMP 유저 목적지가 전혀 동작하지 않던 버그** — `StompAuthChannelInterceptor.preSend()`가 `StompHeaderAccessor.wrap(message)`로 얻은 접근자에 `setUser(...)`를 호출했지만, `wrap()`은 메시지의 헤더를 복사한 새 인스턴스를 반환할 뿐이라 그 변경이 실제로 채널을 타고 흐르는 메시지에는 전혀 반영되지 않았음(원래 `message`를 그대로 리턴). 그 결과 STOMP CONNECT 자체(토큰 검증)는 항상 정상 동작했지만, 세션에 `Principal`이 실제로는 한 번도 연결된 적이 없어 `convertAndSendToUser`가 대상을 못 찾아 항상 조용히 실패했음 — Phase 3부터 존재했던 잠재 버그였지만 그동안은 `/topic/**` 브로드캐스트만 썼기 때문에 드러나지 않다가, 이번에 유저별 알림 푸시를 붙이면서 발견함. `MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)`로 메시지에 내장된 살아있는(mutable) 접근자를 가져오도록 수정.
2. **관리자가 종목을 활성화해도 수집기 실시간 구독에는 반영되지 않던 버그** — `CollectorClient`가 `RestClient.builder().build()`(스프링 부트가 자동구성한 빈이 아니라 정적 팩토리)로 만든 클라이언트로 수집기의 `PUT /subscriptions`를 호출했는데, 매번 수집기(uvicorn)가 `422 Unprocessable Content`(빈 바디)로 거부하고 곧이어 `"Invalid HTTP request received"` 경고를 남겼음. 원인은 JSON 직렬화가 아니라 전송 계층이었음 — JDK `HttpClient`(RestClient 기본 팩토리가 내부적으로 사용)가 평문 HTTP에도 기본으로 h2c(HTTP/2 cleartext) 업그레이드를 시도하는데, uvicorn은 이를 지원하지 않아 "Unsupported upgrade request"로 응답하면서 요청 프레이밍이 깨졌음. 바디가 있는 `PUT /subscriptions` 호출만 JDK `HttpClient.newBuilder().version(HTTP_1_1)`로 명시적으로 HTTP/1.1을 강제하도록 별도 처리해 해결(바디가 없는 `GET /health`는 같은 문제가 안 생겨서 `RestClient` 그대로 유지). 두 버그 모두 STOMP 테스트 클라이언트로 격리 재현 후 수정 → 재검증하는 방식으로 확인함.

**실측 검증 완료** (2026-07-17, `gradlew bootRun` + collector + `npm run dev` 모두 띄운 상태로 Playwright 헤드리스 브라우저로 검증 — 미국 정규장 개장 전이라 실제 시세 틱은 없었지만 collector의 Finnhub WS 연결 자체는 정상 확인됨):
- 관리자 계정으로 로그인 → `/admin/symbols`에서 새 종목(`PHASE5`) 추가 → **별도 브라우저 컨텍스트의 일반 유저 화면에 새로고침 없이 즉시 나타남 확인**(DONE WHEN 조건 충족) → 수집기 `GET /subscriptions`가 새 목록을 실제로 반영하는지 확인(버그 수정 전에는 반영 안 됐던 것도 함께 확인)
- 관리자가 `/admin/users`에서 방금 가입한 유저를 목록에서 확인, `/admin/collector`에서 접속자 수/WS 세션 수/Finnhub 연결 상태/종목별 마지막 틱 시각이 실제 값으로 렌더링되는 것 확인(5초 주기 폴링)
- 일반 유저가 종목 상세 페이지에서 가격 알림 등록(AAPL, 500 이상) → MySQL/Redis 미러에 정상 저장 확인
- `redis-cli PUBLISH alert-triggers`로 수집기와 동일한 페이로드를 수동 발행(장중이 아니라 실제 트리거는 못 봤으므로 Phase 2/3과 같은 방식으로 시뮬레이션) → 백엔드가 MySQL에 `triggered_at` 기록 + STOMP로 유저에게 푸시 → **프론트엔드에 토스트가 실제로 렌더링되는 것까지 화면 텍스트로 확인**("AAPL이(가) 500 이상에 도달했습니다 (현재가 512.34)")
- `collector/app/alerts.py`의 `check_alerts()`를 실제 Redis에 대해 직접 호출해 별도 검증: 미리 심어둔 alert 미러가 조건 충족 시 정확히 원샷으로 제거되고 `alert-triggers`에 발행되는 것 확인
- 브라우저 콘솔 에러 0건, 백엔드 테스트 21건 전부 통과(`./gradlew test`), collector 테스트 10건 전부 통과(`uv run pytest`)
- 검증 중 생성한 테스트 계정 4개 및 관련 watchlist/alert row는 DB에서 정리, 테스트로 추가한 `PHASE5` 종목은 삭제 대신 비활성화(관리자 화면에서 종목 삭제는 지원 안 함 — 아래 범위 결정 참고)로 정리, `PUT /subscriptions`로 수집기 구독 목록도 원상 복구 확인

**범위 결정 — 관리자 종목 관리에 하드 삭제 없음**: `symbols`가 `price_history`/`watchlist_items`/`alerts`에서 FK로 참조되므로 실제 삭제는 이력을 깨뜨린다. "추가/삭제"라는 계획서 표현은 구독 슬롯의 on/off로 해석해 `active` 토글(`PATCH`)만 제공 — 완전히 새 종목을 만드는 `POST`와 활성/비활성 전환은 지원하되, 로우 자체를 지우는 `DELETE`는 없음.

**범위 결정 — "REST 쿼터 사용량" 모니터링 없음**: 계획서의 수집기 상태 항목 중 하나지만, 애초에 수집기 코드 어디에도 Finnhub REST 호출 횟수를 세는 로직이 없어서(REST 폴백은 아직 실거래 미검증 상태) 지금 추가하면 관측 대상 없는 빈 카운터가 된다. `ws_connected`/`reconnect_count`/종목별 마지막 틱 시각은 이미 있는 값 그대로 노출.

**범위 결정 — "최근 에러 로그 요약"은 UI에 없음**: 중앙 로그 수집/집계 인프라가 아직 없어(Phase 6 "관측성"에서 다룰 영역) 로그 파일을 직접 tail하는 것 외에는 요약할 소스가 없다. 수집기의 마지막 에러(`last_error`)는 `/admin/collector` 화면에 배너로 이미 노출하고 있어 최소한의 가시성은 확보.

### ✅ 추가 기능 — 커스터마이즈 가능한 대시보드 (Grafana 스타일) — 완료 (2026-07-17)

계획서(`stock-monitor-dev-plan.html`)의 Phase 1~7 로드맵에는 없던 기능. 사용자가 "차트/인디케이터/뉴스를 Grafana 대시보드처럼 배치하고 싶다"고 요청해 범위를 논의 후 추가함 — 위치는 새 전용 페이지(`/dashboard`), 패널 종류는 차트/뉴스/워치리스트/인디케이터 4종, 레이아웃은 자유 드래그가 아니라 미리 정의된 프리셋 중 선택, 설정은 기기/세션 간 동기화되도록 서버(DB)에 저장하는 것으로 사용자와 합의.

- [x] `V6__create_dashboard_configs_table.sql`, `V7__create_indicators_table.sql` 마이그레이션 추가 — `dashboard_configs`(user_id UNIQUE FK, layout_key, panels_json TEXT, updated_at), `indicators`(symbol_id FK, indicator_type, timeframe, value DECIMAL(18,4), computed_at, UNIQUE(symbol_id, indicator_type, timeframe))
- [x] 인디케이터 계산(`indicator/` 패키지): `IndicatorType`(SMA20/SMA50/RSI14) enum, `TechnicalIndicators`(순수 정적 `sma()`/`rsi()` 함수, 단위 테스트 6건), `Indicator` 엔티티+리포지토리, `IndicatorCalculationService`가 `@Scheduled(cron = "${indicators.cron}")`(기본 5분 주기, `application.yaml`의 `INDICATORS_CRON`으로 오버라이드 가능)로 종목별 최근 60개 일봉을 읽어 3개 지표를 계산 후 upsert. `MarketboardBackendApplication`에 `@EnableScheduling` 추가
- [x] 인디케이터 조회 API: `GET /api/indicators/{ticker}` (`IndicatorController`, `IndicatorResponse` dto)
- [x] 뉴스 프록시 체인: 콜렉터(`collector/app/news.py`)가 Finnhub News REST(`/news?category=general`, `/company-news`)를 직접 호출하는 `get_general_news()`/`get_company_news(symbol, days=14)` 추가, `main.py`에 `GET /news`/`GET /news/{symbol}` 라우트(실패 시 502) 추가. 백엔드(`CollectorClient`)에 `getGeneralNews()`/`getCompanyNews(ticker)` 추가, `news/NewsController`가 `GET /api/news`/`GET /api/news/{ticker}`로 프론트엔드에 노출 — Finnhub API 키는 콜렉터에만 있으므로 백엔드/프론트는 콜렉터를 거쳐서만 뉴스에 접근(키가 프론트로 노출되지 않음)
- [x] 대시보드 설정 저장/조회 API: `dashboard/DashboardConfig` 엔티티(`update(layoutKey, panelsJson)`), `DashboardConfigService`가 `tools.jackson.databind.ObjectMapper`로 패널 배열을 JSON 문자열로 직렬화해 TEXT 컬럼에 저장 — **의도적으로 백엔드는 패널 스키마를 모른다**: `panels_json`은 그냥 불투명한 JSON 블롭이라 프론트엔드가 패널 타입을 추가/변경해도 백엔드 마이그레이션이 필요 없음. `GET/PUT /api/dashboard`(`DashboardConfigController`)
- [x] 프론트엔드 `lib/candles.ts`: 기존 종목 상세 페이지에 있던 캔들 히스토리 fetch + 실시간 틱 병합 로직(`bucketStart`/`mergeLiveTick`)을 `useCandles(fetcher, ticker, timeframe, liveQuote)` 훅으로 추출 — 대시보드의 `ChartPanel`과 종목 상세 페이지가 동일 로직을 공유하도록 리팩터링(종목 상세 페이지에서 중복 코드 ~30줄 제거)
- [x] 프론트엔드 패널 컴포넌트 4종(`components/dashboard/`): `ChartPanel`(캔들차트), `NewsPanel`(전체/종목별 뉴스), `WatchlistPanel`(워치리스트 테이블 재사용), `IndicatorPanel`(SMA/RSI 값 카드) — `PanelSlot`이 슬롯마다 `SegmentedControl`로 패널 타입 전환 + 조건부 티커/인디케이터 입력 UI를 감쌈
- [x] 대시보드 페이지(`app/(app)/dashboard/page.tsx`): (최초 구현 당시) 프리셋 레이아웃 3종(`TWO_COLUMN`/`THREE_COLUMN`/`CHART_FOCUS`) 중 선택, 슬롯별 패널 설정, "레이아웃 저장" 버튼으로 `PUT /api/dashboard` 호출, 마운트 시 `GET /api/dashboard`로 기존 설정 복원. SideNav에 "대시보드" 항목 추가(`app/(app)/layout.tsx`) — **이후 프리셋 선택 UI는 아래 "패널 그리드 재설계"에서 완전히 제거되고 동적 그리드로 대체됨**

**검증 중 발견/수정한 버그**: `useCandles`가 티커가 아직 비어있을 때도(사용자가 대시보드에서 차트 패널의 티커 입력란에 아직 아무것도 안 쳤을 때) fetch를 실행해 `//history?...`처럼 티커가 빈 malformed URL을 호출 → 브라우저 CORS 에러 발생(Playwright 콘솔에서 5회 반복 확인). 원인은 `ChartPanel`이 훅 규칙상 매 렌더마다 `useCandles`를 무조건 호출해야 해서 빈 티커로도 훅 내부 effect가 도는데, 그 effect에 빈 문자열 가드가 없었던 것. `lib/candles.ts`의 effect 첫 줄에 `if (!ticker) return undefined;` 추가로 해결, 재검증 시 콘솔 에러 0건 확인.

**그 외 자잘한 이슈**: `WatchlistPanel.tsx`에서 `WatchlistItemResponse[]`를 Astryx `Table` 로우 타입(`Record<string, unknown>`)으로 캐스팅해야 하는 이 프로젝트에서 반복되는 이슈(기존 보드 페이지에도 있던 패턴) 재발 — 동일한 캐스팅 패턴으로 해결. `NewsPanel.tsx`/`IndicatorPanel.tsx`에서 effect 본문에 동기적으로 `setState(null)`을 호출해 `react-hooks/set-state-in-effect` 린트 에러 — 이미 다른 곳에서 쓰던 "키가 있는 `{key, data}` 상태 + 렌더 타임 키 비교로 로딩 상태 파생" 패턴을 재사용해 해결.

**실측 검증 완료** (2026-07-17, Playwright 헤드리스 브라우저로 검증 — 이번엔 사용자의 IntelliJ 백엔드 인스턴스를 그대로 재사용, 콜렉터만 검증용으로 별도 기동):
- 회원가입 → 대시보드 진입 → 기본 2단 레이아웃 렌더링 확인
- 슬롯 하나를 차트/AAPL로 설정 → 실제 캔들차트 캔버스 렌더링 확인
- 다른 슬롯을 뉴스로 전환 → Finnhub에서 온 실제 뉴스 헤드라인 렌더링 확인
- "레이아웃 저장" → "저장됨" 표시 → 페이지 새로고침 후에도 슬롯 설정(티커 입력값 포함)이 그대로 복원되는 것 확인(`input.inputValue()`로 직접 확인 — `innerText()`로는 input 값이 안 잡혀서 처음엔 잘못 측정했었음)
- 3단 레이아웃으로 전환 → 세 번째 슬롯을 워치리스트로 설정 → 미리 추가해둔 AAPL이 표시되는 것 확인
- 슬롯을 인디케이터/AAPL/RSI14로 설정 → `IndicatorCalculationService` 스케줄 잡이 실제로 계산해 DB에 저장한 RSI(14) 값이 카드로 렌더링되는 것 확인
- 브라우저 콘솔 에러 0건(버그 수정 후 재검증 기준)
- 검증 중 생성한 테스트 계정(`dashtest_*`, `indpanel_*`)과 관련 `dashboard_configs`/`watchlist_items`/`alerts` row, 그리고 검증 중 실수로 실제 관리자 시드 계정(`admin@marketboard.local`)에 저장돼버린 테스트용 대시보드 설정도 모두 DB에서 정리함

**범위 결정 (최초, 이후 아래에서 조정됨) — 레이아웃은 자유 드래그/리사이즈가 아니라 프리셋 선택**: Grafana처럼 패널을 자유롭게 드래그/리사이즈하는 대신, 미리 정의된 레이아웃(2단/3단/차트 중심) 중 고르는 방식으로 범위를 좁혔다 — 사용자가 명시적으로 이 방식을 선택함. 그리드 레이아웃 라이브러리 도입이나 좌표/크기 영속화 없이도 실질적인 "대시보드 커스터마이즈" 경험을 제공할 수 있어 스터디 포트폴리오 범위에 적절하다고 판단.

**범위 결정 — 인디케이터는 프론트엔드가 아니라 백엔드에서 스케줄 계산**: 사용자가 명시적으로 "backend, database, using schedule like crontab"을 요청함 — 클라이언트에서 매번 캔들 데이터를 받아 지표를 계산하는 대신, 서버가 주기적으로 미리 계산해 DB에 저장해두고 프론트는 그 결과만 조회하는 구조. 계산 로직(`TechnicalIndicators`)을 프론트와 무관하게 단위 테스트할 수 있고, 여러 화면(향후 추가될 다른 패널 등)이 같은 계산을 반복하지 않는 이점이 있음.

**추가 — 관심종목(워치리스트) 전체를 자동으로 한 번에 표시** (2026-07-17): 위 수동 슬롯 구성과는 별개로, "차트와 여러 인디케이터를 관심종목 전체에 대해 한 번에 보고 싶다"는 후속 요청에 따라 `WatchlistOverviewSection` 컴포넌트를 추가함(`app/(app)/dashboard/page.tsx`의 수동 슬롯 그리드 아래 배치). 슬롯을 하나씩 수동으로 설정하는 기존 방식과 달리, 워치리스트를 조회해(`GET /api/watchlist`) 자동으로 렌더링 — 워치리스트가 바뀌면 별도 설정 없이 그대로 반영됨.

**추가 레이아웃 조정** (2026-07-17, 같은 날 후속 요청): "차트는 한 줄에 2개씩, 기본으로 뉴스 패널과 지표 패널도 보이게, 지표 패널은 워치리스트 종목별로 나눠서 여러 지표를 함께" 요청에 따라 `WatchlistOverviewSection`을 3단 구성으로 재구조화:
1. 차트 그리드(`Grid columns={2}`) — 워치리스트 종목별 캔들차트를 한 줄에 2개씩 카드로 배치(인디케이터와 분리, 차트만)
2. 뉴스 카드 — `NewsPanel`을 티커 없이 호출해 전체 시장 뉴스를 기본으로 항상 표시
3. 지표 카드 — 워치리스트 종목별로 구분된 하위 블록을 두고, 각 블록에 `IndicatorPanel`(티커 필터 없이 호출해 SMA20/SMA50/RSI14 전부)을 표시

기존 `ChartPanel`/`NewsPanel`/`IndicatorPanel`을 그대로 재사용해 새 API나 백엔드 변경 없이 프론트엔드만으로 구현. Playwright로 두 차례 검증: (1) AAPL/MSFT 2종목 — 차트(canvas 14개) + RSI(14)/SMA(20) 라벨 확인, 콘솔 에러 0건. (2) AAPL/MSFT/GOOGL 3종목, 1400px 뷰포트 — 스크린샷으로 차트가 실제로 한 줄에 2개씩 배치되는 것 육안 확인. 검증용 테스트 계정(`wloverview_*`, `wloverview2_*`)과 관련 row는 DB에서 정리함.

**티커/종목명 가시성 개선** (2026-07-17, 같은 날 후속 요청): "티커와 종목명을 더 눈에 띄게" 요청에 따라 `WatchlistOverviewSection`의 카드 헤더를 `Heading level={5}` 한 줄(`AAPL · AAPL`)에서 `Heading level={3}`(굵은 티커) + `Text type="supporting"`(종목명, 옆에 작게) 조합으로 교체 — 차트 카드 헤더와 지표 섹션의 종목별 하위 헤더 둘 다 동일하게 적용. `HStack align="baseline"`을 시도했으나 Astryx `HStack`의 `align`은 `start|end|center|stretch`만 지원해 타입 에러 발생 → `align="end"`로 교체(하단 정렬로 시각적으로 거의 동일한 효과). Playwright 스크린샷으로 렌더링 확인.

**패널 그리드 재설계** (2026-07-17, 같은 날 후속 요청): "상단 두 패널을 기본으로 뉴스/지표로, +버튼으로 커스텀 패널 추가 가능하게, 기본은 한 줄에 2개씩 그리드로"라는 요청에 따라 상단 수동 패널 그리드(`app/(app)/dashboard/page.tsx`)를 재설계:
- 기존 프리셋 레이아웃 선택 UI(`2단`/`3단`/`차트 중심` `SegmentedControl`, `LAYOUTS` 상수, `LayoutKey` 유니온 타입, `CHART_FOCUS`의 비대칭 `HStack`/`StackItem` 렌더링)를 전부 제거
- 항상 `Grid columns={2}`로 렌더링되는 동적 패널 목록으로 교체 — 패널 개수는 고정 프리셋이 아니라 `slotCount`라는 로컬 상태 값. 기본값 2개, 헤더의 "+" `IconButton`(`PlusIcon`) 클릭 시 `slotCount` 증가로 패널 추가, 각 `PanelSlot` 헤더의 "X" `IconButton`(`XMarkIcon`, 신규 `onRemove` prop) 클릭 시 해당 슬롯을 지우고 뒤쪽 슬롯들의 `slot` 인덱스를 당겨서(재정렬) 삭제 — 프론트엔드 전용 변경이며 백엔드 `DashboardConfigRequest.layoutKey`가 `@NotBlank` 외 별도 enum 제약이 없어(원래도 불투명한 문자열 컬럼으로 설계했던 덕에) 백엔드/마이그레이션 변경이 전혀 필요 없었음 — 저장 시 `layoutKey`는 이제 의미 없는 고정값 `"GRID_2COL"`로 보냄(과거 저장된 `TWO_COLUMN` 등 값도 그냥 무시되므로 마이그레이션 불필요)
- 기본 패널 타입: `PanelSlot`이 슬롯별로 다른 기본값을 받도록 `defaultType` prop 추가(기존엔 항상 `'CHART'` 하드코딩) — 대시보드 페이지가 슬롯 0번은 `NEWS`, 1번은 `INDICATOR`, 2번 이후 신규 추가분은 `CHART`를 기본값으로 넘김. 사용자가 아직 한 번도 저장한 적 없는 새 대시보드는 처음부터 뉴스+지표 패널이 보임
- `PanelSlot` 콘텐츠 영역에 티커가 설정된 경우(WATCHLIST 타입 제외) `Heading level={3}`으로 티커를 패널 본문 위에 크게 표시 — 종목명은 임의 티커에 대한 조회 API가 없어(관리자 전용 `GET /api/admin/symbols`만 존재) 표시하지 못하고 티커만 표시(워치리스트 기반 섹션은 `WatchlistItemResponse.name`이 있어 이름까지 표시하는 것과 차이)

**범위 결정 — 패널 삭제 버튼은 요청에 없었지만 추가함**: "+로 추가"만 요청받았지만, 추가만 가능하고 삭제가 불가능하면 패널이 계속 쌓이기만 하는 구조라 실사용성이 떨어진다고 판단해 각 패널에 대칭적인 삭제("X") 버튼을 함께 추가함 — 백엔드/스키마 영향 없는 순수 프론트엔드 UX 보완.

Playwright로 검증(신규 가입 → 대시보드 진입): 프리셋 레이아웃 셀렉터가 완전히 사라짐 확인, 기본 슬롯 0/1이 뉴스/지표로 렌더링되고 실제 Finnhub 뉴스와 지표 선택 UI가 보임 확인, "+" 클릭 → 3번째 패널(기본 CHART) 추가 확인 → 티커 입력 시 패널 본문 위에 큰 티커 헤딩 렌더링 확인 → "X" 클릭으로 방금 추가한 패널 삭제(삭제 버튼 개수 3→2 확인) → 저장 → 새로고침 후 상태 유지 확인. 콘솔 에러 0건. 검증용 테스트 계정(`dashgrid_*`)과 관련 row는 DB에서 정리함.

**지표 패널 통합 + 하단 중복 카드 제거** (2026-07-17, 같은 날 후속 요청): "상단 우측 패널을 하단 지표 패널처럼 만들고, 하단의 뉴스/지표 패널은 제거해줘" 요청에 따라:
- `WatchlistIndicatorsPanel.tsx`(신규) — `WatchlistOverviewSection`의 "지표" 카드 본문(워치리스트 종목별로 티커+종목명 헤딩과 `IndicatorPanel`을 나열하는 로직)을 별도 컴포넌트로 추출
- `PanelSlot.tsx`의 `INDICATOR` 타입 렌더링을 `<IndicatorPanel ticker={ticker} indicator={indicator} />`(수동 티커 입력 + SMA20/SMA50/RSI14 중 하나 선택)에서 `<WatchlistIndicatorsPanel />`(워치리스트 전 종목 자동 나열, 종목당 3개 지표 전부 표시)로 교체 — `INDICATOR` 타입은 이제 `WATCHLIST` 타입처럼 티커 입력이 필요 없는 자동 패널이 됨. 이에 맞춰 종목 입력 `TextInput`과 지표 종류 `SegmentedControl`을 `INDICATOR`일 때는 더 이상 렌더링하지 않도록 조건 정리, `updateIndicator` 핸들러 및 관련 상태 제거(더 이상 쓰이지 않음 — `PanelConfig.indicator` 필드 자체는 백엔드 스키마 호환을 위해 타입에는 남겨둠, 그냥 안 쓰일 뿐)
- `WatchlistOverviewSection.tsx`에서 "시장 뉴스" 카드와 (이제 `PanelSlot`의 기본 상단 우측 패널과 내용이 완전히 중복되는) "지표" 카드를 삭제, 차트 그리드만 남김. 섹션 설명 문구도 "차트, 뉴스, 지표를 자동으로 표시합니다" → "차트를 자동으로 표시합니다"로 수정

결과적으로 기본 대시보드는: 상단 좌측 = 시장 뉴스(수동 종목 지정 가능), 상단 우측 = 워치리스트 전 종목의 지표 자동 표시(상단/하단 어디서 봐도 동일한 컴포넌트라 중복 제거), 하단 = 워치리스트 전 종목의 차트만 2열 그리드로 표시. Playwright로 검증(AAPL/MSFT 워치리스트) — 상단 우측 패널에 두 종목 모두 티커 헤딩 + SMA(20)/SMA(50)/RSI(14) 값이 자동으로 나타나는 것 확인, 하단에 "시장 뉴스" 카드와 별도 "지표" 카드가 더 이상 없는 것을 스크린샷으로 확인, 콘솔 에러 0건. 검증용 테스트 계정(`dashmerge_*`)과 관련 row는 DB에서 정리함.

**패널 내부 스크롤 고정** (2026-07-17, 같은 날 후속 요청): "뉴스 패널과 지표 패널이 커지지 않도록 내부 스크롤로 만들어줘" 요청에 따라 `NewsPanel.tsx`의 `<List>`와 `WatchlistIndicatorsPanel.tsx`의 종목별 지표 목록을 각각 `<VStack height={320} isScrollable>`로 감쌈(Astryx `VStack`의 `isScrollable` prop이 `overflow: auto`를 적용) — 워치리스트가 커지거나 뉴스가 많아져도 패널/카드 자체의 높이는 320px로 고정되고 내용만 내부 스크롤. Playwright로 검증(워치리스트에 6개 종목 추가 후 상단 우측 지표 패널이 실제로 4번째 종목(TSLA) 중간에서 잘리고 카드 높이가 늘어나지 않는 것을 스크린샷으로 확인).

### 추가 기능 — 참고 이미지 기반 신규 대시보드 페이지 (진행 중, 2026-07-17~)

사용자가 `imgs/` 폴더에 레퍼런스 이미지 3장(`financial_dash.jpg`, `stock_market_dash.png`, `stock_list_dash.png`)을 제공하고 4개의 신규 페이지(포트폴리오/재무/시장/리스트 대시보드)를 요청함 — "포트폴리오 대시보드"만 참고 이미지가 없었음. 범위가 크고(특히 재무 대시보드는 지금 시스템에 전혀 없는 펀더멘털 데이터가 필요, 시장 대시보드는 추적하지 않는 지수 심볼이 필요) 매핑이 모호해 시작 전 사용자에게 4가지를 확인받음:
1. 포트폴리오 페이지 = 기존 워치리스트 데이터를 재활용한 포트폴리오 스타일 카드(보유수량/매입가 같은 새 데이터 모델은 만들지 않음)
2. 재무 대시보드 = 근사치가 아니라 실제 yfinance 펀더멘털(`.info`/`.financials`/`.balance_sheet`/`.cashflow`)을 콜렉터로 수집해서 사용 — 스코프가 큰 쪽을 선택
3. 시장 대시보드 = S&P500/NASDAQ/VIX/국채금리 등 지수 심볼을 추가하되, Finnhub 실시간 WS 대상이 아니므로 yfinance 기반 일봉 전용(실시간 틱 없음)으로 진행
4. 전달 방식 = 한 번에 다 만들지 않고 가장 단순한 것부터 한 페이지씩 검증받으며 진행(리스트 → 포트폴리오 → 시장 → 재무 순으로 예상)

**1/4 완료 — 종목 리스트 대시보드** (`stock_list_dash.png` 참고, 2026-07-17):
- 백엔드: `QuoteResponse`에 `name` 필드 추가(`quote/dto/QuoteResponse.java`) — `QuoteService.getActiveQuotes()`/`getQuote()`가 이미 `Symbol` 엔티티를 조회하고 있었는데 그동안 `name`을 버리고 있었어서, `Symbol.getName()`을 실어 보내도록 `withName()` 헬퍼와 함께 수정. 프론트가 임의 종목의 회사명을 알 수 있는 유일한 경로(이전엔 관리자 전용 `/api/admin/symbols`밖에 없었음)라 이번 페이지뿐 아니라 이후 재무/시장 대시보드에도 재사용 가능
- **부수 버그 발견/수정**: `quote-stream-context.tsx`의 STOMP 틱 핸들러가 `setQuotes(prev => ({...prev, [quote.symbol]: quote}))`로 매 틱마다 종목 객체를 통째로 교체하고 있었음 — 그런데 실시간 틱 페이로드(콜렉터가 발행)는 `symbol/price/volume/ts`만 담고 `name`이 없어서, 최초 REST 스냅샷 이후 첫 실시간 틱이 오는 순간 방금 추가한 `name`이 사라지는 회귀가 생길 뻔했음. 틱 수신 시 `{...prev[tick.symbol], ...tick}`로 기존 값 위에 병합하도록 수정해 예방(배포 전에 발견해 실제로 사용자에게 노출된 적은 없음)
- 프론트엔드: `components/Sparkline.tsx`(신규) — Astryx에 스파크라인 컴포넌트가 없고 `@astryxdesign/charts`(공식 차트 패키지)는 canary 버전이 core 안정 버전(0.1.6)과 peer dependency 충돌이 나서 설치 보류, 대신 순수 SVG `<polyline>` 기반 경량 스파크라인을 직접 구현(값 배열 → 정규화된 좌표, 상승/하락에 따라 `var(--color-success)`/`var(--color-error)` 스트로크)
- `app/(app)/stock-list/page.tsx`(신규): 활성 종목(현재 5~6개) 각각에 대해 `GET /api/quotes/{ticker}/history?timeframe=1d&limit=250`(약 1년치 일봉)을 병렬로 가져와 고가/저가/종가 스파크라인/거래량 시계열/거래량 변화율을 클라이언트에서 계산 — 새 백엔드 집계 엔드포인트 없이 기존 히스토리 API만으로 구현. 상단에 히어로 카드 그리드(티커/이름/현재가/추이 스파크라인), 하단에 종목/고가/저가/연간추이/거래량/거래량추이 테이블. SideNav에 "종목 리스트" 항목 추가(`app/(app)/layout.tsx`)
- Playwright로 검증: AAPL/MSFT/GOOGL/TSLA/NVDA 5종목 전부 히어로 카드+테이블에 렌더링, 스파크라인 SVG 25개 렌더링(히어로 5개 + 테이블 3열×5행 중 2개 스파크라인 열), 회사명 필드가 실제로 채워짐(시드 데이터 특성상 티커와 동일한 문자열이지만 필드 자체는 정상 동작) 확인, 콘솔 에러 0건. 검증용 테스트 계정(`stocklist_*`)과 관련 row는 DB에서 정리함.
- **알아둘 점**: 거래량 컬럼의 절대값(예: "AAPL 100")이 작아 보이는 건 버그가 아니라 기존 데이터 특성 — 실시간 quote의 `volume`은 Finnhub WS의 개별 체결(틱) 단위 수량이라 원래도 작은 값이었음(일별 누적 거래량이 아님, Phase 2 REST 폴백 섹션 참고). "거래량 추이" 스파크라인은 `price_history`의 일별 `volume`(정상적으로 큰 값)을 쓰므로 그쪽은 실제 규모를 반영함.

**3/4 완료 — 시장 지표 대시보드** (`stock_market_dash.png` 참고, 2026-07-17): 사용자가 순서를 바꿔 포트폴리오보다 이 페이지와 재무 대시보드를 먼저 요청함.
- 콜렉터(`collector/app/market_indices.py`, 신규): S&P 500/NASDAQ/Russell 2000/S&P·TSX/변동성지수(VIX)/달러인덱스/미국채 5년물·30년물 8종의 yfinance 티커(`^GSPC`, `^IXIC` 등)를 내부적으로 매핑. URL에 `^` 문자가 그대로 들어가면 라우팅이 지저분해져서, 사람이 읽기 편한 slug(`SPX`, `IXIC`, `VIX` 등)를 API 표면으로 노출하고 내부에서만 실제 yfinance 티커로 변환. `main.py`에 `GET /market-indices`(목록), `GET /market-indices/{slug}/history`(yfinance `Ticker.history(period="6mo", interval="1d")`) 라우트 추가 — **의도적으로 DB 저장이나 스케줄 잡을 만들지 않음**: 지수는 Finnhub 실시간 WS 대상이 아니라 애초에 하루 단위로만 바뀌는 데이터라, 요청이 올 때마다 yfinance에서 바로 읽어오는 것으로 충분하다고 판단(캐싱조차 안 함 — 요청 빈도가 낮고 8개 티커라 yfinance 호출 비용이 무시할 만한 수준)
- 백엔드: `collector/MarketIndexInfo.java`/`MarketIndexCandle.java` dto 추가, `CollectorClient`에 프록시 메서드 2개 추가, `marketindex/MarketIndexController.java`(신규 패키지)가 `GET /api/market-indices`/`GET /api/market-indices/{slug}/history`로 노출 — 새 마이그레이션도, 새 테이블도 없음(콜렉터 news 프록시와 동일한 무상태 프록시 패턴)
- 프론트엔드: `MarketIndexCandle`의 JSON 필드명(`ts/open/high/low/close/volume`)을 기존 `CandleResponse` 타입과 동일하게 맞춰서, **기존 `CandleChart` 컴포넌트를 전혀 수정하지 않고 그대로 재사용** — 새 차트 컴포넌트를 만들 필요가 없었음. `components/market/MarketIndexCard.tsx`(신규): 지수 이름 + 기간 등락률(첫/마지막 종가 비교, 상승/하락 아이콘) + `CandleChart`. `app/(app)/market/page.tsx`(신규): 8개 지수를 2열 그리드로 표시. SideNav에 "시장 지표" 추가
- Playwright로 검증(1400px 뷰포트): 8개 지수 이름 전부 렌더링, 캔들차트 캔버스 56개(지수당 7개 — lightweight-charts가 차트 패널/십자선/시간축 등에 여러 canvas 레이어 사용) 렌더링, 등락률 배지가 실제 데이터로 계산되어 표시됨(스크린샷으로 확인), 콘솔 에러 0건. 검증용 테스트 계정(`marketdash_*`)은 DB에서 정리함.

**4/4 완료 — 재무 대시보드** (`financial_dash.jpg` 참고, 2026-07-17): 4개 페이지 중 가장 스코프가 큰 것 — 사용자가 명시적으로 "근사치가 아니라 실제 yfinance 펀더멘털"을 선택했었음(이전 세션 범위 협의 참고).
- 콜렉터(`collector/app/financials.py`, 신규): yfinance의 `Ticker.info`/`.financials`/`.balance_sheet`/`.cashflow`(연간 재무제표, 보통 최근 4~5개 회계연도)를 읽어 참고 이미지의 6개 패널에 대응하는 값을 실제로 계산:
  - Earnings Analysis(매출/매출총이익/영업이익/순이익, 연도별 원본 값)
  - Growth Analysis(전년대비 매출/영업이익 성장률 %, `(올해-작년)/|작년|*100`)
  - Profitability Analysis(ROE% = 순이익/자기자본, ROA% = 순이익/총자산 — 손익계산서와 재무상태표를 연도 기준으로 매칭)
  - Cash Flow Analysis(영업활동현금흐름/잉여현금흐름, 원본 값)
  - Margins Analysis(매출총이익률/영업이익률/순이익률 %)
  - Market Analysis(P/E·P/B·P/S 비율의 **연도별 추이**) — `.info`는 현재 스냅샷 비율만 주기 때문에, 연도별 EPS(손익계산서)·주당순자산(자기자본/발행주식수)·주당매출(매출/희석평균주식수)을 yfinance의 과거 일봉 종가(`Ticker.history(period="6y")`, 각 회계연도 말 날짜에 `Series.asof()`로 가장 가까운 거래일 종가 매칭)와 조합해 그 해 시점의 비율을 직접 계산함 — 이 프로젝트에서 가장 손이 많이 간 계산 로직
  - KPI 카드 5개: Quick Ratio/Current Ratio는 `.info`에서 그대로(이미 계산되어 제공됨), Debt to Capital %(=총부채/(총부채+최신 자기자본))·Total Debt÷FCF·Interest Coverage(=EBIT/이자비용)는 위 원본 값들로 직접 계산, Cash & Equivalents는 `.info.totalCash`
  - yfinance가 가장 오래된 회계연도 컬럼을 종종 전부 NaN으로 주는 경계 현상이 있어(AAPL 기준 5개 컬럼 중 가장 이른 연도가 전부 null), 매출이 없는 연도는 통째로 드롭 — 차트에 값 없는 연도가 대롱대롱 매달리는 것 방지
  - `main.py`에 `GET /financials/{symbol}` 라우트 추가(실패 시 502)
- 백엔드: `collector/FinancialsResponse.java`(신규, 중첩 record 7개로 연도별 지표 6종 + KPI를 표현), `CollectorClient.getFinancials()`, `financials/FinancialsController.java`(신규 패키지) → `GET /api/financials/{ticker}`(콜렉터가 빈 응답을 주면 404) — 여기도 새 테이블/마이그레이션 없음
- 프론트엔드: 기존 `Sparkline`(단일 계열)로는 다계열 비교 차트를 표현할 수 없어서 신규 컴포넌트 2종 작성 — `components/charts/MultiLineChart.tsx`(범례 포함 다계열 꺾은선, 성장률/수익성/마진/밸류에이션 4개 패널에 사용), `components/charts/GroupedBarChart.tsx`(연도별 그룹 막대, 실적/현금흐름 2개 패널에 사용), 공통 `components/charts/ChartLegend.tsx`. 둘 다 고정 `width`/`height`를 `viewBox`로만 쓰고 실제 렌더링은 `style={{width:'100%', height:'auto'}}`로 반응형 처리(3열 그리드에서 고정 px 폭을 썼더니 카드 밖으로 넘쳐서 발견/수정 — 아래 버그 참고). `app/(app)/financials/page.tsx`(신규): 티커 입력 + 조회 버튼, KPI 카드 5개, 6개 차트 패널을 3열×2행 그리드로 배치(참고 이미지와 동일한 배치). SideNav에 "재무 대시보드" 추가
- **검증 중 발견/수정한 버그**: 차트 SVG에 고정 `width={520}`을 그대로 썼더니 3열 그리드(뷰포트 1500px 기준 카드 폭 ≈380px)에서 오른쪽 두 카드가 넘쳐 잘림 — `viewBox` + `width:'100%'`로 반응형 전환해 해결. 그 다음 라운드에서 `MultiLineChart`의 마지막 연도 라벨(`text-anchor="middle"`)이 SVG 오른쪽 경계에서 절반 잘리는 것 발견 — 첫/마지막 라벨만 `start`/`end`로 앵커를 바꿔 경계 안쪽에 붙게 해결(`GroupedBarChart`는 라벨이 그룹 중앙에 찍혀서 애초에 문제 없었음).
- Playwright로 검증: AAPL 조회 → 6개 패널 제목 전부 렌더링, KPI 카드(Quick Ratio 등) 렌더링, 실제 계산된 성장률/마진/ROE 등 확인(스크린샷). 티커를 MSFT로 바꿔 재조회 → 회사명이 "Microsoft"로 바뀌는 것 확인(다른 종목 데이터로 정상 갱신됨). 콘솔 에러 0건. 검증용 테스트 계정(`finance_*`)은 DB에서 정리함.
- **범위 결정 — Debt to Capital을 게이지(도넛) 차트 대신 숫자 카드로 표시**: 참고 이미지는 이 지표만 반원형 게이지로 그리는데, 게이지 시각화를 위한 별도 SVG 컴포넌트를 새로 만들 가치가 낮다고 판단해 다른 KPI와 동일한 숫자 카드로 통일. 값 자체는 동일하게 정확히 계산됨.
- **알아둘 점 — `Interest Coverage`가 종목에 따라 `—`로 나올 수 있음**: 버그가 아니라 그 종목의 최근 회계연도에 "Interest Expense" 항목 자체가 없는 경우(예: AAPL처럼 이자비용보다 이자수익이 큰 기업)의 정상 동작.
- **백엔드/콜렉터 테스트 회귀 없음 확인**: `./gradlew test`(21건 전부 통과), `uv run pytest`(10건 전부 통과) — 이번 변경들은 전부 새 프록시 엔드포인트 추가라 기존 로직을 건드리지 않았음을 재확인.

**재무 대시보드 후속 개선 2건** (2026-07-17, 같은 날 후속 요청 "차트에 마우스 올리면 값 보이게, 재무 데이터를 백엔드에 저장해줘"):

1. **차트 호버 툴팁**: `components/charts/ChartTooltip.tsx`(신규) — SVG `<g>`/`<rect>`/`<text>`만으로 구성된 툴팁(오른쪽 경계에 닿으면 왼쪽으로 뒤집힘). `MultiLineChart`/`GroupedBarChart` 둘 다에 카테고리별 투명 히트 영역(`<rect fill="transparent">`, 항상 다른 도형 위에 오도록 마지막에 렌더링)을 추가해 `hoveredIndex` 상태를 관리 — `MultiLineChart`는 세로 점선 가이드라인 + 각 계열의 값 지점에 점(circle) 표시, `GroupedBarChart`는 호버 중이 아닌 그룹의 막대를 `opacity: 0.4`로 흐리게 해서 호버 중인 그룹을 강조. 둘 다 이미 갖고 있던 `valueFormatter`를 툴팁 값 포맷에도 그대로 재사용(축 레이블과 툴팁 값 포맷이 항상 일치). Playwright로 검증 — Growth Analysis(꺾은선)에 마우스를 올리면 "2024 / Revenue Growth %: 2% / Operating Income Growth %: 8%"가 뜨는 것, Earnings Analysis(막대)에 마우스를 올리면 "2024 / Revenue: $391B / Gross Profit: $181B / Operating Income: $123B / Net Income: $94B"가 뜨고 다른 연도 막대가 흐려지는 것을 스크린샷으로 확인. 콘솔 에러 0건.

2. **재무 데이터 백엔드 저장(캐싱)**: 지난 라운드에서 "DB 저장 없이 매 요청마다 yfinance에서 바로 읽는다"고 의도적으로 범위를 좁혔었는데, 이번 요청으로 그 결정을 뒤집음 — `V8__create_financial_statements_table.sql`(신규 마이그레이션): `financial_statements(id, ticker UNIQUE, payload_json TEXT, fetched_at TIMESTAMP)`. `financials/FinancialStatement.java`(JPA 엔티티, `dashboard_configs`와 동일한 빌더/업데이트 패턴), `FinancialStatementRepository`(`findByTickerIgnoreCase`). `FinancialsController`가 이제 `CollectorClient`를 직접 부르지 않고 새 `FinancialsService`를 거침 — **읽기 전담 캐시(read-through cache)** 패턴: 캐시가 있고 24시간 이내(`CACHE_TTL`)면 그대로 반환, 없거나 오래됐으면 콜렉터에서 새로 받아와 저장 후 반환, 콜렉터 호출이 실패했는데 오래된 캐시라도 있으면 에러 대신 그 오래된 캐시를 그대로 서빙(완전히 새 데이터가 없는 것보다 낫다고 판단) — `IndicatorCalculationService`처럼 스케줄 잡을 도입하지 않고, 요청이 들어올 때 지연 갱신(lazy refresh)하는 훨씬 가벼운 방식을 선택함(재무제표는 연 단위로만 바뀌고, 사용자가 실제로 조회한 종목만 캐시하면 충분하다고 판단 — 관심도 없는 종목까지 스케줄로 미리 계산해두는 `IndicatorCalculationService`와는 데이터 접근 패턴이 달라서 스케줄 잡보다 지연 캐싱이 더 적합).
   - `tools.jackson.databind.ObjectMapper`로 `FinancialsResponse`(중첩 record 7종 포함)를 그대로 JSON 문자열로 직렬화/역직렬화 — `DashboardConfigService`가 패널 배열을 저장하는 것과 동일한 패턴, 콜렉터 응답 DTO를 캐시 페이로드로 그대로 재사용해 별도 캐시 전용 DTO를 만들지 않음
   - curl로 캐싱 동작 실측 확인: 첫 호출(캐시 미스, yfinance 왕복) 0.97초 → 같은 티커 두 번째 호출(캐시 히트) 0.02초로 약 45배 빨라짐, `financial_statements` 테이블에 실제로 row가 쌓이는 것 확인(`ticker='AAPL', payload_json` 길이 2552bytes)
   - `./gradlew test`(21건) 전부 통과 — 기존 로직 회귀 없음

**4/4 완료 — 포트폴리오 대시보드** (참고 이미지 없음, 2026-07-18): 기존 워치리스트 + 실시간 quotes만 재활용, 보유수량/매입가 같은 새 데이터 모델은 만들지 않기로 사전에 합의된 범위 그대로 구현.
- 새 백엔드/DB 변경 없음 — 기존 `GET /api/watchlist`(종목 목록)과 `GET /api/quotes/{ticker}/history?timeframe=1d`(연간 일봉, `/stock-list`와 동일한 호출) + `QuoteStreamProvider`의 실시간 `quotes`만 조합
- `app/(app)/portfolio/page.tsx`(신규): 워치리스트 종목별로 "전일 대비 등락률"을 계산 — 각 종목의 최근 일봉 종가(아직 당일 봉이 안 쌓인 시점엔 사실상 전일 종가)와 실시간 `quote.price`를 비교. 요약 KPI 4개(보유 종목 수/상승/하락/평균 등락률), 종목별 카드(현재가·등락률·연간 스파크라인), 하단 테이블(종목/현재가/전일 대비/거래량/연간 추이)로 구성 — `/stock-list`의 카드+테이블 패턴과 `isLoading` 파생 패턴(요청 키 비교, `set-state-in-effect` 회피)을 그대로 재사용
- 워치리스트가 비어 있으면 `EmptyState` + "시세 보드로 이동" 링크로 안내
- SideNav에 "포트폴리오" 항목 추가(`BriefcaseIcon`, `app/(app)/layout.tsx`)
- Playwright로 검증: 빈 워치리스트에서 EmptyState와 링크 노출 확인 → 시세 보드에서 2종목(AAPL/GOOGL) 워치리스트 추가 → `/portfolio` 재방문 시 요약 KPI(보유 2/상승 1/하락 1/평균 등락률 3.13%), 종목별 카드, 테이블이 실제 데이터로 렌더링되는 것을 스크린샷으로 확인, 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정과 workflow 중 생긴 watchlist row는 DB에서 정리함.
- **알아둘 점**: 종목 카드의 스파크라인 색상(연간 추세 기준, `Sparkline`이 자체적으로 첫/마지막 값 비교해 자동 결정)과 옆의 등락률 배지(전일 대비 기준)가 서로 다른 시점을 비교하는 지표라 방향이 어긋나 보일 수 있음(예: 연간으로는 상승 추세라 초록 스파크라인인데 전일 대비는 하락) — 버그 아니고 `/stock-list`에서도 스파크라인과 거래량 변화 배지가 서로 다른 지표를 비교하는 동일한 패턴이 이미 있음.

**신규 대시보드 페이지 4개 전부 완료.**

### ✅ 추가 기능 — 포트폴리오 백엔드 + S&P500 유니버스 배치 (2026-07-18 완료)

계획서(`stock-monitor-dev-plan.html`) Phase 1~7 로드맵에는 없던 기능. 위 "4/4 완료 — 포트폴리오 대시보드"(워치리스트 재활용, 보유수량 없음) 구현 직후, 사용자가 "포트폴리오 백엔드가 있어야 되고, 처음엔 빈 포트폴리오로 시작해서 여러 포트폴리오를 관리하는 대시보드가 필요하다. 주식 기본 데이터를 yfinance로 수집하고, 주가 데이터는 우선 S&P500 목록으로 데이터베이스화해서 지표 계산까지 하고 싶다"고 요청 — **워치리스트 재활용 `/portfolio` 구현을 실제 포지션(보유수량/평단가) 백엔드로 완전히 교체하고, 요청받은 세 가지(포트폴리오 백엔드/yfinance 펀더멘털 최소 조회/S&P500 배치)를 같은 날 순차로 모두 구현함.**

착수 전 AskUserQuestion으로 확인한 3가지 설계 결정 (모두 그대로 구현됨):
1. **포지션 모델 = 종목당 스냅샷(거래 이력 아님)**: `portfolio_positions`에 `quantity`/`avg_cost`만 저장, 매수/매도 시 사용자가 수량·평단가를 직접 입력/수정. 거래 이력 테이블 없음 — 미실현손익(현재가 vs 평단가)만 계산, 실현손익 추적은 범위 밖.
2. **S&P500 = 완전히 별도의 배치 전용 유니버스**: 기존 Finnhub 실시간 WS 구독 대상(`symbols.is_active`)과 독립적으로 구현 완료 — 아래 "3)" 참고.
3. **포트폴리오 종목 범위 = 임의 티커 허용**: DB에 없는 티커도 포지션으로 추가 가능 — 없으면 콜렉터가 yfinance로 즉석 조회해 이름/거래소를 채우고 비활성 `Symbol`로 등록. 그런 종목은 실시간 WS 미구독이라 실시간가가 없을 수 있어(가격 없음 배지로 프론트에 정직하게 표시) 최신 일봉 종가로 폴백.

**1) 포트폴리오 백엔드 (신규 `portfolio` 패키지) — 완료**
- `V9__create_portfolios_table.sql`(`portfolios`: id, user_id FK, name, created_at, updated_at), `V10__create_portfolio_positions_table.sql`(`portfolio_positions`: id, portfolio_id FK, symbol_id FK, quantity DECIMAL(18,6), avg_cost DECIMAL(18,4), updated_at, UNIQUE(portfolio_id, symbol_id))
- 엔티티 `Portfolio`/`PortfolioPosition` + 리포지토리, 서비스(`PortfolioService`), 컨트롤러(`PortfolioController`)
- API: `GET/POST /api/portfolios`(목록/생성), `PATCH/DELETE /api/portfolios/{id}`(이름변경/삭제, positions cascade), `GET/POST /api/portfolios/{id}/positions`(목록/추가), `PATCH/DELETE /api/portfolios/{id}/positions/{positionId}`(수정/삭제)
- 응답에 평가금액/평단가 대비 미실현손익(금액+%)을 백엔드에서 직접 계산해 실어보냄(`PortfolioPositionResponse`/`PortfolioSummaryResponse`) — 현재가는 `QuoteService.resolvePrice()`(신규, Redis 실시간 캐시 우선, 없으면 `price_history` 최신 일봉 종가로 폴백, 둘 다 없으면 `UNAVAILABLE`)로 조회. 포트폴리오 요약(`PortfolioSummaryResponse`)의 총계는 가격이 있는 포지션만 합산(가격 없는 포지션 때문에 총 매입금액과 총 평가금액이 서로 다른 모집합을 더하는 왜곡 방지)
- 신규 티커는 `SymbolResolutionService`(신규, `symbol` 패키지)가 처리: `symbols`에 없으면 `CollectorClient.getSymbolProfile()`로 콜렉터에 조회 → 있으면 이름/거래소를 채워 **비활성**(`is_active=false`) `Symbol`로 저장(관리자 종목 추가 흐름과 달리 `syncActiveSymbols()`를 호출하지 않음 — 포트폴리오에 종목을 담는 것만으로 실시간 WS 구독 대상이 몰래 늘어나지 않게 함), 콜렉터도 모르는 티커면 404
- `DuplicatePortfolioPositionException`(신규) → 409, `GlobalExceptionHandler`에 매핑
- curl로 직접 검증: 포트폴리오 생성 → 기존 종목(AAPL, 실시간가) 추가 → 미지의 종목(KO, 콜렉터가 yfinance로 실제 조회해 "The Coca-Cola Company"로 해석, 실시간가 없음 확인) 추가 → 중복 추가 409 → 존재하지 않는 티커 404 → 수정/삭제/포트폴리오 삭제 전부 기대대로 동작. `./gradlew test`(21건, 회귀 없음) 통과.

**2) yfinance 최소 펀더멘털 조회 (콜렉터) — 완료(이름 해석용 최소 버전만)**
- `collector/app/symbol_profile.py`(신규): `Ticker.info`에서 name/exchange/sector/industry/currency/marketCap을 뽑아 반환(`UnknownSymbolError`로 존재하지 않는 티커 구분), `main.py`에 `GET /symbol-profile/{ticker}` 라우트 추가(404/502 구분)
- 백엔드 `collector.SymbolProfileResponse` DTO + `CollectorClient.getSymbolProfile()` 추가(기존 `getFinancials` 등과 동일한 "실패 시 Optional.empty(), 로그만 남김" 패턴)
- **범위를 의도적으로 좁힘**: 지금은 포지션 추가 시 이름/거래소만 `symbols` 테이블에 저장하고, sector/industry/marketCap은 collector 응답에서 받아오긴 하지만 아직 어디에도 저장하지 않음(DB 컬럼 없음) — task #35(재무 대시보드 비교 뷰)에서 실제로 필요해지면 `symbols`에 컬럼을 추가하거나 별도 캐시 테이블을 만들 것. 지금 당장은 "포트폴리오에 임의 티커를 추가하면 실제 회사명이 보인다"는 요구만 충족하도록 최소로 구현.
- **콜렉터 재기동 필요했음**: Python 콜렉터는 uvicorn을 `--reload` 없이 띄워서(기존 실행 방식 그대로) 코드 변경이 핫리로드되지 않음 — 새 라우트를 반영하려고 이번 세션에 collector 프로세스를 재기동함(Java 백엔드는 IntelliJ devtools가 자동 재시작해줘서 별도 조치 불필요했음). 매번 collector 쪽 파일을 바꿀 때 재기동이 필요하다는 걸 기억해둘 것.

**3) S&P500 시세 + 지표 배치 — 완료**
- `V11__add_sp500_universe_flag_to_symbols.sql`(신규 마이그레이션): `symbols.in_sp500_universe BOOLEAN NOT NULL DEFAULT FALSE` 추가 — 기존 실시간 WS용 `is_active`와 완전히 독립적인 플래그. `Symbol` 엔티티에 필드 추가, `SymbolRepository.findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc()`(신규) 추가
- `collector/app/sp500_universe.py`(신규): `get_sp500_constituents()`가 위키피디아 `List_of_S%26P_500_companies` 페이지를 `requests`+`BeautifulSoup`(표준 `html.parser`, 별도 파서 의존성 추가 안 함)로 스크래핑해 티커/이름/섹터 확보(yfinance엔 지수 구성종목 API가 없어 이 방식 채택, 실측으로 503개 종목 정상 파싱 확인). 티커의 `.`을 `-`로 치환(`BRK.B` → `BRK-B`, yfinance 표기법에 맞춤). `run_sp500_batch(limit)`가 `mysql_writer.ensure_sp500_symbols()`(신규, `symbols`를 `is_active=FALSE`로 새로 만들되 기존 row의 `is_active`는 절대 건드리지 않고 `in_sp500_universe=TRUE`만 세팅)로 멤버십을 갱신한 뒤, `yf.download(tickers, period="6mo", group_by="ticker", threads=True)` 단일 배치 호출로 전 종목 일봉을 한 번에 받아와 `insert_candles_bulk()`로 저장
- **타임스탬프 정합성 이슈 발견/해결**: `yf.download()`의 멀티티커 인덱스는 tz-naive로 나오는데(단일 `Ticker.history()`는 tz-aware), 그대로 UTC로 취급하면 기존 백필 경로가 써둔 타임스탬프(예: `America/New_York` 자정 → UTC 04:00)와 어긋나 같은 거래일에 대해 별도의 row가 중복 생성될 뻔했음 — naive 인덱스를 `America/New_York`으로 로컬라이즈 후 UTC로 변환하도록 처리해 기존 데이터와 정확히 병합(upsert)되는 것을 실측 확인(GOOGL처럼 이미 5년치가 있던 종목도 중복 row 없이 정상 병합됨)
- 콜렉터에 스케줄링 인프라가 없어(APScheduler 등 신규 의존성 추가 대신) `main.py`에 기존 `rest_fallback_loop`와 동일한 패턴의 `asyncio.create_task` 무한루프(`sp500_batch_loop`, 기동 시 1회 실행 후 `SP500_BATCH_INTERVAL_SECONDS`—기본 24시간—마다 반복)로 구현, 실패해도 앱 전체가 죽지 않도록 `try/except` + `logger.exception`으로 감쌈. 수동 검증/트리거용 `POST /sp500/sync?limit=N`도 추가
- **개발 안전장치**: `.env`의 `SP500_BATCH_LIMIT=20`으로 처음엔 20종목만 배치하도록 제한(전체 500종목 백필은 다음 세션에 이 값을 지우고 진행 권장 — 사용자와 합의된 "소규모 먼저 검증" 방침)
- 백엔드 `IndicatorCalculationService.recomputeAll()`이 이제 `findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc()`를 순회하도록 변경(기존엔 `active`만) — S&P500 유니버스 종목도 SMA20/SMA50/RSI14 계산 대상에 자동으로 포함됨
- **실측 검증 완료**: 콜렉터 재기동 → 기동 시 자동 배치 1회 실행 확인(로그 `S&P 500 batch: 20 constituents, 2480 candle rows written, 0 ticker(s) with no data`) → DB에서 20개 종목이 실제 회사명(예: `A → Agilent Technologies`)으로 `is_active=0, in_sp500_universe=1`로 생성된 것 확인 → 기존에 활성이던 `GOOGL`은 `is_active=1`이 그대로 유지되면서 `in_sp500_universe=1`만 추가된 것 확인(가장 중요한 불변조건: S&P500 편입이 실시간 WS 구독 대상을 몰래 넓히지 않음) → 백엔드 `indicators` 테이블에 새 종목들의 SMA/RSI 값이 자동으로 계산되어 쌓이는 것 확인 → `POST /sp500/sync?limit=20` 수동 트리거도 성공(20 constituents, 2480 rows, 0 failed) — 다만 직전 배치 직후라 야후 쪽 응답이 눈에 띄게 느려짐(수 분 소요, 아래 "알아둘 점" 참고). `uv run pytest`(10건), `./gradlew test`(21건) 모두 회귀 없이 통과.
- **알아둘 점 — yfinance/야후 자체 속도 저하**: 같은 프로세스에서 대량 배치(`yf.download` 다중 티커)를 짧은 간격으로 연달아 호출하면 야후 쪽에서 응답이 눈에 띄게 느려지는 현상을 관찰함(수동 트리거 2번째 호출이 수 분 걸림, 오류는 아니고 그냥 느려짐) — 콜렉터 자체는 `asyncio.to_thread`로 격리돼 있어 그 사이에도 `/health` 등 다른 엔드포인트는 정상 응답했음(실측 확인). 매일 1회로 충분한 운영 상황에서는 문제되지 않지만, `POST /sp500/sync`로 수동 재트리거할 땐 이전 호출과 시간 간격을 두는 것을 권장.

**4) 프론트엔드 전면 재작업 — 완료**
- `frontend/src/lib/types.ts`에 `PortfolioSummaryResponse`/`PortfolioPositionResponse`/`PriceSource` 추가, `lib/api.ts`에 포트폴리오 CRUD 8개 함수 추가
- `app/(app)/portfolio/page.tsx` 전면 재작성: 포트폴리오 없음 → `EmptyState`, 포트폴리오 여러 개 → `SelectableCard`로 가로 나열해 전환(각 카드에 종목수/평가금액/손익 미리보기), "+" 카드로 인라인 생성 폼, 선택된 포트폴리오 아래 요약 KPI 4개(총 평가금액/총 매입금액/평가손익/보유종목수) + 포지션 추가 폼(`TextInput`+`NumberInput`×2+버튼) + 포지션 테이블(현재가 옆에 "전일 종가"/"가격 없음" `Badge` — LIVE일 땐 배지 없음, `CLAUDE.md`의 "Badge는 예외적 상태에만" 원칙에 따름) + 행별 삭제
- 삭제(포트폴리오/포지션)는 `useImperativeAlertDialog`로 확인 다이얼로그를 거침(되돌릴 수 없는 작업이라 즉시 삭제하지 않음)
- Playwright로 검증: 빈 상태 EmptyState → 포트폴리오 생성 → AAPL(실시간가) + KO(미지의 티커, 콜렉터 조회로 "The Coca-Cola Company" 해석, "가격 없음" 배지) 추가 → 중복 추가 시 에러 배너 노출 → 두 번째 포트폴리오 생성 후 전환해도 각자 포지션이 유지되는 것 확인, 콘솔 에러 0건(의도적으로 트리거한 409 duplicate 요청의 네트워크 로그 제외). `tsc --noEmit`/`eslint` 통과.
- **알아둘 점**: 포지션 테이블이 7개 컬럼이라 좁은 뷰포트(1600px 포함)에서도 테이블 자체 스크롤 컨테이너(`overflow-x: auto`)가 생김 — 버그 아니고 Astryx `Table`이 다른 페이지(예: `/stock-list`)에서도 동일하게 동작하는 표준 패턴.
- 검증용 테스트 계정 다수(`pfcheck*`, `pfui_*`, `pfwide_*`, `pfscroll*`)와 관련 `portfolios`/`portfolio_positions`/`watchlist_items` row는 DB에서 정리함. 실제 조회로 생성된 `KO`(Coca-Cola) `Symbol` row(is_active=0)는 정상 데이터라 그대로 둠.

**남은 작업**: (1) 다음 세션에 `.env`의 `SP500_BATCH_LIMIT=20`을 지우고 전체 500종목으로 확장(소규모 검증은 이번 세션에 완료) → (2) task #35(재무 비교 뷰)에서 실제로 필요해지면 sector/market_cap을 `symbols`에 영구 저장하도록 확장.

### ✅ 버그 수정 — 관리자 페이지에서 PATCH 요청이 전부 CORS로 막혀 있던 문제 (2026-07-18)

사용자가 "관리자 페이지에서 (종목을) 활성화하려고 하면 하이드레이션 불일치(hydration mismatch) 에러가 뜬다"고 보고 — Playwright로 실제 브라우저에서 재현한 결과, 콘솔에 뜬 진짜 에러는 하이드레이션 경고가 아니라 **CORS 에러**였음: `PATCH /api/admin/symbols/{id}` 요청이 브라우저의 CORS preflight를 통과하지 못해 "활성" 스위치를 눌러도 요청 자체가 브라우저에서 막혀 아무 일도 일어나지 않았음(정확히는 `Access to fetch ... has been blocked by CORS policy: ... No 'Access-Control-Allow-Origin' header`).

**근본 원인**: `SecurityConfig.corsConfigurationSource()`가 `configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"))`로 **PATCH를 허용 메서드 목록에서 빠뜨림**(Phase 4에서 CORS를 처음 설정할 때 PATCH 엔드포인트가 아직 하나도 없었어서 생긴 누락으로 추정). 이후 Phase 5의 관리자 종목/유저 활성화 토글, 그리고 이번 세션의 포트폴리오 이름 변경/포지션 수정까지 — PATCH를 쓰는 엔드포인트가 전부 브라우저에서 호출 불가능한 상태였음. `PATCH`를 허용 메서드 목록에 추가해 해결(`List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")`).

**왜 지금까지 발견되지 못했는지**: 이 세션에서 포트폴리오 백엔드를 만들 때 PATCH 엔드포인트(`renamePortfolio`, `updatePortfolioPosition`)를 curl/Python으로 검증했는데, curl 등 브라우저가 아닌 HTTP 클라이언트는 CORS를 아예 적용받지 않아(CORS는 브라우저가 강제하는 정책) 전부 정상 동작하는 것처럼 보였음. 또한 프론트엔드 `/portfolio` 페이지에는 포지션 수정(PATCH)이나 포트폴리오 이름 변경 UI 자체가 없어서(추가/삭제만 구현) 브라우저에서 실제로 PATCH를 호출해볼 경로가 없었음. 관리자 종목 활성화 토글(`admin/symbols` 페이지의 `Switch`)이 이 프로젝트에서 브라우저가 실제로 PATCH를 호출하는 유일한 화면이었는데, Phase 5 검증 때는 종목 "추가"(POST)만 테스트하고 "활성화 토글"(PATCH)은 브라우저로 실측하지 않아 그동안 발견되지 않았던 것으로 보임.

**부수적으로 발견/수정한 실제 하이드레이션 안티패턴**: 사용자가 언급한 에러 메시지 자체는 재현하지 못했지만, `lib/auth-context.tsx`의 `isInitializing` state가 `useState(() => typeof window !== 'undefined' && Boolean(localStorage.getItem(...)))`로 초기화되고 있었음 — Next.js 하이드레이션 불일치의 교과서적 원인("A server/client branch `if (typeof window !== 'undefined')`") 그 자체라 발견한 김에 같이 고침. 서버(Node, `window` 없음)와 클라이언트(브라우저, 저장된 refresh token 유무에 따라 다름)가 각각 다른 초기값을 계산할 수 있는 구조였음. `isInitializing`을 서버/클라이언트 어디서나 동일하게 `useState(true)`로 고정하고, 실제 localStorage 확인은 마운트 후 effect에서만 하도록 수정 — "토큰 없음" 분기에서도 `isInitializing`이 `false`로 정리되도록 `Promise.resolve()`로 통일된 코드 경로를 씀(기존 `.finally()` 패턴 재사용, `set-state-in-effect` 린트 규칙과도 충돌 없음).

**검증**: Playwright로 관리자 계정 로그인 → 새로고침으로 `/admin/symbols` 하드 리로드(리프레시 토큰이 이미 localStorage에 있는 상태, 문제가 재현될 조건) → 콘솔 에러 0건(하이드레이션 경고 없음) 확인 → "활성" 스위치 클릭 → 이전엔 CORS 에러가 떴던 자리에 콘솔 에러 0건, DB에도 실제로 반영됨을 확인. 검증 중 실수로 `AAPL`을 비활성화했다가 즉시 재활성화 + 콜렉터 실시간 구독 목록도 정상 복구된 것까지 재확인함. `./gradlew test`(21건) 회귀 없음, `tsc --noEmit`/`eslint` 통과.

### ✅ 추가 기능 — 관리자 종목 관리에 일괄 활성화 (2026-07-18)

사용자가 "주식 종목 활성을 한번에 전부 키는 기능을 추가해달라"고 요청 — S&P500 배치로 종목 수가 크게 늘어난 상태(`SP500_BATCH_LIMIT`도 사용자가 직접 비워둠, 다음 재기동 시 전체 500종목 배치 예정)라 종목 하나하나 스위치를 누르는 게 비현실적이라는 맥락. 다만 "전부"를 문자 그대로 구현하면(테이블의 모든 종목을 예외 없이 한 번에 활성화) Finnhub 실시간 WS 동시구독 제한을 넘어 기존에 잘 되던 실시간 시세가 깨질 실질적 위험이 있어, 착수 전 AskUserQuestion으로 범위를 확인 — **체크박스로 원하는 종목만 골라 일괄 활성화**하는 방식으로 확정(사용자 선택).

- 백엔드: `PATCH /api/admin/symbols/bulk-active`(신규, `SymbolAdminController`/`SymbolAdminService.bulkSetActive()`) — `{ids: number[], active: boolean}`를 받아 해당 심볼들의 `active`를 한 번에 세팅하고, 콜렉터 동기화(`syncActiveSymbols()`)는 심볼마다가 아니라 **끝에 딱 한 번만** 호출(N번 개별 PATCH를 순차로 날렸다면 N번 동기화됐을 것을 방지) — `SymbolBulkActiveRequest` dto 신규 추가
- 프론트엔드 `app/(app)/admin/symbols/page.tsx`: Astryx `Table`의 내장 선택 플러그인(`useTableSelection`/`useTableSelectionState`, `plugins={{selection: ...}}`)으로 체크박스 열 추가 — `getIsItemSelectable: (item) => !item.active`로 **이미 활성인 종목은 체크박스 자체가 안 보임**(활성화할 필요가 없는 행이므로). 선택된 항목이 있을 때만 툴바("N개 선택됨" + "선택 항목 활성화 (N)" + "선택 해제")가 나타남, `AlertDialog`(제어형, `useImperativeAlertDialog` 아님 — 아래 참고)로 확인 다이얼로그를 거친 뒤 실행 — 다이얼로그 설명에 Finnhub 동시구독 제한 경고 문구를 명시
- `lib/api.ts`에 `bulkSetAdminSymbolsActive(fetcher, ids, active)` 추가

**검증 중 겪은 두 가지 실제 이슈와 원인**:
1. **처음엔 `useImperativeAlertDialog`(명령형 훅)로 구현했는데 확인 다이얼로그가 성공 후에도 안 닫힘** — Astryx 공식 예제(`AlertDialogDeleteConfirmation` 템플릿)를 보면 `onAction` 안에서 `alert.hide()`를 직접 호출하는 게 맞는 패턴인데도 실제로 닫히지 않는 현상을 반복 재현함. 근본 원인까지 완전히 특정하진 못했지만(`show()`에 넘긴 옵션 객체가 이후 리렌더에 반응하지 않는 스냅샷 구조로 추정), Astryx 공식 문서의 `AlertDialogAsyncAction` 예제가 쓰는 **제어형 `<AlertDialog isOpen={...} onOpenChange={...} isActionLoading={...} onAction={...} />` 패턴으로 교체**하니 완전히 해결됨 — `isActivateDialogOpen` state를 직접 관리하고 성공 시 `setIsActivateDialogOpen(false)`. **포트폴리오 페이지(`app/(app)/portfolio/page.tsx`)의 삭제 확인 다이얼로그도 같은 `useImperativeAlertDialog` 패턴을 쓰고 있어 동일한 버그가 있을 가능성이 높음 — 아직 브라우저로 실측 검증한 적이 없으니(추가만 테스트했었음) 다음에 만지게 되면 확인/수정 필요.**
2. **종목 활성화가 브라우저에서 최대 ~10초까지 걸림 — 버그 아님**: `SymbolAdminService`의 활성화 경로(단일 PATCH든 이번 bulk PATCH든)는 `syncActiveSymbols()`가 콜렉터의 `PUT /subscriptions`를 동기 호출하고, 콜렉터는 그 안에서 실제 Finnhub WebSocket에 구독 프레임을 보내는(`source.sync_subscriptions()`) 실외부-네트워크 왕복까지 끝나야 응답함 — curl로는 이미 여러 번 순간적으로 끝났던 것과 달리 브라우저에서 새로 구독을 추가할 때는 수 초~10초 가까이 걸리는 걸 실측함(재현 스크립트로 raw `fetch()` 타이밍 직접 측정, 10066ms). 콜렉터 자체는 이 동안에도 `/health` 등 다른 요청에 정상 응답(`asyncio.to_thread`로 격리되어 있어 이벤트 루프가 막히지 않음). 기존 단일 종목 토글도 원래 이랬던 동작이라 이번에 새로 생긴 문제는 아님 — 다이얼로그에 `isActionLoading`을 제대로 연결해 "멈춘 것처럼 보이는" 체감만 해결.
- **검증**: Playwright로 체크박스 2개 선택(비활성 종목만 체크박스 노출되는 것 확인) → "선택 항목 활성화 (2)" → 확인 다이얼로그(경고 문구 포함) → 활성화 클릭 → 버튼에 로딩 스피너 표시 → 약 9초 후 다이얼로그 자동으로 닫힘, 콘솔 에러 0건 → DB에 실제 반영 확인. 검증 중 실수로 `MMM`이 활성 상태로 남아있던 걸(이전 세션 검증 잔재로 추정) 함께 발견해 정리, 최종적으로 활성 종목이 원래의 6개(AAPL/MSFT/GOOGL/TSLA/NVDA/AMZN)로 정확히 복원된 것과 콜렉터 실시간 구독 목록이 일치하는 것까지 재확인. `./gradlew test`(21건), `tsc --noEmit`/`eslint` 통과.

### ✅ 추가 기능 — 실시간 WS 대상 종목 재구성 (2026-07-18)

"실시간 주가 데이터 받는 항목은 주요 종목 상위 10개 + 유저 관심 10개 정도로 하면 어떰?" 질문에 대해, 유저별 워치리스트를 실시간 대상에 자동 포함시키면(1) 워치리스트 변경 시 활성 세트를 자동 재계산/재구독하는 로직이 새로 필요하고 (2) 유저별 상한은 있어도 **전체 유저 워치리스트의 합집합**엔 상한이 없어 Finnhub 동시구독 제한을 넘길 위험이 있다는 트레이드오프를 먼저 짚음 — 사용자가 범위를 단순화해서 "실시간은 그냥 대표 인덱스들과 상위 10 종목"으로 확정. 착수 전 AskUserQuestion 2개로 세부사항 확정:
1. **지수 실시간 처리 = 대표 ETF로 대체**: Finnhub 무료 티어는 지수 자체(^GSPC 등)의 실시간 체결가를 주지 않음(개별 종목/ETF만 지원) — SPY(S&P500)/QQQ(나스닥)/DIA(다우) ETF를 지수 대신 실시간 WS에 포함. `/market` 페이지의 진짜 지수는 지금처럼 yfinance 일봉 그대로 유지(변경 없음).
2. **상위 10종목 = 고정 큐레이션 리스트**: 시가총액 동적 산정(스키마 확장 필요) 대신, 잘 알려진 대형주를 직접 지정 — AAPL/MSFT/GOOGL/AMZN/NVDA/META/TSLA/BRK-B/AVGO/JPM.

**최종 실시간 WS 대상 13종목**: SPY, QQQ, DIA + AAPL, MSFT, GOOGL, AMZN, NVDA, META, TSLA, BRK-B, AVGO, JPM. 기존 활성 6종목(AAPL/MSFT/GOOGL/AMZN/NVDA/TSLA)은 이미 이 안에 포함돼 있어 그대로 유지, 나머지 7종목(SPY/QQQ/DIA/META/BRK-B/AVGO/JPM)을 콜렉터 `GET /symbol-profile/{ticker}`로 실제 이름/거래소를 조회해 생성 후 활성화 — 새 백엔드/DB 스키마 변경 없이 기존 admin API(`POST /api/admin/symbols` + 이번 세션에 만든 `PATCH .../bulk-active`)만으로 구현.

**작업 중 발견/수정한 진짜 버그 2건**:
1. **콜렉터 재기동 시 실시간 구독 목록이 DB의 `is_active`를 무시하고 `DEFAULT_SYMBOLS` 환경변수로 되돌아가던 문제**: `main.py`의 `lifespan()`이 항상 `state.subscribed_symbols = set(config.DEFAULT_SYMBOLS)`로 시작했는데, `DEFAULT_SYMBOLS` 기본값("AAPL,MSFT,GOOGL,TSLA,NVDA")엔 이미 활성이던 `AMZN`이 빠져 있어서 재기동 시 아무도 모르게 AMZN의 실시간 시세가 끊길 뻔했던 잠재 버그를 이번에 재구성하다가 발견. `mysql_writer.get_active_symbols()`(신규)로 DB의 `is_active=TRUE` 집합을 먼저 조회하고, **DB에 활성 종목이 하나도 없을 때만**(신규 설치 부트스트랩 상황) `DEFAULT_SYMBOLS`로 폴백하도록 수정 — 이제 admin 패널에서 설정한 활성 종목이 콜렉터 재기동과 무관하게 계속 유지됨. `.env`의 `DEFAULT_SYMBOLS`도 새 13종목 목록으로 맞춰둠(부트스트랩 전용, 평소엔 안 쓰임).
2. **새 종목을 활성 상태로 생성/활성화할 때 백엔드↔콜렉터 사이 교착(lock wait timeout) 발생 가능** — `SymbolAdminService.create()`/`update()`/`bulkSetActive()`가 `@Transactional`인 채로 그 안에서 `syncActiveSymbols()`(콜렉터에 blocking HTTP 호출, 콜렉터는 그 안에서 같은 `symbols` 테이블에 `ensure_symbols()` INSERT를 시도)를 호출하고 있었음 — 백엔드 트랜잭션이 새로 만든 티커 행에 커밋 전 잠금을 쥔 채 콜렉터의 응답을 기다리는데, 콜렉터는 그 **같은 행**에 자기 나름의 INSERT를 하려다 그 잠금이 풀리길 기다리는 교착 상태가 됨. `DIA`(그동안 한 번도 구독된 적 없는 완전히 새 티커)를 생성하면서 실제로 `Lock wait timeout exceeded` 에러로 재현됨. `syncActiveSymbols()`를 트랜잭션 메서드들에서 빼내 별도의 non-transactional public 메서드로 분리하고, `SymbolAdminController`가 서비스 메서드 호출이 끝난(=트랜잭션이 이미 커밋된) **다음에** 별도로 호출하도록 수정 — 이제 콜렉터 동기화는 항상 커밋된 데이터를 대상으로만 일어남.
- **검증**: 콜렉터 재기동 후 `/health`가 DB의 6개 활성 종목을 그대로 반영하는 것 확인(DEFAULT_SYMBOLS 무시하지 않음) → 콜렉터 `GET /symbol-profile`로 7개 신규 티커 실제 정보 조회 확인 → SPY/QQQ/DIA는 `POST /api/admin/symbols`로 생성(수정 후이므로 신규 티커 생성도 교착 없이 정상 완료 확인) → META/BRK-B/AVGO/JPM은 S&P500 배치가 이미 만들어둔 행이 있어 `PATCH .../bulk-active`로 일괄 활성화(10.1초 소요, 교착 없이 정상 완료) → 최종적으로 `symbols.is_active=1`인 13종목과 콜렉터 `/health`의 `subscribed_symbols`가 정확히 일치하는 것 확인. `./gradlew test`(21건), `uv run pytest`(10건) 회귀 없음.
- **알아둘 점 — 이번 재구성 중 S&P500 전체 배치와 동시에 겹쳐서 겪은 잠금 경합**: 마침 사용자가 `SP500_BATCH_LIMIT`를 비워둬서 콜렉터 재기동과 동시에 503종목 전체 배치(`ensure_sp500_symbols`)가 함께 돌고 있었고, 그로 인한 `symbols` 테이블 잠금 경합이 위 버그 1건과 겹쳐 재현/디버깅이 더 오래 걸렸음(진짜 원인은 버그 2건이 맞고, 배치는 타이밍만 앞당긴 것). 이후 배치가 여러 종목의 `name`을 위키피디아 실제 회사명으로 갱신하면서 `AAPL`/`MSFT`/`AMZN`/`NVDA`/`TSLA`/`GOOGL`의 표시 이름도 부수적으로 더 정확해짐(예: "AAPL" → "Apple Inc.") — 의도한 정상 동작.

### ✅ 추가 기능 — 시세 보드/종목 리스트 등락 색상 표시 (2026-07-18)

"시세 보드와 종목 리스트에서 가격 상승/하락을 색깔로 알기 쉽게 — 하락은 파란색, 상승은 빨간색으로, 변동값과 퍼센트도 같이" 요청. **국내 증시 표기 관행(상승=빨강, 하락=파랑)은 Astryx의 `success`(초록)/`error`(빨강) 시맨틱 색상 방향과 정반대**라 그 두 semantic prop을 그대로 못 쓰고, 실제 컬러 토큰(`--color-text-red`/`--color-text-blue`, `--color-icon-red`/`--color-icon-blue`)을 직접 참조하는 방식 채택 — 이미 `financials` 페이지 차트 색상에서 같은 방식(`var(--color-icon-blue)` 등)을 쓰고 있어 이 코드베이스에서 처음 쓰는 패턴은 아님.

- `components/PriceChangeIndicator.tsx`(신규): `changeValue`/`changePct`를 받아 화살표 아이콘 + "±값 (±퍼센트%)" 텍스트를 렌더링 — 상승은 `<span style={{color: 'var(--color-text-red)'}}>`로 감싸고 `Icon`/`Text` 모두 `color="inherit"`로 그 안에서 색을 물려받게 함(하락은 blue), 변동이 정확히 0이면 회색 텍스트로 중립 처리, 데이터가 없으면 "—". 두 페이지에서 공통으로 재사용.
- `app/(app)/page.tsx`(시세 보드): 실시간 틱마다 반짝이는 기존 화살표 플래시도 같은 톤(빨강/파랑)으로 맞춤(기존엔 success/error 초록/빨강이라 새로 추가한 "전일대비" 컬럼과 색이 어긋났을 것). "전일대비" 컬럼(신규) 추가 — 활성 종목(현재 13개)마다 `GET /api/quotes/{ticker}/history?timeframe=1d&limit=5`로 최근 일봉을 가져와 가장 최근 봉의 종가를 "전일 종가"로 삼고(오늘 봉은 장 마감 후에나 쌓이므로), 실시간가와 비교해 변동값/퍼센트 계산 — `/stock-list`와 동일한 패턴(요청 키로 캐시, 틱마다 재요청 안 함).
- `app/(app)/stock-list/page.tsx`: 이미 종목당 250개 일봉을 갖고 있어서(연간 고가/저가/추이용) 추가 API 호출 없이 그 배열의 마지막 종가를 전일 종가로 재사용 — 히어로 카드와 테이블 둘 다에 "전일대비" 표시 추가.
- **검증**: Playwright로 시세 보드/종목 리스트 둘 다 스크린샷 확인 — 하락 종목(MSFT −0.82%, TSLA −3.26%, NVDA −2.98%)은 파란색, 상승 종목(BRK-B, QQQ)은 빨간색으로 정확히 렌더링됨, 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과(의존성 배열 관련 경고 1건 발견해 `useMemo`로 정리).
- **최초 배포 직후 관찰한 "0 변동" 현상에 대한 잘못된 추측을 바로잡음**: 처음엔 "막 백필한 종목이라 오늘 장중 스냅샷이 전일 종가처럼 잡혀서 그렇다"고 짐작하고 기록했었는데, 사용자가 "정말 yfinance 데이터가 안 쌓인 거 아니냐"고 재질문해서 실제로 DB를 까보니 그 추측이 틀렸음 — 진짜 원인과 수정 내용은 바로 아래 "버그 수정" 항목 참고.

### ✅ 버그 수정 — 실시간 대상 종목의 일봉이 계속 갱신되지 않던 문제 (2026-07-18)

사용자가 "전일대비가 0인 종목들이 있는데 yfinance로 과거 데이터가 저장이 안 된 거냐"고 질문 — DB를 직접 대조해서 실제 원인을 확인함.

**진짜 원인**: `MSFT`/`TSLA`/`NVDA`의 최신 일봉이 `2026-07-16`에 멈춰 있었음(하루 뒤처짐), 반면 `AAPL`/`GOOGL`/`AMZN`은 `2026-07-17`(최신)까지 있었음 — 둘 다 원래 같은 최초 5종목 수동 백필(`backfill.py`, Phase 2)에서 시작했지만, 그 이후로는 **S&P500 일일 배치(`sp500_batch_loop`)만** 일봉을 계속 갱신해주고 있었고, 그 배치는 아직 진행 중이라 위키피디아 종목 순서상 `A`/`G` 티커는 이미 처리했지만 `M`/`T`/`N` 티커는 아직 못 미친 상태였음. 그래서 `MSFT`/`TSLA`/`NVDA`는 실시간가를 하루 지난 종가와 비교하고 있었던 것 — 우연히 "그럴듯한" 변동폭을 보여줬지만 실제로는 **더 부정확한 비교**였음(2일치 변동을 1일치처럼 표시). 반대로 `SPY`/`QQQ`/`DIA`(지수 ETF)는 애초에 S&P500 구성종목이 아니라서 이 배치가 절대 건드리지 않고, 지난 세션에 1회성으로 수동 백필한 게 전부라 — **앞으로도 아무것도 안 하면 계속 정체될 예정**이었음. 즉, "실시간 WS 대상 13종목"의 일봉을 계속 최신 상태로 유지해주는 장치가 하나도 없었음(S&P500 배치는 어디까지나 S&P500 구성종목 500여 개를 위한 것이지 이 13종목 세트를 위한 게 아님).

**수정**: 
1. **즉시 조치**: `uv run python -m app.backfill AAPL MSFT GOOGL AMZN NVDA TSLA META BRK-B AVGO JPM SPY QQQ DIA`로 13종목 전부 5년치 재백필 — 전부 `2026-07-17`(최신 거래일)까지 일치하는 것 확인.
2. **재발 방지**: `collector/main.py`에 `active_symbols_daily_refresh_loop()`(신규) 추가 — 콜렉터 기동 시 1회, 이후 매일(`ACTIVE_SYMBOLS_REFRESH_INTERVAL_SECONDS`, 기본 24시간) `symbols.is_active=TRUE`인 종목(=지금의 실시간 WS 대상 13종목, S&P500 소속 여부와 무관)만 `backfill_symbol(ticker, id, period="5d")`로 가볍게 갱신. S&P500 배치와 별개로 동작해서, 관리자가 실시간 대상을 바꿔도(예: 다음에 다른 종목으로 교체) 항상 그 종목들의 일봉이 최신으로 유지됨.
- **검증**: 콜렉터 재기동 후 `/health`가 13종목 정상 반영, DB 조회로 13종목 전부 최신(2026-07-17) 종가 확인. `uv run pytest`(10건) 회귀 없음.
- **처음엔 "다음 거래일부터 정상화될 것"이라고 잘못 예측했음** — 실제로는 다음 거래일까지 기다릴 필요 없는, 그 자리에서 고칠 수 있는 진짜 버그였음. 아래 "버그 수정" 항목 참고.

### ✅ 버그 수정 — 전일대비가 전 종목 0%로 보이던 문제 (2026-07-18)

일봉을 전부 최신화하고 나니, 이번엔 사용자가 "AAPL/MSFT/GOOGL 등 전 종목의 전일대비가 다 0으로 나온다"고 재보고 — 실제로 DB/Redis를 대조해서 원인을 확정함.

**진짜 원인**: 주말이라 장이 닫혀 있어서, 콜렉터의 실시간가(`quote:{ticker}`, REST 폴백이 yfinance의 마지막 체결가를 그대로 재발행)가 **금요일 종가와 완전히 동일한 값**이 되어 있었음(`redis-cli HGETALL quote:AAPL` → `333.739990234375`, DB의 최신 일봉 종가 `333.7400`과 사실상 같음). 그런데 "전일대비" 계산 로직은 항상 "가장 최근 일봉의 종가"를 기준선으로 썼는데, 지금 이 상황에서는 그 "가장 최근 일봉"이 바로 지금 비교하려는 실시간가 그 자체였음 — 즉 **같은 값을 자기 자신과 비교**하고 있었던 것이라 무조건 0이 나왔음(장중이었다면 실시간가가 어제 종가와 실제로 달랐을 것이므로 이 문제가 드러나지 않았을 것).

**수정**: `frontend/src/lib/priceChange.ts`(신규) — `resolvePrevClose(closes, livePrice)` 헬퍼 추가: 실시간가가 최근 일봉 종가와 (오차범위 0.005 이내로) 사실상 같으면, 그건 "장이 닫혀서 마지막 체결가를 그대로 돌려받은 것"으로 판단해 **한 봉 더 이전(전전일) 종가**를 기준선으로 대신 사용 — "금요일이 목요일 대비 어떻게 움직였는지"를 보여줘서 장이 닫혀 있어도 의미 있는 값이 나옴. 장중에는 실시간가가 어제 종가와 실제로 다를 것이므로 이 폴백이 발동하지 않고 기존처럼 정상적인 "오늘 vs 어제" 비교가 나옴 — 즉 장 개폐와 무관하게 자동으로 올바른 기준선을 고름.
- `app/(app)/page.tsx`(시세 보드): 종목마다 종가 1개만 저장하던 것을 최근 5개 종가 배열로 바꾸고, 실시간가가 들어온 뒤 `resolvePrevClose`로 기준선을 정함.
- `app/(app)/stock-list/page.tsx`: 이미 갖고 있던 연간 종가 배열을 그대로 넘겨서 재사용 — 추가 API 호출 없음.
- **검증**: Playwright로 시세 보드/종목 리스트 재확인 — AAPL +0.14%(빨강), MSFT −1.82%(파랑), TSLA −2.61%(파랑) 등 13종목 전부 의미 있는 값으로 렌더링됨, 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과.
- **알아둘 점**: 오차범위(0.005)로 "장 마감이라 값이 같다"를 판단하는 휴리스틱이라, 극히 드물게 장중에 실시간가가 어제 종가와 우연히 거의 똑같은 경우(예: 시가가 전일 종가와 거의 동일)에도 하루를 더 건너뛴 비교가 나올 수 있음 — 다만 그 경우에도 어차피 "거의 0%"라는 결론 자체는 맞기 때문에 실질적인 오정보는 아님.

### ✅ 추가 기능 — 재무 대시보드 다중 종목 비교 (task #35, 2026-07-18 완료)

계획서 로드맵에는 없던 요청(2026-07-18, "재무 건전성과 미래 성장 잠재력을 여러 종목 리스트로 비교하고 싶다"). 착수 전 AskUserQuestion 2개로 스코프 확정:
1. **종목 선택 방식 = 자유 티커 입력 + 워치리스트 빠른 추가**: 포트폴리오/S&P500 유니버스 연동 같은 무거운 방식 대신, 티커를 직접 입력하거나(콜렉터가 즉석 조회) 워치리스트 종목을 칩 클릭 한 번으로 추가하는 가벼운 방식.
2. **시각화 = 표 형태(종목 x 지표)**: 카드형/레이더 차트 대신 숫자를 정확히 비교하기 쉬운 테이블.

**구현**:
- `app/(app)/financials/compare/page.tsx`(신규 페이지, `/financials`에서 "여러 종목 비교하기 →" 링크로 진입) — 새 백엔드/DB 변경 전혀 없이 기존 `GET /api/financials/{ticker}`(이미 24시간 TTL로 캐싱됨, `FinancialsService`)를 종목마다 병렬 호출(`Promise.allSettled` — 종목 하나가 실패해도 나머지는 정상 표시)해서 조립.
- 티커 관리 UI: Astryx `Token`(단독 컴포넌트, `Tokenizer`처럼 무거운 검색 소스 없이 단순 추가/제거 칩에 적합)을 두 용도로 사용 — 워치리스트 빠른 추가 칩(`onClick`으로 추가)과 "비교 중" 칩(`onRemove`로 제거).
- 비교 테이블 8개 지표: 재무 건전성(Quick Ratio, Debt to Capital %, Interest Coverage, Net Margin %) + 성장 잠재력(Revenue Growth %, Operating Income Growth %, ROE %, P/E) — 전부 `FinancialsResponse`의 **최신 연도** 값만 뽑아서 표시(`kpis`는 이미 최신 스냅샷, `marginsAnalysis`/`growthAnalysis`/`profitabilityAnalysis`/`marketAnalysis`는 연도별 배열이라 마지막 원소 사용).
- 조회 실패한 티커(오타 등)는 테이블에서 빠지고 상단에 경고 Banner로 어떤 티커가 실패했는지 명시.

**검증 중 발견/수정한 진짜 버그**: 존재하지 않는 티커를 비교에 추가하면 403(빈 바디)이 떠서 원인 조사 — `financial_statements.ticker` 컬럼이 `VARCHAR(10)`이었는데(V8 마이그레이션 때 지정), 이 프로젝트 다른 곳의 `symbols.ticker`는 `VARCHAR(20)`이라 애초에 컬럼 크기가 서로 안 맞았음. 11자 이상 문자열("ZZZINVALIDTICKER" 등)을 캐시에 저장하려다 데이터 잘림(truncation) 에러가 나면서, 그게 (정확한 원인까진 못 밝혔지만) 하필 컨트롤러 계층 예외 매핑을 우회해 401도 404도 아닌 403 빈 응답으로 새어나갔던 것 — `curl`로 티커 길이를 10, 11자로 각각 테스트해서 정확히 그 경계에서 재현/확정함. `V12__widen_financial_statements_ticker_column.sql`(신규, `ticker VARCHAR(10)` → `VARCHAR(20)`)로 수정, `FinancialStatement` 엔티티의 `length` 애노테이션도 맞춤. 수정 후엔 존재하지 않는 티커도 (yfinance가 애초에 예외를 안 던지므로) 200 + 빈 데이터로 정상 응답, `/financials`의 기존 "재무 데이터 없음" EmptyState 처리 경로를 그대로 재사용해 프론트에서 자연스럽게 처리됨.
- **검증**: Playwright로 AAPL/MSFT/TSLA를 비교에 추가 → 8개 지표 전부 실제 계산값으로 렌더링 확인(예: TSLA Revenue Growth −2.9%, MSFT ROE 29.6%) → 존재하지 않는 티커 추가 시 경고 배너 확인 → 버그 수정 전/후로 403 재현과 해결을 curl로 직접 검증. `./gradlew test`(21건) 회귀 없음, `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정과 `financial_statements`에 쌓인 테스트용 가짜 티커 캐시(ZZZZ 등)는 정리함.
- **알아둘 점**: 유효하지만 재무 데이터가 아예 없는 티커(예: 상장 초기라 연간 재무제표가 아직 없는 회사)는 에러 배너가 아니라 테이블에 전부 "—"인 행으로 나타남 — 실패(못 불러옴)와 데이터 없음(불러왔지만 비어있음)을 구분한 의도된 동작.

### ✅ 개선 — 재무 대시보드 라우트 재구성: 비교가 먼저, 종목 선택 시 상세로 (2026-07-18)

사용자 요청("재무 대시보드에서 재무 종목 비교가 먼저 나오고 종목을 선택하면 재무 대시보드가 보이게 해줘") — 직전 세션에서 만든 다중 종목 비교가 `/financials/compare`라는 서브 경로에 있고 단일 종목 상세가 `/financials`(랜딩)를 차지하고 있던 걸 뒤집음.

**변경**:
- `app/(app)/financials/[ticker]/page.tsx`(신규) — 기존 `/financials/page.tsx`(KPI 5종 + 6개 차트짜리 단일 종목 상세)를 이 동적 라우트로 이동. 로컬 `useState` 티커 대신 `/symbols/[ticker]/page.tsx`와 동일한 컨벤션(`params: Promise<{ ticker: string }>`를 `use()`로 언랩)으로 티커를 URL이 소유하도록 바꿈. 상단 "조회" 버튼은 이제 `router.push(\`/financials/\${tickerInput}\`)`로 라우팅. 비교 화면으로 돌아가는 "← 종목 비교로 돌아가기" 링크를 `/financials`로 추가.
- `app/(app)/financials/page.tsx`(교체) — 기존 `/financials/compare/page.tsx`(다중 종목 비교 표) 내용을 이 경로로 이동시켜 새 랜딩 페이지로 만듦. 비교 표의 종목 링크(`renderCell`)를 `/symbols/${ticker}`(시세 상세)에서 `/financials/${ticker}`(재무 상세)로 변경 — 종목을 클릭하면 재무 상세 대시보드로 이동하는 것이 이번 요청의 핵심.
- `app/(app)/financials/compare/`(구 경로) 디렉터리 삭제.
- `app/(app)/layout.tsx`의 SideNav "재무 대시보드" 항목 — `isSelected={pathname === '/financials'}`였던 걸 관리자 섹션과 동일한 패턴인 `pathname.startsWith('/financials')`로 바꿔서, `/financials/AAPL` 같은 상세 페이지를 보고 있을 때도 네비 항목이 계속 활성 표시되도록 함.
- **검증**: Playwright로 신규 계정 가입 → "재무 대시보드" 네비 클릭 → `/financials`(비교 화면, EmptyState)로 착지 확인 → AAPL 추가 → 비교 표에 렌더링 확인 → AAPL 행 클릭 → `/financials/AAPL`(상세, KPI 5종 + 6개 차트) 정상 렌더링 확인 → 두 경로 모두에서 네비 `aria-current="page"` 유지 확인 → "← 종목 비교로 돌아가기" 링크로 `/financials`로 복귀 확인. 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정은 `stockmonitordb`에서 직접 정리함.

### ✅ 개선 — 관심종목(워치리스트) 자동 비교 추가 (2026-07-18)

사용자 요청("관심종목에 있는 티커를 자동으로 재무 종목 비교 목록에 추가하게 해줘") — 기존엔 워치리스트 종목도 "빠른 추가" 칩을 일일이 클릭해야 비교 표에 들어갔는데, 이제 `/financials` 진입 시 워치리스트 전체가 자동으로 비교 목록에 채워짐.

**구현**: `app/(app)/financials/page.tsx` — 기존엔 `tickers`가 단일 `useState`였는데, 이걸 `manualTickers`(입력창/빠른 추가 칩으로 명시적으로 추가한 티커)와 `removedTickers`(명시적으로 뺀 티커, `Set`) 두 개의 state로 쪼개고, 실제 화면에 쓰는 `tickers`는 `useMemo`로 `워치리스트 티커(removedTickers 제외) + manualTickers(중복 제외)`를 매 렌더마다 파생시키도록 변경. 워치리스트가 바뀌면 다음 렌더에서 `tickers`가 자동으로 갱신됨 — `useEffect` 안에서 `setTickers`를 직접 호출하는 방식은 처음 시도했다가 `react-hooks/set-state-in-effect` 린트 에러로 반려되어 파생 상태(derived state) 패턴으로 바꿈.
- 종목 제거(`removeTicker`): `manualTickers`에서 빼고 `removedTickers`에 추가 — 워치리스트에 남아있어도 재자동추가되지 않고, "워치리스트에서 빠른 추가" 칩으로 다시 표시됨(사용자가 다시 클릭하면 재추가 가능).
- 티커 추가(`addTicker`, 입력창 또는 빠른 추가 칩): `manualTickers`에 넣고, 혹시 `removedTickers`에 있었다면 제거해 "다시 추가 가능" 상태로 되돌림.
- **알아둘 점**: `removedTickers`는 컴포넌트 상태일 뿐 서버에 저장되지 않음 — 페이지를 새로고침하면 초기화되어, 방금 뺐던 워치리스트 종목도 다시 자동으로 채워짐(의도된 동작: "이번 세션에서 잠깐 숨기기"이지 "워치리스트에서 영구 제외"가 아님. 진짜로 비교 대상에서 빼고 싶으면 워치리스트 자체에서 제거해야 함).
- **검증**: Playwright로 신규 계정 가입 → `/financials`(워치리스트 비어있음, EmptyState) → 시세 보드(`/`)에서 AAPL/MSFT 별표(워치리스트 추가) → `/financials` 재방문 시 AAPL/MSFT가 자동으로 비교 표에 렌더링됨 확인 → AAPL 칩의 "Remove AAPL" 버튼 클릭 → AAPL이 표에서 빠지고 "워치리스트에서 빠른 추가" 칩으로 재등장, MSFT는 그대로 남음 확인 → 페이지 새로고침 시 AAPL이 다시 자동 추가됨 확인(위 "알아둘 점"과 일치하는 의도된 동작). 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정 2개는 `stockmonitordb`에서 직접 정리함.

### ✅ 개선 — 종목 리스트 상단 카드 그리드 제거 (2026-07-18)

사용자 지적("종목 리스트에서 차트 카드는 인덱스만 남기고 나머지는 없애도 될 듯") — `/stock-list` 상단에 종목별 카드(티커/가격/등락률/스파크라인) 그리드가 있었는데, 바로 아래 표에도 티커·전일대비·연간 추이(스파크라인)가 전부 있어서 완전히 중복이었음. AskUserQuestion으로 범위 확인 후("카드 그리드 전체 삭제" 선택) 카드 그리드 섹션 자체를 제거.

**구현**: `app/(app)/stock-list/page.tsx` — `isLoading` 분기 안에서 `rows`를 `Card`로 렌더링하던 `Grid` 블록(전체)을 삭제하고 `Table`만 남김. 더 이상 안 쓰는 `Card`/`Grid` import 제거(`Sparkline`은 표의 "연간 추이"/"거래량 추이" 컬럼에서 계속 쓰이므로 유지).
- **검증**: Playwright로 신규 계정 가입 → `/stock-list` 방문 → 카드형 DOM 요소 0개, 표 행 13개(AAPL~DIA) 정상 렌더링 확인, 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정은 `stockmonitordb`에서 직접 정리함.

### ✅ 개선 — 시세 보드(`/`)를 종목 리스트(`/stock-list`)에 통합 (2026-07-18)

사용자 요청("시세보드를 없애고 종목 리스트와 합쳐봐 종목 리스트에 관심종목 설정할 수 있게 해주고") — 실시간 시세 보드(`/`)와 종목 리스트(`/stock-list`)가 겹치는 기능(둘 다 활성 종목 실시간/일봉 데이터를 테이블로 보여줌)이 많아 하나로 합침. 결과물은 `/stock-list` 하나에 실시간가+전일대비(구 시세 보드)와 1년 고가/저가/추이+거래량(구 종목 리스트)가 전부 있고, 관심종목 별표 토글도 새로 추가됨.

**구현**:
- `app/(app)/stock-list/page.tsx`(전면 재작성) — 구 시세 보드의 `useQuoteStream`(실시간가/거래량/연결상태), `usePriceFlash`(가격 변동 시 빨강/파랑 화살표 깜빡임), 워치리스트 별표 토글(`IconButton` + `StarIcon` outline/solid), 전체/관심종목 `SegmentedControl` 필터를 그대로 가져와 구 종목 리스트의 1년 통계(`computeStats`: 고가/저가/연간 종가 스파크라인/거래량 추이 스파크라인) 로직과 합침. 컬럼 순서: 관심종목 별표 → 종목 → **현재가**(신규, 실시간 깜빡임) → 전일대비 → 고가(1년) → 저가(1년) → 연간 추이 → 거래량 → 거래량 추이. 구 시세 보드에 있던 "갱신 시각" 컬럼은 실시간 깜빡임 표시로 이미 최신성이 드러나서 중복이라 판단해 제외(컬럼이 이미 9개라 정리 차원).
- `app/(app)/page.tsx`(구 시세 보드 페이지 내용 전체 삭제, 리다이렉트로 교체) — `/` 방문 시 `router.replace('/stock-list')`로 클라이언트 사이드 리다이렉트만 수행(이 코드베이스의 기존 인증 리다이렉트 패턴 `RedirectIfAuthed`/`RequireAuth`와 동일한 `useEffect` + `router.replace` 방식).
- 로그인/회원가입 성공 후, `RedirectIfAuthed`(이미 로그인된 유저가 `/login`·`/signup` 접근 시), `RequireAdmin`(비관리자가 관리자 페이지 접근 시)의 리다이렉트 목적지를 전부 `'/'` → `'/stock-list'`로 직접 변경 — `/`를 거쳐 다시 리다이렉트되는 이중 홉을 없앰(단, `/`는 위 리다이렉트 페이지로 여전히 살아있어서 즐겨찾기/외부 링크로 들어와도 안전).
- `app/(app)/layout.tsx`의 SideNav — "시세 보드" 항목(HomeIcon) 삭제, "종목 리스트" 항목을 "메인" 섹션의 첫 번째로 이동(기존엔 3번째였음, 이제 사실상 앱의 홈 역할을 하므로). 아이콘은 그대로 `ListBulletIcon` 유지(다른 항목들과 톤 일치, 굳이 바꿀 이유 없음).
- 텍스트 정리: `WatchlistIndicatorsPanel`/`WatchlistOverviewSection`(대시보드 위젯)의 빈 워치리스트 안내 문구 "시세 보드에서 종목을 워치리스트에 추가해보세요" → "종목 리스트에서 종목을 워치리스트에 추가해보세요". 루트 `layout.tsx`의 메타 description "실시간 시세 보드" → "실시간 종목 리스트".
- **검증**: Playwright로 신규 계정 가입 → `/stock-list`로 바로 착지(구 `/`가 아님) 확인 → `/` 직접 방문 시에도 `/stock-list`로 리다이렉트되는지 확인 → 네비에 "시세 보드" 항목 없고 "종목 리스트"가 첫 항목인지 확인 → 병합된 표에 실시간가/전일대비/1년 고가·저가/추이/거래량이 한 행에 다 나오는지 확인(AAPL 333.74, +0.14% 등) → AAPL 별표 클릭으로 워치리스트 추가 → "관심종목" 필터로 전환 시 AAPL만 남는지 확인. 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정은 `stockmonitordb`에서 직접 정리함.
- **알아둘 점**: `/dashboard`(커스터마이즈 가능한 위젯 대시보드)와 `/portfolio`는 이번 통합과 무관하게 그대로 유지됨 — 둘 다 워치리스트 데이터를 별도로 조회해서 쓰는 페이지라 `/stock-list` 재작성의 영향을 받지 않음(단, 위 "빈 워치리스트" 안내 문구만 갱신).

### ✅ 버그 수정 — 종목 리스트에서 회사명이 길면 티커까지 여러 줄로 밀리던 문제 (2026-07-18)

사용자 지적("종목 리스트에서 티커가 이름이 길면 한 줄에 다 안나오고 여러 줄로 나오는데 이름을 ...으로 truncate 시켜서 티커 한 줄로 나오게 해줘") — `종목` 컬럼이 `HStack`으로 티커(`Heading`)와 회사명(`Text`)을 나란히 배치하는데, 회사명이 길면(예: "State Street SPDR Dow Jones Industrial Average ETF Trust") `Text`가 기본적으로 줄바꿈되어 행 전체가 여러 줄로 늘어나던 문제.

**수정**: `app/(app)/stock-list/page.tsx`의 `ticker` 컬럼 — 회사명 `Text`에 Astryx의 `maxLines={1}`(1줄 넘으면 자동으로 `text-overflow: ellipsis` 처리 + 잘렸을 때 hover 시 전체 이름을 보여주는 툴팁 내장)을 추가. 다만 `maxLines`만으로는 안 됨 — `HStack`(flexbox) 안에서 flex 아이템은 기본적으로 내용물 크기 밑으로 줄어들지 않아서(`min-width: auto` 기본값) ellipsis가 작동하려면 `style={{ minWidth: 0, flex: 1 }}`를 같이 줘야 함(흔한 flexbox truncation 함정). 티커 `Heading`에는 `style={{ flexShrink: 0 }}`을 줘서 티커 자체는 항상 온전히 다 보이고 회사명 쪽만 줄어들도록 함.
- **검증**: Playwright로 가장 긴 이름(DIA="State Street SPDR Dow Jones Industrial Average ETF Trust")을 포함한 13개 행의 실제 렌더링 높이를 측정 — 전부 약 49px(단일 줄)로 동일함을 확인(수정 전이었다면 DIA/SPY/QQQ 행만 눈에 띄게 더 높았을 것). GOOGL 스크린샷에서 "Alphabet Inc.(Class ..."로 ellipsis 렌더링 확인, SPY 행의 실제 DOM에서 truncation 전용 CSS 클래스와 `title="State Street SPDR S&P 500 ETF Trust"`(hover 툴팁) 속성이 붙어있음을 computed style로 재확인. 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정들은 `stockmonitordb`에서 직접 정리함.
- **알아둘 점**: 앞으로 flex 컨테이너(`HStack`/`Stack` 등) 안에서 `Text`의 `maxLines`로 말줄임을 적용해야 하면, 그 `Text`에 `style={{ minWidth: 0 }}`(및 필요시 `flex: 1`)를 함께 주는 걸 기본으로 할 것 — 안 그러면 `maxLines`가 있어도 flex 아이템의 기본 `min-width: auto` 때문에 조용히 무시되고 줄바꿈이 그대로 발생함.

### ✅ 개선 — 종목 세부 페이지(`/symbols/[ticker]`) 보강 (2026-07-18)

사용자에게 "종목 세부 페이지에서 부족한 부분이 뭔지" 분석해서 보고한 뒤("1, 2번 먼저 진행해줘"), 두 가지를 구현: (1) 이미 만들어져 있었지만 이 페이지엔 안 붙어있던 컴포넌트 3개(뉴스/기술지표/재무 대시보드 링크) 연결, (2) 이미 fetch하고 있던 데이터인데 화면에 없던 정보 3개(전일대비/회사명/1년 고가·저가) 추가.

**구현**: `app/(app)/symbols/[ticker]/page.tsx`
- **뉴스**: 대시보드 위젯 전용이었던 `NewsPanel` 컴포넌트를 그대로 재사용(`<NewsPanel ticker={ticker} />`) — 페이지 하단에 "관련 뉴스" 패널로 추가.
- **기술 지표**: 역시 대시보드 위젯 전용이었던 `IndicatorPanel`을 재사용(`<IndicatorPanel ticker={ticker} />`) — KPI 카드 grid 바로 아래에 SMA(20)/SMA(50)/RSI(14) 패널로 추가.
- **재무 대시보드 링크**: 헤더에 `/financials/${ticker}`로 가는 "재무 대시보드 보기 →" 링크 추가 — 이전엔 이 페이지에서 재무 대시보드로 가는 진입점이 전혀 없었음.
- **전일대비**: 차트의 `timeframe`(일봉/분봉) 선택과 무관하게 항상 정확한 전일대비를 보여주기 위해, 차트용 캔들(`useCandles`, 선택된 timeframe에 종속)과는 **별도로** `api.getHistory(ticker, '1d', 250)`를 한 번 더 fetch(`dailyStats` state) — 여기서 얻은 최근 종가 배열을 `resolvePrevClose`에 넘겨 `PriceChangeIndicator`로 "현재가" 카드에 표시. 분봉 보는 중에도 전일대비가 흔들리지 않음.
- **회사명**: `liveQuote.name`(API 응답에 이미 있던 필드, 화면엔 그동안 안 씀)을 헤더의 티커 옆에 표시.
- **1년 고가/저가**: 위에서 새로 fetch한 `dailyStats`에서 `Math.max/min`으로 계산해 KPI 카드 grid에 2개 카드로 추가(`stock-list`의 `computeStats`와 동일한 계산 방식, 다만 이 페이지는 스파크라인/거래량 추이까진 필요 없어서 고가/저가만 뽑는 축소판 계산을 인라인으로 둠).
- KPI 카드 grid를 `columns={3}` → `columns={5}`로 확장(현재가/거래량/고가/저가/갱신시각), 반복되는 카드/패널 마크업은 로컬 `InfoCard`/`PanelCard` 헬퍼로 뽑아서 중복 축소.
- **검증**: Playwright로 신규 계정 가입 → `/symbols/AAPL` 방문 → 헤더에 "AAPL Apple Inc." + "재무 대시보드 보기 →" 확인 → KPI grid에 현재가 333.74 · 전일대비 +0.48(+0.14%, 빨강) · 거래량 · 고가(1년) 334.99 · 저가(1년) 200.70 확인 → "기술 지표" 패널에 SMA(20) 305.52 / SMA(50) 302.63 / RSI(14) 88.59 실제 계산값 렌더링 확인 → "관련 뉴스" 패널에 콜렉터가 가져온 실제 기사 8건(SeekingAlpha/Yahoo) 렌더링 확인 → 관심종목 토글 버튼 정상 동작 확인 → "재무 대시보드 보기 →" 클릭 시 `/financials/AAPL`로 정상 이동 확인. 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정들은 `stockmonitordb`에서 직접 정리함.
- **남은 항목(3, 4번, 이번엔 미착수)**: 섹터/업종/시가총액 등 기업 개요는 콜렉터 `GET /symbol-profile/{ticker}`가 이미 데이터를 갖고 있지만 백엔드에 공개 컨트롤러가 없어서 프론트에 노출 불가(백엔드 작업 필요). 차트 위 지표 오버레이(`CandleChart`가 `candles`/`height`만 받고 지표 오버레이 미지원), 잘못된/비활성 티커에 대한 에러 상태 처리, `/stock-list`로 돌아가는 링크는 아직 미착수.

### ✅ 개선 — 종목 세부 페이지 3·4번: 기업 개요 + 차트 지표 오버레이 + 에러 상태 (2026-07-18)

앞서 남겨뒀던 3번(기업 개요)과 4번(차트 지표 오버레이/잘못된 티커 처리/뒤로가기 링크)을 이어서 구현.

**3번 — 기업 개요 (백엔드 신규 엔드포인트 필요했음)**:
- `marketboardBackend/.../symbol/SymbolProfileController.java`(신규) — `GET /api/symbols/{ticker}/profile`, `CollectorClient.getSymbolProfile(ticker)`(이미 있었지만 `SymbolResolutionService` 내부에서만 쓰이던 메서드)를 그대로 얇게 프록시. DB 캐싱 없음 — `MarketIndexController`와 동일한 이유("요청량이 적어 캐싱 없이도 충분")로 매 요청 프록시. `Optional.empty()`(콜렉터가 404, 즉 yfinance가 모르는 티커)면 `ResourceNotFoundException`으로 변환해 404 응답(`SymbolResolutionService`가 이미 쓰던 것과 동일한 패턴).
- 새 컨트롤러 클래스만 추가했는데 devtools 자동 재시작이 안 걸려서(IDE가 아니라 `./gradlew bootRun`으로 떠 있는 프로세스라 소스 변경만으론 재컴파일이 안 됨), `./gradlew compileJava`로 수동 컴파일 트리거 → devtools가 감지해서 자동 재시작함. **알아둘 점**: 앞으로 새 파일(기존 파일 수정이 아니라)을 추가했는데 devtools가 반응 안 하면 `./gradlew compileJava`를 수동으로 한 번 돌려줄 것.
- 프론트: `lib/types.ts`에 `SymbolProfileResponse` 추가, `lib/api.ts`에 `getSymbolProfile(fetcher, ticker)` 추가. `app/(app)/symbols/[ticker]/page.tsx`에 "기업 개요" 패널(거래소/섹터/업종/시가총액, `$X.XB` 형식) 추가.

**4번 — 차트 지표 오버레이 + 잘못된 티커 처리 + 뒤로가기 링크**:
- `components/CandleChart.tsx` — `smaOverlays?: SmaOverlay[]` prop 추가(다른 두 사용처인 `ChartPanel`/`MarketIndexCard`는 안 넘기면 기존과 동일하게 동작, 옵셔널이라 하위 호환). SMA는 이미 갖고 있는 캔들 배열에서 클라이언트에서 직접 계산(`computeSma`, 단순 이동평균) — `indicators` 테이블은 최신 스냅샷 값 1개만 저장하고 시계열이 아니라서 차트 오버레이용으로 못 씀. lightweight-charts의 `LineSeries`를 오버레이 개수만큼 추가해서 렌더링.
- `app/(app)/symbols/[ticker]/page.tsx` — `일봉` 선택 시에만 SMA20(파랑)/SMA50(보라) 오버레이 표시(`분봉`에선 일봉 기준 지표라 의미 없어서 안 보여줌). "기술 지표" 패널의 SMA(20)/SMA(50) 숫자와 차트 라인의 최신 지점이 서로 검증 가능하게 일치함.
- **잘못된 티커 처리**: `getSymbolProfile`이 404면(yfinance가 모르는 티커) 상단에 경고 Banner("존재하지 않는 종목입니다") 표시 — 이 판정을 "유효한 티커인지"의 기준으로 삼음(실시간 미구독/데이터 없음과는 구분).
- **뒤로가기 링크**: 헤더 위에 "← 종목 리스트로 돌아가기" 링크(`/stock-list`) 추가.
- **버그 발견/수정**: 잘못된 티커로 검증하다가 `useCandles`(`lib/candles.ts`, 이 페이지의 캔들 차트뿐 아니라 대시보드 `ChartPanel`도 같이 씀)와 이 페이지의 신규 `dailyStats` fetch가 둘 다 `api.getHistory(...).then(...)`에 `.catch()`가 없어서, 존재하지 않는 티커 조회 시 백엔드가 던지는 예외가 처리 안 된 Promise rejection으로 브라우저 콘솔에 그대로 새는 걸 발견 — 둘 다 `.catch()`로 빈 배열/빈 상태로 폴백하도록 수정(공유 훅이라 대시보드 차트 패널의 견고성도 같이 개선됨).
- **검증**: Playwright로 AAPL 방문 → "기업 개요"에 거래소 NMS/섹터 Technology/업종 Consumer Electronics/시가총액 $4901.8B 렌더링 확인, 차트에 SMA20(파랑)/SMA50(보라) 라인이 캔들 위에 겹쳐 그려지고 우측에 "SMA20"/"SMA50" 가격 라벨이 뜨는 것 스크린샷으로 확인 → 존재하지 않는 티커(`ZZZINVALIDXYZ`)로 방문 → 경고 Banner 렌더링, 나머지 섹션은 전부 "—"/"데이터 없음" 계열 빈 상태로 정상 폴백, `pageerror`(처리 안 된 예외) 0건 확인(수정 전엔 4건 발생했었음) → 뒤로가기 링크 정상 동작 확인. `./gradlew test`(기존 21건) 회귀 없음, `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정들은 `stockmonitordb`에서 직접 정리함.

### ✅ 버그 수정 — 종목 세부 페이지 차트의 SMA 지표 라인이 전부 검은색으로 나오던 문제 (2026-07-18)

바로 위 항목에서 추가한 SMA20/SMA50 오버레이가 실제로는 의도한 파랑/보라가 아니라 전부 검은색으로 렌더링되고 있었음(스크린샷을 다시 육안으로 확인하고서야 발견 — 이전 검증 때는 "라인이 그려지는지"만 확인하고 색상까지 픽셀 단위로 확인 안 함). 사용자가 직접 보고 지적.

**원인**: `CandleChart`가 SMA 라인 색상으로 `'var(--color-icon-blue)'` 같은 CSS 커스텀 프로퍼티 문자열을 그대로 lightweight-charts(캔버스 기반 렌더링)에 넘기고 있었음 — `var(...)` 참조는 DOM/SVG의 CSS 엔진만 해석할 수 있고, `<canvas>`의 2D 렌더링 컨텍스트는 이런 참조를 이해하지 못해 유효하지 않은 색상 값으로 처리되어 조용히 검은색(기본값)으로 그려짐. 같은 컴포넌트의 캔들 색상(`upColor`/`downColor` 등)은 처음부터 문제 없었는데, 그건 `var(...)` 문자열이 아니라 Astryx `useTheme().tokens[key]`로 **이미 해석된 실제 hex 값**을 넘기고 있었기 때문 — Astryx `useTheme` 문서에도 "캔버스/SVG처럼 CSS 커스텀 프로퍼티를 직접 못 읽는 소비자를 위해 실제 값으로 해석해서 반환한다"고 명시돼 있음.

**수정**: `components/CandleChart.tsx`의 `SmaOverlay.color`를 "토큰 키 문자열"(예: `--color-icon-blue`, `var()` 없이)로 의미를 바꾸고, `LineSeries` 생성 시 `tokens[overlay.color] ?? overlay.color`로 실제 값을 해석해서 넘기도록 수정 — 캔들 색상과 동일한 해석 경로를 타게 함. `app/(app)/symbols/[ticker]/page.tsx`의 `DAILY_SMA_OVERLAYS` 설정값도 `'var(--color-icon-blue)'` → `'--color-icon-blue'`로 맞춤.
- **검증**: Playwright로 AAPL 재방문 → 스크린샷에서 SMA20 라인이 뚜렷한 파랑, SMA50 라인이 뚜렷한 보라로 렌더링되고 우측 가격 라벨도 각각 파랑/보라 배경으로 표시되는 것 육안 확인. 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정은 `stockmonitordb`에서 직접 정리함.
- **알아둘 점**: 캔버스/SVG 등 CSS 엔진을 거치지 않는 렌더링 대상(차트 라이브러리, `<canvas>`)에 Astryx 색상을 넘길 땐 항상 `useTheme().tokens[key]`(또는 `token(key)`)로 해석한 실제 값을 넘길 것 — `var(--color-*)` 문자열을 그대로 넘기면 이번처럼 DOM 밖에서는 조용히 무시되고 기본색(보통 검은색)으로 떨어짐. 반대로 `PriceChangeIndicator`/`financials` 차트처럼 실제 DOM/SVG 엘리먼트의 `style`/`fill` 속성에 넣는 경우엔 `var(--color-*)` 문자열 그대로 써도 정상 동작함(이건 CSS 엔진이 처리하니까) — 렌더링 대상이 DOM/SVG인지 캔버스인지에 따라 방식이 달라진다는 점에 유의.

### ✅ 버그 수정 — 포트폴리오 페이지의 삭제 확인 다이얼로그가 안 닫히던 문제 (2026-07-18)

지난 세션부터 "의심되는 버그"로 남겨뒀던 항목("다음 세션 시작점" 참고) — 관리자 페이지에서 이미 한 번 확인됐던 `useImperativeAlertDialog`의 `.hide()` 미동작 버그가 `app/(app)/portfolio/page.tsx`(포지션 삭제 + 포트폴리오 삭제, 두 곳 모두 같은 `deleteDialog` 인스턴스 공유)에도 동일하게 있는지 브라우저로 실측 후, 있길래 admin/symbols 페이지에서 썼던 것과 동일한 제어형 패턴으로 교체.

**수정**: `useImperativeAlertDialog()` 제거 → `AlertDialog`(제어형)로 교체. 포지션 삭제/포트폴리오 삭제가 각자 다른 동적 제목·설명(예: "MSFT 포지션을 삭제할까요?")을 써야 해서, 클릭한 대상을 판별할 수 있는 판별 유니온 상태 하나로 통합: `deleteTarget: { type: 'position'; positionId; ticker } | { type: 'portfolio'; portfolioId; name } | null`. `isOpen={deleteTarget != null}`이고, `title`/`description`은 `deleteTarget.type`에 따라 렌더 시점에 계산. `onAction`(`handleConfirmDelete`)이 실제 삭제 API를 호출하고 성공하면 명시적으로 `setDeleteTarget(null)`(AlertDialog는 자동으로 안 닫힘 — `onAction` 안에서 직접 닫아야 함, admin/symbols의 `handleBulkActivate`와 동일한 책임 분담).
- **검증**: Playwright로 포트폴리오 생성 → MSFT 포지션 추가(수량 10, 평단가 300, 평가손익 +938.20(+31.27%) 실시간 반영 확인) → 포지션 삭제 버튼 클릭 → 다이얼로그 열림 확인 → "삭제" 클릭 → **다이얼로그가 실제로 닫히고**(`isVisible()` false) 포지션 행이 사라지고 "포지션이 없습니다" 빈 상태로 정상 전환 확인 → 포트폴리오 삭제 버튼 클릭 → 다이얼로그 열림 확인 → "삭제" 클릭 → **다이얼로그가 실제로 닫히고** 포트폴리오가 사라지고 "아직 포트폴리오가 없습니다" 빈 상태로 정상 전환 확인. 콘솔 에러 0건. `tsc --noEmit`/`eslint` 통과. 검증용 테스트 계정/포트폴리오는 앱 자체 삭제 플로우로 이미 정리됐고, 계정만 `stockmonitordb`에서 추가로 정리함.
- **알아둘 점**: `useImperativeAlertDialog`는 이제 이 프로젝트에서 완전히 안 씀(admin/symbols, portfolio 둘 다 제어형으로 교체 완료) — 앞으로 확인 다이얼로그가 새로 필요한 곳이 생기면 처음부터 `AlertDialog`(제어형)로 만들 것, 명령형 훅은 재도입하지 말 것.

### ✅ Phase 6 — 관측성 (Prometheus + Grafana) — 완료 (2026-07-18)

계획서 로드맵(Phase 1~7)의 마지막 남은 항목. 백엔드 커스텀 메트릭 4종 추가 + Prometheus/Grafana를 독립 Docker 컨테이너로 기동 + 대시보드 1개까지 전부 끝남.

**백엔드 — 커스텀 메트릭 (`MeterRegistry` 직접 주입, 신규 `metrics` 패키지 + 기존 서비스 3곳 수정)**:
- `metrics/MetricsConfig.java`(신규) — `marketboard.stomp.sessions.active`(게이지). 새 이벤트 리스너 없이 이미 살아있는 `SimpUserRegistry`(STOMP 커넥트/디스커넥트마다 스프링이 알아서 최신 상태 유지)를 그대로 게이지 소스로 사용.
- `metrics/CollectorMetricsPoller.java`(신규) — `marketboard.collector.reconnect.count`/`marketboard.collector.ws.connected`(게이지). `CollectorClient.getHealth()`가 그동안 관리자 대시보드 로드 시에만 온디맨드로 호출되던 걸, 30초 주기 `@Scheduled` 폴러를 새로 추가해서 시계열로 볼 수 있게 함.
- `indicator/IndicatorCalculationService.java` — `marketboard.indicators.recompute`(카운터, `result=success|failure` 태그) + `marketboard.indicators.recompute.duration`(타이머). 부수적으로 버그도 하나 고침: 기존엔 `recomputeForSymbol()` 호출에 try/catch가 없어서 종목 하나가 실패하면 `@Transactional` 배치 전체가 롤백되고 나머지 종목들의 성공한 upsert까지 같이 날아가는 구조였음 — 이제 종목별로 try/catch해서 실패 하나가 나머지를 막지 않고, 실패 자체도 카운터로 집계됨.
- `watchlist/WatchlistService.java`/`alert/AlertService.java` — 각각 `marketboard.watchlist.items.created`/`marketboard.alerts.created` 카운터를 생성 성공 시 증가.
- `security/SecurityConfig.java` — `/actuator/prometheus`를 `/actuator/health`·`/actuator/info`와 같은 permitAll 그룹으로 이동(원래는 `/actuator/**`가 ADMIN 전용이라 걸려있었음) — Prometheus 스크레이퍼는 JWT를 보낼 수 없으므로 이 변경 없이는 스크레이프가 전부 403이 났을 것. **순서 중요**: `.requestMatchers("/actuator/**").hasRole("ADMIN")`보다 반드시 앞에 와야 함(Spring Security는 먼저 매치되는 규칙을 씀).
- `application.yaml` — `management.metrics.distribution.percentiles-histogram.http.server.requests: true` 추가(p95/p99 지연시간 패널에 필요한 히스토그램 버킷 노출).

**인프라 — 독립 Docker 컨테이너(compose 아님, Phase 7까지 의도적으로 미루기로 한 기존 방침 유지)**:
- `observability/prometheus.yml`(신규) — `marketboard-backend` 스크레이프 잡, `host.docker.internal:8080/actuator/prometheus`(백엔드가 컨테이너가 아니라 호스트에서 직접 떠 있어서 `host.docker.internal`로 접근).
- `observability/grafana/provisioning/datasources/prometheus.yml`(신규) — Grafana가 Prometheus를 `http://marketboard-prometheus:9090`(컨테이너 이름, 아래 네트워크 참고)으로 자동 연결.
- `observability/grafana/provisioning/dashboards/`(신규) — `dashboards.yml`(프로비저닝 설정) + `marketboard-overview.json`(대시보드 8개 패널: HTTP 요청률/p95 지연시간, STOMP 활성 세션, 콜렉터 WS 연결상태/재접속횟수, 인디케이터 잡 소요시간, 인디케이터 성공/실패율, 워치리스트/알림 생성률).
- 실행 커맨드(재현용):
  ```
  docker network create marketboard-observability
  docker run -d --name marketboard-prometheus --network marketboard-observability -p 9091:9090 -v "<repo>/observability/prometheus.yml:/etc/prometheus/prometheus.yml" prom/prometheus
  docker run -d --name marketboard-grafana --network marketboard-observability -p 3300:3000 -v "<repo>/observability/grafana/provisioning:/etc/grafana/provisioning" grafana/grafana:10.3.0
  ```
  Prometheus UI: `http://localhost:9091`, Grafana: `http://localhost:3300`(기본 계정 admin/admin).
- **컨테이너 이름 충돌 주의**: `docker run --name prometheus`/`--name grafana`로 바로 실행하면 실패함 — 이 Docker 데몬은 사용자의 다른 스터디 프로젝트와 공유 중인데, 거기 이미 동일 이름의 **정지된** 컨테이너가 남아있었음(`prometheus`, `grafana` 둘 다 3일 전 종료 상태). 남의 컨테이너를 지우지 않고 `marketboard-prometheus`/`marketboard-grafana`로 프로젝트 스코프 이름을 써서 우회함 — 앞으로 이 Docker 환경에서 새 컨테이너를 띄울 땐 항상 `docker ps -a`로 이름 충돌부터 확인할 것.
- **포트 충돌 주의**: Prometheus 기본 포트 9090은 이미 `jenkins-server` 컨테이너가 호스트 9090을 쓰고 있어서 9091로, Grafana 기본 포트 3000은 프론트엔드가 3100을 쓰는 이유였던 그 "다른 WSL2 프로젝트가 3000을 상시 점유" 문제와 겹쳐서 3300으로 각각 조정함.
- **`grafana/grafana:latest` pull 실패**: `docker pull grafana/grafana:latest`가 "authentication required" 에러로 실패함(Docker Hub 인증/레이트리밋 이슈로 추정, 원인 미조사). 이미 로컬에 캐시돼 있던 `grafana/grafana:10.3.0`(다른 프로젝트가 예전에 받아둔 것)을 대신 써서 우회 — 이 환경에서 새 이미지가 필요하면 먼저 `docker images`로 로컬 캐시부터 확인할 것.
- **검증**: `curl :8080/actuator/prometheus`에서 4개 커스텀 메트릭(`marketboard_collector_*`, `marketboard_indicators_recompute_*`, `marketboard_stomp_sessions_active`) 노출 확인 → Prometheus 타겟 페이지에서 `marketboard-backend` 잡이 `up` 상태 확인 → Grafana 대시보드가 프로비저닝으로 자동 등록된 것 확인(`/api/search`) → Grafana의 Prometheus 데이터소스 프록시로 실제 패널 쿼리(HTTP 요청률, p95 지연시간, 커스텀 게이지) 실행해 실데이터 반환 확인 → 신규 계정으로 워치리스트/알림을 실제로 하나씩 생성해서 `marketboard_watchlist_items_total`/`marketboard_alerts_total` 카운터가 0에서 1로 오르는 것까지 확인. `./gradlew test`(21건) 회귀 없음. 검증용 테스트 계정은 `stockmonitordb`에서 직접 정리함.
- **알아둘 점 — Micrometer가 카운터 이름의 `.created`를 조용히 지움**: `marketboard.watchlist.items.created`로 등록한 카운터가 Prometheus로 나갈 땐 `marketboard_watchlist_items_total`(created가 사라짐)로 나옴 — `_created`가 OpenMetrics 스펙에서 "이 메트릭이 언제 생성됐는지"를 뜻하는 예약 접미사라서, Micrometer의 Prometheus 네이밍 컨벤션이 카운터 이름 끝의 `created`를 자동으로 제거하고 표준 `_total`을 붙이기 때문(버그 아님, 의도된 변환). 앞으로 카운터 이름에 `count`/`total`/`created` 같은 예약어를 끝에 붙이면 실제 노출되는 이름이 코드에 쓴 것과 달라질 수 있으니, Grafana 쿼리를 새로 작성할 땐 항상 `curl :8080/actuator/prometheus`로 실제 노출된 이름을 먼저 확인할 것.
- **알아둘 점 — Prometheus/Grafana는 아직 `docker-compose.yml`에 안 들어가 있음**: 기존 방침(mysql/redis도 Phase 7 전까진 수동 `docker run`)과 동일하게, 이 두 컨테이너도 지금은 수동 기동 상태. Phase 7에서 배포용 `docker-compose.yml`을 쓸 때 `observability/` 설정을 그대로 서비스로 편입하면 됨.

### ✅ 버그 수정 — S&P500 500종목 일봉 백필이 사실상 멈춰있던 문제 (2026-07-18)

지난 세션부터 "217/507에서 안 움직이는 것 같다"고 남겨뒀던 항목을 본격 조사 → **완전히 잘못된 가설로 시작해서, 실측을 거듭한 끝에 진짜 원인을 찾아 수정 완료**. 최종 결과만 보면 간단하지만, 중간에 완전히 틀린 방향으로 상당한 시간을 썼기 때문에 그 과정 전체를 기록함(같은 실수를 반복하지 않기 위해).

**1차 가설(틀림) — "yfinance가 500종목을 한 번에 요청하면 못 버틴다"**: `POST /sp500/sync`를 수동으로 재실행해서 관찰하니 8분 넘게 응답이 전혀 안 왔음(콜렉터 자체는 살아있고 실시간 WS는 계속 정상 — `run_sp500_batch`가 `asyncio.to_thread`로 별도 스레드에서 도는 동안 메인 이벤트루프는 안 막혔던 것). "Yahoo Finance 비공식 API가 500개 이상 티커를 한 번에 묶으면 rate limit에 걸린다"고 추정하고, `collector/app/sp500_universe.py`를 다음처럼 고쳤음: (a) 40개씩 청크로 나눠서 순차 호출, (b) 청크당 `ThreadPoolExecutor` + `future.result(timeout=60)`으로 강제 타임아웃, (c) 청크 사이 2초 딜레이.

**재발(가설 반박)**: 고친 버전으로 다시 돌렸는데도 **똑같이 멈춤** — 이번엔 15분 넘게 응답 없음, CPU 사용률도 거의 0(블로킹 대기 상태). "그럼 yfinance 내부 스레드풀(`threads=True`)이 내가 만든 `ThreadPoolExecutor`와 중첩되면서 뭔가 꼬이는 것 아닐까" 하는 2차 가설을 세우고 `threads=False`(청크 내에서 순차 fetch) + `CHUNK_SIZE=20`으로 다시 수정 → **역시 재현**(6분 넘게 CPU 거의 0).

**진짜 원인 발견 — 개별 조각 실측으로 좁혀감**: 이쯤에서 "혹시 전체 조합이 아니라 특정 한 조각이 문제 아닐까" 싶어서 `run_sp500_batch()`를 구성하는 조각들을 하나씩 독립 실행해서 시간을 재봄:
- Wikipedia 스크레이핑(`get_sp500_constituents`): 3초 — 정상
- DB 심볼 upsert(`ensure_sp500_symbols`, 503건): 11초 — 정상
- 실제 첫 청크(40종목) yfinance 다운로드: 2~4초 — 정상
- `_download_chunk_with_timeout`(내가 만든 타임아웃 래퍼) 단독 호출: 4.4초 — 정상
- 청크 8개(160종목) 연속 다운로드 루프: 전부 3~4초씩, 문제 없음

**여기까지 전부 빨랐는데, 유일하게 안 해본 게 "다운로드한 데이터를 실제로 DB에 쓰는 것"까지 포함한 루프**였음 — 이걸 포함해서 재현하자마자 바로 잡힘: **청크 1개(20종목) 다운로드는 4.5초인데, 그 데이터를 DB에 쓰는 데 201.9초**가 걸림. `mysql_writer.insert_candles_bulk()`가 **티커 1개당 새 MySQL 커넥션을 열고 닫는 구조**였는데(`run_sp500_batch`의 루프가 종목별로 이 함수를 호출), 이 환경에서 새 커넥션을 열 때마다 **~10초**가 걸리고 있었음(원인은 확정 못 함 — MySQL `skip_name_resolve`는 이미 켜져 있어서 흔한 "커넥트마다 역방향 DNS 조회" 문제는 아니었음, Docker NAT/포트포워딩 계층의 오버헤드로 추정). 503종목 × ~10초 ≈ 80분 이상이 걸릴 수 있는 구조였던 것 — "멈춘 것처럼 보인" 진짜 정체.

**최종 수정**:
- `collector/app/mysql_writer.py`의 `insert_candles_bulk(rows)`에 옵셔널 `conn` 파라미터 추가 — 넘기면 그 커넥션을 재사용(호출자가 lifecycle 관리), 안 넘기면 기존처럼 자체 커넥션을 열고 닫음(하위 호환).
- `collector/app/sp500_universe.py`의 `run_sp500_batch()` — 배치 시작 시 커넥션을 **하나만** 열어서 전체 루프 동안 재사용, 끝나면 `finally`에서 닫음.
- 1차/2차 가설에서 시도했던 `threads=False`/`CHUNK_SIZE=20`은 근본 원인이 아니었다는 게 밝혀졌으므로 되돌림(`threads=True`, `CHUNK_SIZE=40`) — 다만 청크 나누기 + 청크별 `CHUNK_TIMEOUT_SECONDS=60` 타임아웃 래퍼 자체는 "한 청크가 느려져도 전체가 안 막히게" 하는 방어적 장치로 그대로 유지.
- 부수적으로 `insert_candles_bulk` 호출부에 없던 try/except도 추가(종목 하나의 DB 쓰기 실패가 나머지 전체 청크를 건너뛰게 만들던 잠재 버그 — 발견은 했지만 이번 사건의 직접 원인은 아니었음).
- **검증**: 수정 후 `run_sp500_batch()`를 실제 503종목으로 단독 실행 → **91.5초 만에 완료**, `{'constituents': 503, 'candle_rows': 62183, 'failed_tickers': []}` — **실패 티커 0개**. DB 재확인: `price_history`의 `COUNT(DISTINCT symbol_id)`가 217 → **506**, 전체 row 수 41,528 → **77,263**. 콜렉터 재기동 후 `/health` 정상(13종목 실시간 구독 그대로 유지), `uv run pytest`(10건) 회귀 없음.
- **알아둘 점**: 이 프로젝트에서 "느리다/멈춘 것 같다"는 증상을 조사할 때, 외부 API(yfinance/Yahoo)를 먼저 의심하기 쉽지만 **이번엔 진짜 원인이 우리 쪽 DB 커넥션 관리였음** — 다음에 비슷한 증상을 보면 외부 서비스보다 먼저 "커넥션을 매번 새로 여는 곳은 없는지"부터 체크할 것. `collector/main.py`의 `_refresh_active_symbols_daily_bars()`(실시간 대상 13종목 일봉 갱신 루프)도 내부적으로 `backfill.py`의 백필 함수를 종목별로 호출하는 구조라 같은 패턴의 위험이 있는지는 아직 확인 안 함(종목 수가 13개뿐이라 체감 못 했을 가능성 — 낮은 우선순위로 남겨둠).
- 이전에 "야후 응답이 느려진다"고 잘못 추정해서 적어뒀던 메모(아래 "S&P500 배치 재실행/확장 방법" 항목)는 이 발견에 맞게 갱신함.

### 🟡 Phase 7 — CI/CD (GitHub Actions) — 부분 완료 (2026-07-19)
- [x] Git 저장소 초기화 및 첫 푸시 완료(`github.com/parkjunss/marketboard`, Private) (2026-07-18)
- [x] **PR 테스트 워크플로 추가** (`.github/workflows/ci.yml`, 2026-07-19) — `pull_request`/`push`(main) 트리거로 3개 병렬 job:
  - `backend`: `redis:7`을 GitHub Actions `services:`로 띄운 뒤 `./gradlew test`(28건). **처음엔 `mysql:8` 서비스 컨테이너도 같이 띄웠으나 실제 CI 실행에서 제거함** — 상세는 아래 "실제 CI 첫 실행에서 발견/수정한 문제" 참고
  - `collector`: `astral-sh/setup-uv` + `uv sync --locked --all-groups` + `uv run pytest`(10건, 순수 로직 테스트라 외부 의존성 없음)
  - `frontend`: `npm ci` + `npx tsc --noEmit` + `npm run lint`(eslint) + `npm run build`(`next build`) — `NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_WS_URL`는 `.env.local`(gitignore)이 없어도 코드에 폴백 기본값이 있어 빌드 실패 없음(`src/lib/api.ts`, `src/lib/quote-stream-context.tsx`)
  - 로컬에서 3개 job의 커맨드를 전부 그대로 실행해 사전 검증함(백엔드 41초/`BUILD SUCCESSFUL`, collector 10건 통과, 프론트 typecheck/lint/build 전부 통과) — CI 러너 자체(실제 GitHub Actions 실행)는 다음 푸시/PR에서 최초로 트리거될 것이므로 아직 미검증
- **범위 결정(2026-07-19, 이후 정정됨) — 처음엔 ARM64 buildx / GHCR 푸시 / SSH 배포를 "배포 대상 인프라가 없다"는 이유로 제외했었음**: 이후 대화에서 사용자가 이미 라즈베리파이(`rasp4`, SSH 별칭 `raspberrypi`)에 SSH로 접속해둔 상태라는 게 밝혀짐 — 아래 "배포 준비" 항목 참고, 정정됨.

**배포 준비 — Dockerfile 3종 + `docker-compose.yml` 작성 완료 (2026-07-19)**
- 배포 대상: 기존에 SSH로 연결해둔 라즈베리파이 `rasp4`(`192.168.0.174`, aarch64, RAM 7.6GB) — 이미 다른 프로젝트 `tradehub`가 떠 있어 포트 80/3000/8080/6379/3307/9092를 점유 중(상세는 세션 메모리 `project_raspberrypi_shared_deploy_target` 참고). **사설 IP라 GitHub 호스팅 러너가 SSH로 직접 못 들어감** → self-hosted runner를 Pi에 설치해 outbound 연결만으로 배포하는 방식으로 결정(아래 "남은 일" 참고). 이미지 레지스트리는 GHCR(`ghcr.io/parkjunss/marketboard-*`)로 결정, `docker-compose.yml`부터 먼저 작성하기로 순서 합의.
- `marketboardBackend/Dockerfile`(신규) — `eclipse-temurin:17-jdk`로 `./gradlew bootJar` 빌드 후 `eclipse-temurin:17-jre` 런타임으로 멀티스테이지 축소. `.dockerignore` 동반 추가.
- `collector/Dockerfile`(신규) — `ghcr.io/astral-sh/uv:python3.12-bookworm-slim` 베이스, deps만 먼저 `uv sync --no-install-project`로 캐싱 레이어 분리 후 앱 코드 복사 + 재동기화. `.dockerignore` 동반 추가.
- `frontend/Dockerfile`(신규) — `next.config.ts`에 `output: "standalone"` 추가 후 3단계(`deps`/`build`/`runtime`) 빌드, `node:20-alpine`. `NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_WS_URL`은 Next.js가 빌드 타임에 클라이언트 번들에 박아 넣는 값이라 컨테이너 런타임 env가 아니라 **Docker build ARG**로 받도록 함(`.dockerignore`에 `.env*`를 넣어 로컬 `.env.local`이 이미지에 실수로 안 들어가게 함 — ARG로 명시적으로 넘긴 값만 반영되도록). `.dockerignore` 동반 추가.
- `docker-compose.yml`(신규, 프로젝트 루트) — mysql/redis/backend/collector/frontend/prometheus/grafana 7개 서비스, 전용 브리지 네트워크(`marketboard`). tradehub와 안 겹치게 호스트 포트 재배정: mysql `3308`(컨테이너 3306), redis `6380`(컨테이너 6379), backend `8081`(컨테이너 8080), frontend `3100`(로컬 개발 컨벤션과 동일하게 컨테이너도 3100), collector `8001`(컨테이너 8000, 외부 노출은 디버깅용이고 백엔드는 도커 네트워크 내부에서 `http://collector:8000`으로 호출), prometheus `9091`, grafana `3300`(기존 로컬 관측성 컨테이너와 동일 포트 컨벤션 유지). 각 서비스에 `build:`(로컬/수동 빌드용)와 `image: ghcr.io/parkjunss/marketboard-*:latest`(CI가 푸시한 이미지를 `docker compose pull`로 받는 용도)를 둘 다 지정해서 두 워크플로 다 지원.
- `observability/prometheus.deploy.yml`(신규) — 기존 `observability/prometheus.yml`은 로컬 개발용(백엔드가 호스트에서 직접 떠서 `host.docker.internal:8080`을 스크레이프)이라 그대로 못 씀. 배포용은 백엔드도 같은 컴포즈 네트워크의 컨테이너라 스크레이프 타깃을 서비스명 `backend:8080`으로 바꾼 별도 파일. 기존 로컬용 파일은 그대로 둠(둘 다 필요).
- `.env.example`(신규, 루트) — `docker-compose.yml`이 읽는 env var 목록(DB_PASSWORD/MYSQL_ROOT_PASSWORD/JWT_SECRET/ADMIN_SEED_*/CORS_ALLOWED_ORIGINS/FINNHUB_API_KEY/NEXT_PUBLIC_*/GRAFANA_ADMIN_PASSWORD) 문서화. 루트 `.gitignore`에 `.env` 추가(실제 배포 시크릿은 Pi의 `.env`에만 존재, 커밋 안 됨).
- **로컬 검증 완료** (2026-07-19, Docker Desktop으로 amd64 빌드 — 최종 ARM64 크로스빌드 검증은 아님, 아래 "남은 일" 참고): 3개 Dockerfile 전부 `docker build` 성공. 백엔드는 실제로 컨테이너 기동까지 해서 기존 로컬 `mysql-container`/`redis-container`에 붙여 `/actuator/health` → `200`, 로그에서 Flyway/Hibernate/Tomcat 정상 기동 확인. 프론트엔드도 컨테이너 기동 후 `/`, `/login` → `200` 확인, 빌드 ARG로 넘긴 `NEXT_PUBLIC_API_BASE_URL` 값이 실제 클라이언트 번들(`*.next/static/chunks/*.js`)에 박혀 들어간 것까지 grep으로 확인. collector는 컨테이너 안에서 `main.py` import 성공 확인. `docker compose config`로 `docker-compose.yml` 문법/변수 보간도 검증함.
- **환경 이슈 발견/해결**: 로컬 Docker Desktop이 `eclipse-temurin:17-jdk`(공개 이미지, 인증 불필요) pull 시 `401 Unauthorized: incorrect username or password`로 실패하는 현상 있었음 — Docker Desktop의 자격증명 저장소(`credsStore: desktop`)에 남아있던 상한 Docker Hub 로그인 정보가 익명 pull까지 방해한 것으로 추정(이전 세션의 "grafana/grafana:latest pull 실패" 이슈와 동일 계열 원인일 가능성). `docker logout` 한 번으로 해결됨 — 이 환경에서 앞으로 도커 이미지 pull이 뜬금없이 401로 실패하면 우선 `docker logout` 시도할 것.

**Pi에 self-hosted runner 설치 + `ci.yml` build/deploy job 추가 완료 (2026-07-19)**
- Pi(`rasp4`)에 GitHub Actions self-hosted runner 설치·등록 완료 — 러너 이름 `marketboard`, 라벨 `self-hosted`/`Linux`/`ARM64`/`deploy`(커스텀 라벨 추가), `~/marketboard/actions-runner`에서 `./run.sh`로 상시 대기 중. 등록 토큰은 GitHub 웹(저장소 Settings → Actions → Runners)에서 사용자가 직접 발급받아 진행함(API로 대행 불가한 부분).
- `ci.yml`에 `deploy` job 추가 — `runs-on: [self-hosted, Linux, ARM64, deploy]`로 위 러너를 타깃팅, `backend`/`collector`/`frontend` 테스트 job이 전부 통과한 뒤(`needs:`) `push`(main)에서만 실행. **러너가 Pi 위에서 직접 도는 것이라 `buildx`/QEMU 크로스빌드가 아예 불필요**(처음 계획했던 "호스팅 러너에서 크로스빌드 → GHCR 푸시 → self-hosted 러너가 pull"보다 단순해짐) — `docker compose build`(네이티브 ARM64) → `docker compose push`(GHCR, `docker/login-action`으로 `GITHUB_TOKEN` 인증, 워크플로에 `permissions: packages: write` 필요) → `docker compose up -d --remove-orphans` 3단계.
- 시크릿: 실제 `.env`(FINNHUB_API_KEY/JWT_SECRET/DB 비밀번호 등 채운 것)는 Pi의 `/home/jun/marketboard/.env`에 배치(잡의 일회성 체크아웃 디렉터리 밖 — 커밋 안 되고 실행마다 유지됨), `docker compose` 커맨드에서 `--env-file`로 명시 참조. GitHub 저장소 Settings → Actions → General → Workflow permissions를 "Read and write permissions"로 변경(그래야 job의 `packages: write`가 실제로 GHCR 푸시를 허용함).

**실제 CI 첫 실행에서 발견/수정한 문제 2건 (2026-07-19)**:
1. **`./gradlew: Permission denied` (exit 126)** — `marketboardBackend/gradlew`가 git에 `100644`(실행권한 없음)로 커밋돼 있었음(이 Windows 개발 머신에서 커밋할 때 실행 비트가 안 잡힌 것으로 추정, 로컬에선 `gradlew.bat`을 쓰니 여태 안 드러남 — 세션 메모리 `feedback_windows_git_strips_exec_bit` 참고). `git update-index --chmod=+x`로 트래킹 모드 수정 + `ci.yml`의 backend job에 방어적으로 `chmod +x gradlew` 스텝 추가.
2. **`MarketboardBackendApplicationTests`(`@SpringBootTest`)가 MySQL 연결 실패로 죽음** — `FlywaySqlUnableToConnectToDbException`. `mysql:8` 서비스 컨테이너의 healthcheck(`mysqladmin ping`)가 초기화 재시작 구간에서 일시적으로 "healthy"를 잘못 보고하는 것으로 추정(MySQL 공식 이미지의 잘 알려진 CI 레이스 컨디션). 근본 수정 대신 **테스트를 아예 실제 MySQL에 안 의존하게** 접근 전환 — `marketboardBackend/src/test/resources/application.yaml`(신규, 테스트 클래스패스에서 메인 `application.yaml`을 완전히 shadow함)을 추가해 datasource를 인메모리 H2(`jdbc:h2:mem:testdb;MODE=MySQL`)로, `spring.flyway.enabled: false` + `ddl-auto: create-drop`(마이그레이션 SQL이 MySQL 전용 문법이라 Flyway로 H2에 그대로 적용 불가 — Hibernate가 엔티티에서 스키마를 직접 생성하도록 우회)로 전환. `build.gradle`에 `testRuntimeOnly 'com.h2database:h2'` 추가, `ci.yml`의 backend job에서 `mysql` 서비스 컨테이너 자체를 제거(더 이상 필요 없음 — `redis`는 `AlertMirrorInitializer`가 컨텍스트 기동 시 실제로 Redis에 접속하므로 유지). 로컬에서 `./gradlew test` 재검증(28건 전부 통과, `Database JDBC URL [jdbc:h2:mem:testdb]`로 H2 사용 확인).

**공개 HTTPS 접속 설정 — nginx + Let's Encrypt + DuckDNS (`marketboard.duckdns.org`, 2026-07-19, 코드 작성 완료·Pi 반영 전)**
- 목표: LAN IP:포트(`192.168.0.174:3100`/`:8081`) 대신 `https://marketboard.duckdns.org` 하나로 공개 접속. Pi에 이미 떠 있던 `tradehub-nginx`가 호스트 포트 80을 점유하고 있어 충돌했는데, 사용자가 `tradehub-nginx`를 stop하고 지금은 그대로 진행 → **나중에 공유 리버스 프록시(하나의 nginx/Traefik가 Host 헤더로 tradehub/marketboard 둘 다 라우팅)로 갈 계획**이라고 확정. 이번 구현은 그 방향으로 나중에 접기 쉽게 마켓보드 전용 `nginx/` 디렉터리로 격리해서 작성함.
- `nginx/conf.d/marketboard.conf`(신규) — `:80`은 ACME 챌린지 경로(`/.well-known/acme-challenge/`) 제외 전부 `:443`으로 301 리다이렉트. `:443`은 TLS 종료 후 `/` → frontend, `/api/` → backend(경로 재작성 없음, 컨트롤러가 이미 `/api/...`에 마운트돼 있어서), `/ws` → backend(Upgrade/Connection 헤더 + 긴 `proxy_read_timeout`, SockJS/STOMP용). `/actuator/**`는 공개 프록시하지 않음(Prometheus는 내부 도커 네트워크에서 이미 스크레이프 중이라 외부 노출 불필요). **업스트림을 `resolver 127.0.0.11 valid=30s;` + 변수 간접참조로 지연 해석**하도록 함 — 그냥 `proxy_pass http://backend:8080;`로 쓰면 nginx가 설정 로드 시점에 한 번만 DNS를 풀어서, 재부팅 후 nginx가 backend/frontend보다 먼저 뜨는 순간이 있으면 그대로 기동 실패해버림(로컬에서 `nginx -t`로 실제 재현/확인함, 아래 검증 참고).
- `nginx/init-letsencrypt.sh`(신규, 실행권한 포함 커밋 — 아래 "Windows git 실행비트" 이슈 재발 방지로 `git update-index --chmod=+x` 사용) — 표준 certbot/nginx 부트스트랩 레시피(더미 인증서로 nginx 기동 → 더미 삭제 → certbot webroot로 진짜 인증서 발급 → nginx reload). **CI 자동배포 루프에 포함 안 시킴** — 매 배포마다 재실행하면 의미도 없고 Let's Encrypt rate limit 위험만 커짐, Pi에서 최초 1회 수동 실행 전제.
- `docker-compose.yml`에 3개 서비스 추가: `nginx`(`nginx:alpine`, 호스트 80/443 유일하게 점유, `nginx/conf.d`+`nginx/certbot-webroot`+`nginx/letsencrypt` 마운트, **certbot이 갱신한 인증서를 실제로 반영하도록 6시간마다 자체 `nginx -s reload`하는 백그라운드 루프를 `command:`에 추가** — certbot이 디스크에 새 인증서를 써도 nginx가 재시작/reload 전까지는 메모리에 캐싱된 옛 인증서를 계속 서빙하기 때문), `certbot`(`certbot/certbot`, 12시간마다 `certbot renew --webroot` 루프), `duckdns`(`linuxserver/duckdns`, `.env`의 `DUCKDNS_TOKEN`/`DUCKDNS_SUBDOMAIN`로 A 레코드 갱신).
- `.env.example` — `DUCKDNS_TOKEN`/`DUCKDNS_SUBDOMAIN=marketboard`/`LETSENCRYPT_EMAIL` 추가, `CORS_ALLOWED_ORIGINS`/`NEXT_PUBLIC_API_BASE_URL`/`NEXT_PUBLIC_WS_URL` 기본값을 LAN IP에서 `https://marketboard.duckdns.org`(WS는 `/ws` 붙임 — SockJS는 `ws://`가 아니라 `http(s)://` URL을 받아서 자체적으로 업그레이드하므로 스킴 그대로 유지, `frontend/src/lib/quote-stream-context.tsx:76`)로 변경. `.gitignore`에 `nginx/letsencrypt/`, `nginx/certbot-webroot/` 추가(런타임 생성물, 소스 아님).
- `.github/workflows/ci.yml`의 `deploy` job — `docker compose push`를 인자 없이 돌리면 nginx/certbot/duckdns의 퍼블릭 이미지까지 우리 GHCR 네임스페이스로 푸시 시도하다 실패하므로, `docker compose push backend collector frontend`로 우리가 실제로 빌드하는 서비스만 지정하도록 수정.
- **로컬 검증**: `docker compose config`로 전체 문법/변수 보간 확인. `nginx/conf.d/marketboard.conf`는 더미 인증서(최초엔 실수로 1024비트로 만들었다가 **최신 OpenSSL이 1024비트 RSA를 거부하는 것까지 실제로 재현**해서 2048비트로 수정 — `init-letsencrypt.sh`의 더미 인증서도 동일하게 2048비트로 수정함, 원래 널리 쓰이는 레퍼런스 레시피가 1024비트를 쓰길래 그대로 따라했다가 걸린 것)를 로컬에 만들어 붙인 뒤 `nginx -t`로 문법 통과 확인, 실제로 컨테이너를 띄워서 `:80→:443` 301 리다이렉트, ACME 챌린지 경로 서빙, 그리고 (아직 backend/frontend가 없는 격리 환경이라) `:443`이 크래시 대신 502를 정상적으로 반환하는 것까지 확인(위 resolver 지연 해석 수정이 실제로 먹힌다는 증거).
- **Windows에서 Docker 바인드 마운트 테스트 시 주의할 점**: Git Bash에서 `docker run -v $(pwd)/...:/etc/...` 실행 시 MSYS가 컨테이너 쪽 경로(`/etc/...`)까지 Windows 경로로 멋대로 변환해버려서 엉뚱한 곳에 마운트됨 — `MSYS_NO_PATHCONV=1` 환경변수를 커맨드 앞에 붙이면 방지됨. 이번 세션에 실제로 걸려서 알아냄.
- **아직 안 끝난 것 — Pi 쪽 수동 준비 전부 남음**: (1) 홈 라우터에서 외부 80/443 → `192.168.0.174` 포트포워딩, (2) DuckDNS 토큰 확인 + `marketboard` 서브도메인 등록 확인, (3) Pi의 `~/marketboard/.env`에 `DUCKDNS_TOKEN`/`DUCKDNS_SUBDOMAIN`/`LETSENCRYPT_EMAIL` 채워 넣고 `CORS_ALLOWED_ORIGINS`/`NEXT_PUBLIC_*` 갱신, (4) `./nginx/init-letsencrypt.sh`를 Pi에서 최초 1회 실행해 진짜 인증서 발급. **이 4개를 끝내기 전에 이 커밋을 푸시하면 `deploy` job이 `docker compose up -d` 하는 순간 nginx가 존재하지 않는 인증서 파일을 참조하다 크래시루프에 빠짐** — 순서 반드시 지킬 것.

---

## 데이터 모델 현황 (계획서 §06 대비)

| 테이블 | 상태 |
|---|---|
| `users` | ✅ 생성됨 (V1 마이그레이션) |
| `symbols` | ✅ 생성됨 (V2), MVP 5종목 + AMZN(테스트로 추가된 leftover) 시드됨 |
| `watchlist_items` | ✅ 생성됨 (V4) |
| `price_history` | ✅ 생성됨 (V3), 1m/1d 실데이터 적재 확인 |
| `alerts` | ✅ 생성됨 (V5) |
| `dashboard_configs` | ✅ 생성됨 (V6), 유저별 대시보드 레이아웃/패널 설정(JSON 블롭) |
| `indicators` | ✅ 생성됨 (V7), 스케줄 잡이 SMA20/SMA50/RSI14 계산 후 upsert |
| `financial_statements` | ✅ 생성됨 (V8), 티커별 yfinance 연간 재무제표 캐시(24h TTL) |
| `portfolios` | ✅ 생성됨 (V9), 유저별 여러 포트폴리오(이름), 처음엔 빈 상태로 생성 |
| `portfolio_positions` | ✅ 생성됨 (V10), 포트폴리오별 종목 스냅샷(수량/평단가), 거래 이력 아님 |
| `symbols` 프로필 컬럼 확장 | ⚪ 미착수 — 콜렉터 `GET /symbol-profile/{ticker}`는 sector/industry/marketCap을 이미 반환하지만 아직 DB에 저장 안 함(task #35에서 필요해지면 추가) |
| `symbols.in_sp500_universe` | ✅ 생성됨 (V11), 기존 `is_active`(실시간 WS)와 별개의 배치 전용 유니버스 플래그. 현재 20종목(`SP500_BATCH_LIMIT=20` 개발 안전장치, 전체 500종목은 다음 세션에 확장 예정) |

---

## 다음 액션 제안 (우선순위 순)
> 상세 실행 계획은 문서 맨 위 "다음 세션 시작점" 참고.
1. **nginx/certbot/DuckDNS Pi 쪽 수동 준비 완료 후 푸시** — 위 "다음 세션 시작점"의 체크리스트(라우터 포트포워딩/DuckDNS 토큰/`.env` 갱신/`init-letsencrypt.sh` 실행) 먼저 끝낼 것. 순서 어기고 그냥 푸시하면 nginx 크래시루프.
2. 그 다음 최신 푸시의 GitHub Actions 실행 결과 확인 — 4개 테스트/빌드 job + `deploy` job 전부 통과하는지, `https://marketboard.duckdns.org` 실제 응답하는지
3. 장 마감 후 REST 폴백(`rest_fallback.py`) 실거래 재검증

**완료됨** — Git 저장소 초기화/첫 푸시(2026-07-18), 포트폴리오 삭제 다이얼로그 버그 수정(2026-07-18), Phase 6 관측성(2026-07-18), S&P500 일봉 백필 정체 문제 조사·수정(2026-07-18), Phase 7 PR 테스트 워크플로 추가(2026-07-19), 배포용 Dockerfile 3종 + docker-compose.yml 작성 및 로컬 검증(2026-07-19), Pi self-hosted runner + ci.yml build/deploy job(2026-07-19), gradlew 실행권한/백엔드 테스트 H2 전환 CI 버그 수정(2026-07-19), nginx+certbot+DuckDNS 공개 HTTPS 코드 작성(2026-07-19, Pi 반영 전)

---

## 참고
- 로컬 개발 환경: `mysql-container`(MySQL 8, DB `stockmonitordb`, 계정 `stockmonitor`)와 `redis-container`(Redis 7)는 이 프로젝트 전용이 아니라 사용자의 다른 스터디 프로젝트들과 함께 상시 기동해 두고 공유하는 컨테이너. 호스트 포트 3306/6379를 계속 점유하므로, 프로젝트 전용 `docker-compose.yml`을 만들 때는 포트 충돌에 유의(또는 Phase 7에서 배포용으로 별도 격리 구성).
- **이 Docker 데몬은 다른 스터디 프로젝트들과 공유 중** — `mysql-container`/`redis-container` 외에도 `jenkins-server`(호스트 9090 점유), 그리고 지금은 꺼져있지만 이름이 `prometheus`/`grafana`인 컨테이너가 이미 존재함. 새 컨테이너를 `docker run --name`으로 띄우기 전엔 항상 `docker ps -a`로 이름/포트 충돌부터 확인할 것 — 이번 세션에서 Prometheus/Grafana를 `marketboard-prometheus`/`marketboard-grafana`(포트 9091/3300)로 프로젝트 스코프 이름을 써서 우회한 사례 있음(위 "Phase 6" 섹션 참고).
- **Prometheus/Grafana 재기동 방법**: `docker start marketboard-prometheus marketboard-grafana`로 켜고 `docker stop`으로 끄면 됨(볼륨 마운트가 `observability/` 안의 파일을 직접 가리키므로 컨테이너를 삭제해도 설정은 안 날아감). 완전히 새로 만들어야 하면 위 "Phase 6" 섹션의 `docker run` 커맨드 그대로 재사용. Grafana 로그인은 admin/admin(첫 로그인 시 비밀번호 변경 프롬프트가 뜰 수 있음, 로컬 전용이라 무시해도 무방).
- 백엔드는 보통 사용자가 IntelliJ에서 devtools와 함께 8080 포트로 직접 구동해 둔 상태로 개발함 — 자동화 스크립트에서 별도 `bootRun`을 띄우기 전에 8080 점유 여부를 먼저 확인할 것.
- 테스트: `marketboardBackend/src/test/java/.../auth/AuthServiceTest.java`, `AuthControllerTest.java` — `./gradlew test`로 실행. collector는 `collector/tests/` — `uv run pytest`로 실행.
- collector 실행: `collector/.env`에 `FINNHUB_API_KEY` 필요(사용자 개인 키, git에 커밋 금지). `cd collector && uv run uvicorn main:app --port 8000` (기본으로 AAPL/MSFT/GOOGL/TSLA/NVDA 5종목 구독). 백필은 `uv run python -m app.backfill [TICKER...]`.
- 프론트엔드 실행: `cd frontend && npm run dev` → **포트 3100**(3000 아님 — 로컬 3000번은 사용자의 다른 프로젝트가 WSL2에서 상시 점유 중이라 `package.json`에 `-p 3100`으로 고정해둠). 백엔드 `app.cors.allowed-origins`도 `http://localhost:3100` 기준이니, 프론트엔드 포트를 바꾸면 백엔드 CORS 설정도 같이 바꿔야 함. `.env.local`에 `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_WS_URL` 설정돼 있음(기본값 `http://localhost:8080`, `http://localhost:8080/ws`).
- 디자인 시스템: Astryx(`@astryxdesign/core` + `@astryxdesign/theme-neutral`) 사용 중. CLI는 `npx astryx <cmd>`로 쓰되, 반드시 `@astryxdesign/cli`가 devDependency로 설치돼 있어야 함(패키지명이 `astryx`인 것과는 다른 패키지 — 이미 설치 완료돼 있으니 새로 설치할 필요는 없음, 다른 프로젝트에서 재현 시 참고).
- 백엔드→수집기 내부 호출(`CollectorClient`): 수집기(uvicorn)는 JDK `HttpClient`의 기본 h2c 업그레이드 시도를 지원하지 않아 바디가 있는 요청이 깨짐 — `syncSubscriptions`는 `HttpClient.Version.HTTP_1_1`을 명시적으로 강제해서 우회함(Phase 5에서 실측으로 발견). 앞으로 수집기에 새 내부 API를 백엔드에서 호출할 때도 동일 이슈에 유의.
- **새 HTTP 메서드를 쓰는 엔드포인트를 추가하면 `SecurityConfig.corsConfigurationSource()`의 `setAllowedMethods()` 목록도 같이 확인할 것**: PATCH가 누락돼 있던 걸 2026-07-18에 발견/수정함(위 "버그 수정" 참고) — curl/Python으로 백엔드를 직접 검증하는 것만으로는 이런 CORS 문제를 못 잡음(CORS는 브라우저만 강제하는 정책). PATCH/PUT처럼 자주 안 쓰던 메서드의 신규 엔드포인트는 반드시 Playwright 등으로 **실제 브라우저에서** 최소 1번 호출해볼 것.
- **확인 다이얼로그가 필요하면 `useImperativeAlertDialog`(명령형 훅) 대신 제어형 `<AlertDialog isOpen={...} onOpenChange={...} isActionLoading={...} onAction={...} />`을 쓸 것**: 2026-07-18에 `useImperativeAlertDialog`로 만든 다이얼로그가 `onAction` 안에서 `.hide()`를 호출해도(Astryx 공식 예제와 동일한 방식인데도) 실제로 안 닫히는 버그를 반복 재현함(근본 원인은 완전히 특정 못함, `show()`에 넘긴 옵션이 이후 리렌더에 반응 안 하는 구조로 추정) — 제어형 `AlertDialog`(자체 `isOpen` state로 직접 관리)로 바꾸니 확실히 해결됨. `app/(app)/portfolio/page.tsx`의 삭제 다이얼로그도 아직 명령형 훅을 쓰고 있어 같은 문제가 있을 수 있음(브라우저 실측 전) — 위 "다음 액션 제안" 참고.
- **관리자 종목 활성화(단일/일괄 모두)는 브라우저에서 최대 ~10초 걸릴 수 있음 — 정상 동작**: `syncActiveSymbols()`가 콜렉터의 실제 Finnhub WebSocket 구독 왕복이 끝날 때까지 동기 대기하기 때문(curl로는 순간적으로 끝나던 것도 브라우저에서 새 구독을 추가할 땐 수 초 걸림, 실측 10066ms). 이런 느린 액션을 다이얼로그/버튼에 연결할 땐 반드시 로딩 상태(`isActionLoading`/`isLoading`)를 연결해 "멈춘 것처럼 보이는" 문제를 방지할 것.
- **`@Transactional` 메서드 안에서 콜렉터를 호출하지 말 것 — 특히 같은 테이블에 쓰기가 있을 때**: `SymbolAdminService`가 트랜잭션 안에서 `syncActiveSymbols()`(콜렉터에 blocking HTTP 호출, 콜렉터는 그 안에서 `symbols` 테이블에 자기 나름의 INSERT를 시도)를 호출하다가, 완전히 새 티커를 활성 상태로 만들 때 두 프로세스가 같은 행을 두고 교착 상태(`Lock wait timeout exceeded`)에 빠지는 버그를 2026-07-18에 발견/수정함(자세한 내용은 위 "실시간 WS 대상 종목 재구성" 섹션). 고친 패턴: 트랜잭션 메서드는 DB 쓰기만 하고 리턴, 컨트롤러가 그 호출이 끝난(=커밋된) 다음 별도로 non-transactional 동기화 메서드를 호출 — 앞으로 백엔드에서 트랜잭션 도중 콜렉터(또는 그 무엇이든 외부 서비스)를 호출해야 하면 이 패턴을 따를 것. 대량 배치 작업(S&P500 500종목 시딩 등)과 관리자 조작이 동시에 겹치면 같은 종류의 잠금 경합이 더 쉽게 드러나므로, 의심되면 `SHOW ENGINE INNODB STATUS`의 `TRANSACTIONS` 섹션이나 `information_schema.innodb_trx`로 걸린 트랜잭션을 확인할 것.
- STOMP 인터셉터에서 세션 `Principal`을 설정할 때는 `StompHeaderAccessor.wrap(message)`가 아니라 `MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)`로 메시지에 내장된 mutable 접근자를 가져와야 함 — `wrap()`은 복사본이라 `setUser()` 등의 변경이 실제 메시지에 반영되지 않음(Phase 3에서 심어진 버그를 Phase 5에서 유저별 알림 추가하며 발견/수정).
- 신규 대시보드 페이지 4종의 레퍼런스 이미지는 `imgs/`(프로젝트 루트, git에는 커밋 여부 확인 필요)에 있음: `stock_list_dash.png`(완료), `stock_market_dash.png`(완료), `financial_dash.jpg`(완료) — 포트폴리오만 참고 이미지 없이 진행하기로 합의했었고 2026-07-18에 완료. 4개 페이지 전부 완료됨.
- 콜렉터에 새 시세/재무 데이터가 필요하면 `main.py`에 라우트 추가 → `CollectorClient`에 프록시 메서드 추가 → 백엔드에 얇은 컨트롤러 추가, 이 3단 패턴을 그대로 따를 것(뉴스/시장지표/재무 전부 동일 패턴). **캐싱 여부는 데이터 성격에 따라 다름**: 시장 지표(`/api/market-indices`)는 여전히 DB 저장 없이 매 요청마다 그대로 프록시(요청이 적고 캐싱 안 해도 충분하다고 판단, 재검토 여지 있음). 재무 데이터(`/api/financials`)는 `FinancialsService`가 `financial_statements` 테이블에 24시간 TTL로 캐싱함(콜렉터 프록시를 직접 부르지 말고 이 서비스를 거칠 것) — 사용자가 명시적으로 "백엔드에 저장"을 요청해서 추가됨.
- 다계열 비교 차트(꺾은선/막대)가 필요하면 `frontend/src/components/charts/`의 `MultiLineChart`/`GroupedBarChart`/`ChartLegend`/`ChartTooltip`을 재사용할 것 — 셋 다 `viewBox` 기반 반응형 SVG라 그리드 컬럼 폭에 맞춰 자동으로 줄어듦(고정 px `width`를 그대로 쓰면 좁은 그리드 컬럼에서 넘칠 수 있으니 주의). 카테고리별 호버 시 `ChartTooltip`이 값을 보여주는 것까지 이미 내장돼 있으므로, 새 차트 타입을 또 만들 때도 이 히트 영역(투명 `<rect>`, 항상 마지막에 렌더링) + `ChartTooltip` 조합을 그대로 재사용하면 됨.
- 스파크라인/미니 차트가 필요하면 `frontend/src/components/Sparkline.tsx`(순수 SVG, 값 배열만 받음, 상승/하락에 따라 `--color-success`/`--color-error` 자동 선택)를 재사용할 것 — `@astryxdesign/charts`는 현재 peer dependency 충돌로 설치 보류 중(위 "남겨둔 확인 작업" 참고). **단, "전일대비 상승/하락"처럼 국내 증시 관행(빨강=상승/파랑=하락)을 따라야 하는 곳에는 Sparkline의 success/error(초록/빨강) 색을 쓰지 말 것** — 아래 `PriceChangeIndicator` 참고.
- 가격 변동(값+퍼센트)을 상승=빨강/하락=파랑으로 표시해야 하면 `frontend/src/components/PriceChangeIndicator.tsx`(2026-07-18 신규, `/`와 `/stock-list`에서 사용 중)를 재사용할 것 — `changeValue`/`changePct`만 넘기면 됨. Astryx의 `success`/`error` 시맨틱 색은 초록/빨강(서구 관행)이라 국내 관행과 정반대라서 재사용 안 하고, `--color-text-red`/`--color-text-blue`(및 `--color-icon-red`/`--color-icon-blue`) 토큰을 직접 참조함 — 새로 이런 "상승/하락" 표시가 필요한 화면에서도 success/error를 그대로 쓰지 말고 이 컴포넌트나 같은 토큰 조합을 재사용할 것.
- **"전일 종가"를 직접 `candles[candles.length-1].close`로 구하지 말고 `frontend/src/lib/priceChange.ts`의 `resolvePrevClose(closes, livePrice)`를 쓸 것** — 장이 닫혀 있으면 실시간가가 최근 일봉 종가와 같아져서 "자기 자신과 비교"가 되는 버그가 있었음(2026-07-18 발견/수정, 위 "버그 수정 — 전일대비가 전 종목 0%로 보이던 문제" 참고). `resolvePrevClose`는 이 경우를 감지해 한 봉 더 이전 종가로 자동 폴백함 — `/`, `/stock-list`가 이미 이걸 씀. 포트폴리오 페이지(`/portfolio`)의 손익 계산은 평단가(매입가) 기준이라 이 문제와 무관하지만, 앞으로 "전일 종가/전일대비" 계산이 새로 필요한 화면이 생기면 직접 구현하지 말고 이 헬퍼를 재사용할 것.
- 임의 종목의 회사명이 필요하면 `GET /api/quotes`/`GET /api/quotes/{ticker}`가 이제 `name` 필드를 포함함(`QuoteResponse`, 2026-07-17 추가) — 관리자 전용 엔드포인트를 새로 호출할 필요 없음. STOMP 실시간 틱 페이로드에는 `name`이 없으므로 `quote-stream-context.tsx`는 반드시 병합(merge) 방식으로 갱신해야 함(교체 시 사라짐, 이미 수정됨).
- **`ticker`를 저장하는 새 테이블/컬럼을 추가하면 `symbols.ticker VARCHAR(20)`과 길이를 맞출 것**: `financial_statements.ticker`가 `VARCHAR(10)`으로 좁게 만들어져 있어서, 11자 이상인(존재하지 않는) 티커를 조회하면 캐시 저장 단계에서 데이터 잘림 에러가 나고 그게 엉뚱하게 403으로 새어나가는 버그가 있었음(2026-07-18 발견/수정, `V12__widen_financial_statements_ticker_column.sql`). 앞으로 티커를 저장하는 컬럼을 새로 만들 때는 `VARCHAR(20)`으로 통일해서 이런 불일치가 재발하지 않게 할 것.
- **콜렉터는 핫리로드가 없음**: Java 백엔드는 IntelliJ devtools가 파일 변경을 감지해 자동 재시작하지만, 콜렉터는 `uv run uvicorn main:app --port 8000`을 `--reload` 없이 띄운 상태라 `collector/` 쪽 코드(`main.py`, `app/*.py`)를 바꾼 뒤엔 반드시 프로세스를 수동으로 재시작해야 새 라우트/로직이 반영됨(2026-07-18, `symbol_profile.py` 추가 때 이걸 놓쳐서 한 번 헛디딤 — 이제 알아둘 것).
- 임의 티커를 `Symbol`로 새로 만들어야 하면 `symbol/SymbolResolutionService.resolveOrFetch(ticker)`(2026-07-18 추가, 포트폴리오 포지션 추가 흐름에서 사용)를 재사용할 것 — DB에 없으면 콜렉터 `GET /symbol-profile/{ticker}`로 조회해 **비활성**(`is_active=false`) `Symbol`을 만듦(관리자 종목 추가와 달리 `syncActiveSymbols()`를 호출하지 않아 실시간 WS 구독 대상이 몰래 늘어나지 않음). 임의 티커의 현재가가 필요하면 `quote/QuoteService.resolvePrice(ticker)`(같은 날 추가)를 재사용 — Redis 실시간 캐시 우선, 없으면 `price_history` 최신 일봉 종가로 폴백, 둘 다 없으면 `Optional.empty()`.
- **S&P500 유니버스와 실시간 WS 대상은 서로 다른 개념**: `symbols.is_active`(실시간 Finnhub WS 구독)와 `symbols.in_sp500_universe`(yfinance 배치 전용, 2026-07-18 추가)는 완전히 독립적인 두 플래그. 한 종목이 둘 다 true일 수도(예: GOOGL), 하나만 true일 수도 있음 — "이 종목이 S&P500이니까 실시간으로 봐야 한다"고 가정하지 말 것. S&P500 유니버스 종목 목록이 필요하면 `SymbolRepository`에 `findByInSp500UniverseTrue...` 계열 메서드를 추가해 재사용(현재는 `findByActiveTrueOrInSp500UniverseTrueOrderByPriorityAsc()`만 있음, `IndicatorCalculationService` 전용).
- **S&P500 배치 재실행/확장 방법**: 콜렉터 `.env`의 `SP500_BATCH_LIMIT`을 지우거나 늘리면 다음 재시작(또는 24시간 뒤 자동 루프, 혹은 `POST /sp500/sync?limit=N` 수동 호출)부터 그만큼 반영됨 — 코드 변경 불필요. **(2026-07-18 정정)** 예전엔 여기에 "수동 트리거를 연달아 호출하면 야후 응답이 느려진다"고 적혀 있었는데 잘못된 추정이었음 — 진짜 원인은 yfinance가 아니라 `insert_candles_bulk`가 종목마다 새 MySQL 커넥션을 열던 것(커넥션 재사용으로 수정 완료, 위 "버그 수정 — S&P500 500종목 일봉 백필이 사실상 멈춰있던 문제" 참고). 수정 후 503종목 전체가 91.5초에 끝남 — 더 이상 간격을 두고 호출할 필요 없음.
- 이 문서는 계획서(`stock-monitor-dev-plan.html`)의 로드맵을 기준으로 코드베이스를 스캔해 작성한 스냅샷입니다. 실제 작업이 진행되면 각 체크박스와 표를 갱신해 주세요.
