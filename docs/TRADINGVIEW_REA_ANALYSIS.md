# TradingView REA 관찰 결과

작성일: 2026-10-14 · 상태: 공개 비로그인 차트 2차 관찰 및 봉 정보 1차 적용 완료

## 조사 범위와 방법

대상: https://www.tradingview.com/chart/?symbol=NASDAQ%3AAAPL

REA CLI `rea-agents@6.4.0`의 `capture-browser-scenario`를 사용했다. Chrome의 독립된 임시 프로필에서 1440×900, en-US, America/New_York 설정으로 공개 페이지를 열고 접근성 스냅샷과 조작 결과를 수집했다. 첫 관찰에서는 화면 이미지도 캡처했다. REA가 소유한 브라우저 프로세스는 각 시나리오 종료 후 종료되었다.

Codex 연결 진단의 `config_drift`를 승인된 `setup --client codex --yes`로 복구했다. 기존 설정 백업 경로는 `C:\Users\wnstj\.codex\config.toml.rea.backup`이다. 현재 대화에는 REA MCP 도구가 노출되지 않아 CLI로 분석했다.

이 결과는 실행 중 공개 UI에 대한 관찰이다. 내부 소스 구조·저장 API·데이터베이스·렌더링 알고리즘을 역추적한 결과는 아니다. 로그인이나 유료 기능은 사용하지 않았다. 2차에서는 Playwright로 마우스·키보드를 조작하고, 같은 소유 브라우저에 REA `inspect-web-page`를 연결해 상태를 관찰했다. REA가 직접 포인터를 조작했다고 해석하지 않는다.

## Evidence 목록

| 참조 | REA Evidence ID | 관찰 |
|---|---|---|
| E1 | `ev_f6119f6cd4c902fab9e22707d3ddcbdc86c42690ed0dda9f2282e3e5050073b7` | 첫 로딩, 접근성 및 화면 이미지 캡처 성공 |
| E2 | `ev_21d36a3adbcb58174aca718461119c02b220f87d4a0561ec89e70c5fb6bf4420` | 차트 도구 모음·OHLC 범례·기간 버튼·보조 패널 확인 |
| E3 | `ev_c1f0f3d72ead248f3c439b08f962ed36c30bb92a99222723e63988744a7ff9c8` | 추세 도구·지표 템플릿·비로그인 저장 조작 |
| E4 | `ev_e34235b031a08db7fd50b67c443e909d29c51870961653d4b13bbdb257438fb2` | 시간봉 메뉴·비교 버튼 조작 |

Evidence ID는 이번 CLI 실행 결과의 식별자다. 전체 원본 JSON·이미지 파일을 저장소에 보존하지 않았으므로 ID만으로 이후 세션에서 원본을 재조회할 수 있다고 보장하지 않는다. 아래의 관찰 요약과 실행 시나리오를 저장한다. 실행 시각과 외부 사이트 상태에 따라 재실행 결과는 달라질 수 있다.

## 확인된 동작

| 기능 | 직접 관찰 | MarketBoard에 주는 의미 |
|---|---|---|
| 정보 범례 | 차트 상단에 종목·시간봉·거래소 및 O/H/L/C·가격 변화·등락률 표시. 거래량 범례에 Hide·Settings·Remove 버튼 존재 (E2) | 현재 차트에 읽기 쉬운 봉 정보와 지표별 조작을 제공하는 우선순위가 높음 |
| 도구 배치 | 위쪽에 종목·시간봉·차트 종류·지표·알림·재생·저장, 왼쪽에 드로잉, 오른쪽에 관심종목·알림·Object tree and data window (E2) | 탐색·분석·객체 관리를 구분하되 필요한 조작부터 도입 |
| 추세 도구 메뉴 | Trend tools 클릭 성공. Horizontal line·Horizontal ray·Vertical line·Crossline·Parallel channel 등과 즐겨찾기 버튼 표시 (E3) | 1차 수평선·추세선, 후속 수평 레이·채널 순서가 적절 |
| 단축키 | Horizontal line `Alt+H`, Horizontal ray `Alt+J`, Vertical line `Alt+V`, Crossline `Alt+C` 표시 (E3) | MarketBoard도 도구별 키보드 접근 경로를 제공; 동일 키 채택은 충돌 검토 후 결정 |
| 드로잉 관리 | Magnet mode 설명은 가까운 OHLC 값으로 스냅. Keep drawing·Lock drawings and indicators·Hide all drawings·Remove objects 버튼 존재 (E2) | 선을 추가할 때 선택·취소·삭제와 표시 상태를 함께 고려 |
| 지표 템플릿 | Indicator templates 클릭 후 Save indicator template…·Open template… 메뉴 표시. Esc로 다음 조작 진행 (E3) | 기존 지표 설정 저장을 템플릿으로 확장할 후보; 실제 저장 성공은 미검증 |
| 레이아웃 저장 | Save 버튼 클릭 후 “Save your chart”, 무료 계정 가입 및 나중에 설정으로 복귀할 수 있다는 안내 표시 (E3) | 비로그인 제한·로그인 저장·저장 실패 안내를 사용자 여정에 포함 |
| 봉 간격 | 1 day 메뉴에서 분·시간·일 그룹, 1D 선택 상태, 1W·1M 등과 즐겨찾기 표시 (E4) | 자주 사용하는 시간봉을 쉽게 전환; 지원하는 데이터 간격만 노출 |
| 조회 기간 | 아래쪽에 1D·5D·1M·3M·6M·YTD·1Y·5Y·All. 접근성 이름에는 각 버튼에 대응하는 봉 간격도 포함 (E2) | 조회 기간과 봉 간격을 별도 개념으로 정의하고 자동 연동 규칙 명시 |
| 비교 | Compare symbols 버튼 클릭은 완료, 후속 스냅샷에 관련 텍스트 표시 (E4) | 비교 진입점만 확인. 검색·선택·비교선 생성은 추가 관찰 필요 |

## 확인하지 못한 부분

- 십자선 이동 시 OHLC·등락 정보 갱신은 2차에서 확인했다. 추가 지표 값 갱신은 미확인.
- 수평선·추세선 생성 시 2차에서 가입 안내가 나타났다. 실제 생성·끝점 이동·스냅·삭제·확대 후 위치 유지·취소는 비로그인 상태에서 확인하지 못했다.
- 로그인 이후 레이아웃 복원, 종목·시간봉 간 드로잉 공유, 저장 충돌 규칙은 미확인.
- 차트 설정 대화상자, 전체화면 진입·종료, 모바일·키보드 조작 전체, 비교 차트 실제 생성, 리플레이 재생은 미검증.
- 정적 번들 분석·네트워크 계약 분석은 수행하지 않았다. UI 증거로 서버 구조를 추정하지 않는다.

## 적용 우선순위 보완

1. **정보 표시와 조작:** OHLC·등락·거래량·지표 값 범례, 최근 구간 복귀와 전체화면. 십자선 갱신 규칙은 추가 관찰 후 확정.
2. **분석 표시와 관리:** 수평선·추세선 생성에 선택·취소·삭제·숨김을 포함. 잠금·OHLC 스냅은 실제 편집 동작 확인 후 추가.
3. **저장과 복원:** 로그인 사용자별 분석 저장과 저장 상태 표시. TradingView 저장 안내를 관찰한 것이며 MarketBoard 저장 범위는 자체 요구에 맞춰 정의.
4. **반복 조작 감소:** 자주 쓰는 시간봉, 기존 지표 설정을 바탕으로 템플릿 저장·불러오기.
5. **작업 연결:** 기존 가격 알림·투자 검토 연결. 비교·다중 차트·리플레이는 핵심 분석 여정 완성 후 진행.

위 우선순위는 관찰에 기반한 설계 제안이며, 해당 기능이 MarketBoard에 구현되었다는 의미는 아니다. 전체 계획은 [TRADINGVIEW_FEATURE_ADOPTION.md](TRADINGVIEW_FEATURE_ADOPTION.md)를 참고한다.

## 재실행

실행 입력은 `docs/refactor/tradingview-rea-scenario.json`과 `docs/refactor/tradingview-rea-controls.json`이다. `docs/refactor/run-tradingview-rea.ps1`은 Evidence ID·단계 성공 여부·접근성 텍스트 일부를 출력한다. 출력은 각 메뉴 끝부분 85줄로 제한되어 전체 접근성 트리를 보존하지 않는다.

```powershell
rtk proxy powershell -NoProfile -File docs/refactor/run-tradingview-rea.ps1
rtk proxy powershell -NoProfile -File docs/refactor/run-tradingview-rea.ps1 -Scenario docs/refactor/tradingview-rea-controls.json
```

Node.js, npm 접근, 로컬 Chrome이 필요하며 외부 사이트를 실제 실행한다. 초기 대기는 12초이고 메뉴 조작 제한 시간은 5초이므로 사이트 로딩이 느리거나 UI 이름이 바뀌면 실패할 수 있다.

## 2차 관찰: 십자선과 드로잉 접근 제한

`docs/refactor/observe-tradingview-drawings.mjs`에서 Playwright로 공개 차트 포인터를 이동했다. REA는 동일 페이지의 상태를 수집했다. 원본 Evidence JSON·접근성 텍스트·화면 이미지를 `docs/refactor/tradingview-evidence/`에 보존했다. CLI 연결 endpoint는 로컬 임시 브라우저 주소이며 재실행 시 달라진다.

Git에는 최종 관찰 5개 상태의 접근성 텍스트·화면·Evidence ID 목록을 포함한다. 큰 원본 JSON과 첫 실패 시도의 중복 파일은 `.gitignore`로 제외하고 로컬에 보존한다. 원본 JSON이 필요한 경우 관찰 스크립트를 재실행한다.

| 상태 | Evidence ID | 결과 |
|---|---|---|
| 초기 | `ev_5117b2de91bd5acef3402e2224e010893ef9adda6adcef2cb534ec1e8d7aa9fe` | 최신 봉 범례 표시 |
| 왼쪽 봉 | `ev_27e263498768235c8f890390853367f222aa4a21f2b3169bf033e1d5a375cc9f` | O 307.46 / H 315.45 / L 306.69 / C 315.20 / +2.90%로 변경 |
| 오른쪽 봉 | `ev_eaac63f1cb1b2dbcbeefdc1499ed4109a04500827a6978ff0c1caa4da97bc0a8` | O 306.21 / H 307.66 / L 302.94 / C 305.59 / −0.11%로 변경 |
| 수평선 시도 | `ev_a7b3f686650841377d6b96264e1795012200bf46ebd7b46619840b3cf7e3c021` | “Join for free to access horizontal line…” 안내 표시 |
| 추세선 시도 | `ev_7214a353f00927ac8fdfc4c29fffead8d7ba62c62d444a13896205a545db5775` | 수평선 안내를 닫은 후 추세선 선택·차트 클릭 시 “Join for free to access trendline…” 안내 표시 |

포인터를 다른 봉으로 이동하자 범례가 해당 봉 값으로 바뀌었다는 사실은 관찰되었다. 등락의 내부 계산식과 모든 시간봉에서 같은 방식으로 동작하는지는 별도 확인 대상이다. MarketBoard의 전봉 종가 기준 등락 계산은 자체 적용 규칙이다.

첫 시도는 수평선 가입 안내가 열린 상태에서 추세선 클릭을 이어가 timeout으로 종료되었다. 이때의 `horizontal-created`, `horizontal-moved`, `horizontal-delete` 파일명은 당시 **시도 단계 이름**이며 선 생성·이동·삭제가 성공했다는 의미가 아니다. 후속 시나리오는 안내를 감지하고 닫은 뒤 추세선을 별도로 시도해 정상 종료했다. 가입 안내를 우회하거나 계정을 생성하지 않았다.

재실행에는 설치된 REA 패키지 디렉터리를 전달한다.

```powershell
rtk proxy node docs/refactor/observe-tradingview-drawings.mjs C:\Users\wnstj\AppData\Local\npm-cache\_npx\9b9809298fe40d2c\node_modules\rea-agents
```

위 경로는 이번 환경에서 확인한 패키지 위치다. 다른 환경에서는 실제 설치 경로와 Chrome 위치를 확인해야 한다. 이 스크립트는 전용 브라우저·원본 Evidence·화면을 생성하므로 일반 테스트나 CI에 자동 연결하지 않는다.

## MarketBoard 첫 적용과 완료 기준

공용 `CandleChart`에 선택 봉 정보 표시를 추가했다. 기존 종목 상세·대시보드·백테스트 차트 호출자는 별도 변경 없이 표시 기능을 공유한다.

- 기본 표시: 최신 봉의 시각·OHLC·전봉 대비 등락률·거래량.
- 십자선 이동: 이벤트의 timestamp와 실제 봉 timestamp를 매칭해 해당 봉 정보 표시.
- 이탈·데이터 없는 위치: 최신 봉 정보로 복귀.
- 종목 또는 데이터 배열 변경: 이전 배열의 선택 상태를 재사용하지 않음.
- 최초 봉·전봉 종가가 없거나 0인 경우: 등락률은 `—`.
- 결측·비유한 값: `—`, 실제 거래량 0은 0으로 표시.
- 시각 표기: 명시적 UTC. 일봉 전용 날짜 형식과 사용자 시간대 선택은 후속 UX 개선 후보.
- 테마 변경으로 차트를 재생성할 때 봉·지표 데이터를 다시 채움.

검증: 포맷 테스트 2개, 십자선 timestamp 매칭·이탈 복귀 테스트 1개 통과. 프런트 린트와 프로덕션 빌드 통과. 테스트의 차트 이벤트는 모의 이벤트이며 실제 MarketBoard 페이지에서 터치·레이아웃·실데이터 동작을 확인한 것은 아니다. 드로잉·전체화면·저장 기능은 아직 구현하지 않았다.
