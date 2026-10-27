# 비회원 분석 페이지 공개

## 적용 범위 (2026-10-27)

- 공개 페이지: `/symbols/{ticker}`, `/financials`, `/financials/{ticker}`, `/screener`, `/etf`.
- 비회원 메뉴에 스크리너, 포트폴리오 설계, 재무 비교 추가.
- 종목 상세는 비회원에게도 REST 시세 초기값을 표시한다. 인증이 필요한 실시간 구독은 기존 정책을 유지한다.
- 비회원도 차트 지표를 화면에서 조정하고 ETF 배분안을 서버에서 계산할 수 있다.
- 관심종목 추가, SMA 설정 저장, 가격 알림, 설계 저장, ETF 검토 기록 및 모의 운용은 로그인 후 이용한다.
- 개인 관심종목·설정·알림·저장 설계·검토·계좌를 비회원 진입 시 조회하지 않는다.
- `/symbols/{ticker}/reports`, 개인 포트폴리오, 투자 기록, 실행, 관리자 페이지는 계속 인증이 필요하다.

## API 경계

추가 공개 GET 경로:

- `/api/symbols/*/profile`
- `/api/financials/*`
- `/api/indicators/*`
- `/api/analysis/*`
- `/api/screener/etf-momentum`

구조화된 검색/계산 입력을 받는 읽기 전용 POST만 공개:

- `/api/screener/search`
- `/api/portfolio/design`

저장용 `/api/portfolio/designs`와 ETF 기록·계좌 API는 공개하지 않는다. 전체 `/api/portfolio/**` 또는 `/api/etf/**`를 허용하지 않는다.

## 검증

- Java MVC 테스트 39개 통과: PublicAnalysisSecurityTest, PortfolioDesignControllerTest, EtfMomentumControllerTest. 익명 조회/계산 허용, 입력 검증, 개인 API GET/POST 401을 확인했다.
- 프런트 공개 경로·로그인 복귀 단위 테스트 6개 통과.
- 변경한 TS/TSX 파일 ESLint 및 `tsc --noEmit` 통과.
- 로컬 Next.js + Edge 브라우저 검증 통과: 공개 화면 5개와 메뉴, 비회원 시세 표시, ETF 계산, 로그인 복귀 경로, 개인 페이지 차단, 개인 API 미호출 및 처리되지 않은 오류 없음.
- 브라우저 API는 합성 응답이다. 데이터 실패 시 화면 처리와 시세/계산 응답 렌더링을 검증했으며 실데이터·운영 배포 검증은 아니다.
- 추가 실행한 기존 MarketReportSecurityTest는 로컬 Redis `localhost:16380` 연결 불가로 Spring 컨텍스트 초기화에 실패했다. 관련 코드는 변경하지 않았다.

재현 명령 (프로젝트 루트 / 프런트 / 백엔드 작업 디렉터리에 맞춰 실행):

```text
rtk proxy node scripts/verify-public-access.cjs <playwright 모듈 경로> http://localhost:13110
rtk proxy node --test tests/public-routes.test.mjs tests/login-return.test.mjs
rtk proxy npx.cmd tsc --noEmit
rtk proxy .\gradlew.bat test --tests "*PublicAnalysisSecurityTest" --tests "*PortfolioDesignControllerTest" --tests "*EtfMomentumControllerTest"
```

브라우저 스크립트는 기본 Edge를 사용한다. 다른 설치 브라우저는 `PLAYWRIGHT_CHANNEL`로 선택한다.
