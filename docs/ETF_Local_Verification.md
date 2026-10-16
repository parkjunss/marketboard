# ETF 로컬 실행 검증 — 2026-10-16

## 실행 환경

- `compose.local.yml`의 `marketboard-local` 구성으로 실행. 기존 `mysql-container:3306`과 별개인 MySQL 8.4, Redis, Spring, Collector, Next.js 컨테이너 사용.
- 화면: http://localhost:13100/etf
- API: http://localhost:18081 / Collector: http://localhost:18001
- 로컬 MySQL `flyway_schema_history`에서 V38 및 `success=1` 직접 확인.
- ETF SPY/QQQ/EFA/EEM/IEF/GLD 각각 실제 5년 일봉 1,254개 수집. 합성 가격이나 API 응답 목업은 사용하지 않음.

## 검증 범위

- 2024-10-01~2025-10-01의 3개 전략 비교, 검토 저장, 동일 멱등 키 재전송 시 같은 ID/근거 반환.
- 비로그인 ETF API 401, 타 사용자 검토/모의 계좌 조회 및 중지 404.
- 실제 브라우저 로그인, 검토 조회, 채택 판단, 모의 계좌 생성, 동일 날짜 반복 갱신 시 신호 기록 불변 확인.
- 현재 월말 신호와 확인한 USD 현금을 사용한 현금 전용 포트폴리오의 리밸런싱 제안 표시.
- 데스크톱 1440px / 모바일 390px 스크린샷 및 문서 가로 넘침 검사, 브라우저 예외 검사, 운용 중지와 기록 보존.
- 검증 중 다크 테마에서 제목/본문이 어둡게 표시되는 문제 발견: 공유 타이포그래피 색상 토큰을 ETF 페이지에 맞게 지정하고 여백/입력란 스타일 보완.

## 재실행

```powershell
rtk proxy docker compose -f compose.local.yml up -d --build
rtk proxy node scripts/verify-etf-local.cjs <설치된-playwright-모듈-경로> <결과-디렉터리>
```

`scripts/verify-etf-local.cjs`는 로컬 주소만 대상으로 검증용 계정과 기록을 생성한다. 비밀번호/토큰은 출력하거나 파일에 저장하지 않는다. 생성한 모의 계좌는 성공/실패 시 중지하며 기록은 보존한다. 기존 인증 제한(분당 5회)을 우회하지 않고 요청 간격과 429 재시도를 적용한다.

## 검증 한계

- 새 모의 계좌를 시작한 당일이므로 실제 다음 거래일의 체결 및 08:30 UTC 스케줄 경과는 아직 관측하지 않음. 이 부분은 기존 계산/저장 테스트와 구분한다.
- 배당·분할 조정 데이터 일관성, 실제 주문 체결, 수익성을 검증한 결과가 아니다.
- 수집기 초기 병렬 작업에서 yfinance 캐시/잠금 경고가 관찰됐으나, ETF 6종 개별 수집과 해당 API 흐름은 완료됨.
- 운영 환경 배포나 운영 DB 변경은 하지 않음. 검증용 로컬 서비스는 사용자가 확인할 수 있도록 실행 상태 유지.
