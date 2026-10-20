# 주문 실행 P0 구현 및 검증

작성일: 2026-10-19

## 구현

- Python Decimal 실행 엔진, 고정 SPY 모의 거래소, SPY 보유 목표 주문 산출.
- 주문 의도·자금 예약, 부분 체결·취소·거절, 결과 불명·대사, 중복·역순 수신, 재진입 금지·중지·리스크 차단.
- Spring 인증 API, 계좌별 DB 행 잠금, 요청 멱등성, 원장·상태·DECIMAL 잔액의 원자적 저장, 재생 검증.
- V40 신규 테이블. 기존 ETF 모의 계좌를 변경하지 않음.
- `/execution` 최소 조작 화면 및 메뉴. 고정 모의 가격·실거래 미연결을 명시.

계산·저장 경계와 세부 한계는 `adr/0003-execution-sandbox-persistence.md`를 따른다.

## API

| API | 용도 |
|---|---|
| POST `/api/execution` | `Idempotency-Key`와 설정으로 실행 생성 |
| GET `/api/execution` | 본인의 최근 실행 30개 |
| GET `/api/execution/{id}` | 본인 실행 상태 |
| POST `/api/execution/{id}/commands` | `key`, `operation`, 선택적 `orderId`, `quantity` |
| POST `/api/execution/{id}/verify` | DB 명령 재생 및 저장 전체 상태 비교 |

내부 계산: POST `/execution/replay` (collector). Spring이 저장된 설정·명령만 전달한다. 내부 collector 포트를 외부 공개하지 않는 기존 배포 경계를 유지해야 한다.

## 시연 절차

1. 로그인 후 ‘모의 주문 실행’에서 자금 10000으로 새 실행 생성.
2. 수량 10으로 매수 주문. 예약 현금 1000.50000000 확인.
3. 거래소 접수 → 응답 유실. 내부 UNKNOWN, 거래소 ACCEPTED, 예약금 유지 확인.
4. 부분 체결 수량 4 → 잔량 취소. 내부 잔고는 아직 반영되지 않음.
5. 역순 수신·대사. 현금 9599.80000000, SPY 4, 예약금 0, CANCELLED 확인.
6. 중복 수신·대사 후 잔고 동일 확인.
7. DB 기록으로 리플레이 검증 → 전체 상태 일치 확인.
8. 다른 실행에서 전략 주문 생성, 전량 체결·대사, 전량 매도·대사 후 재진입 차단 확인.

## 자동 검증

2026-10-19 실행 결과: Python 37개(새 실행 엔진·라우터 25개, 기존 ETF 워크플로 12개) 통과. Spring 실행 서비스 5개·컨트롤러 6개 통과. TypeScript 검사와 Next.js 프로덕션 빌드 통과(`/execution` 포함). 변경한 TSX 파일의 ESLint와 `git diff --check` 통과. 최초 프론트 빌드는 파일 생성 EPERM으로 실패했고 권한 제한을 해제한 동일 빌드에서 성공했다.

- `collector/tests/test_execution.py`: 계산·리스크·상태 전이·중복·역순·현금/수량 보존·수수료 이후 비중·재진입 검증.
- `collector/tests/test_execution_api.py`: FastAPI 라우터 함수 응답, 입력 오류 422, 명령 수 제한. HTTP 전송 테스트와 구분한다.
- `ExecutionServiceTest`: mock 계산기 대신 별도 Python 프로세스의 실제 reducer를 HTTP로 호출한다. 디스크 H2(MySQL 모드)를 사용하며 동시 요청 4개, 요청 충돌, 롤백, 계산 서버 장애, 사용자 격리, Python 강제 종료·재시작 후 복구를 검증한다.
- `ExecutionControllerTest`: 기존 Spring Security 설정으로 익명 접근 차단과 입력 검증을 확인한다.

재현 명령(저장소 루트 기준, 각 작업 폴더에서 실행):

```text
# collector
rtk proxy .\.venv\Scripts\python.exe -m pytest tests/test_execution.py tests/test_execution_api.py tests/test_etf_workflow.py -q
# marketboardBackend (PATH에 Python 3.12+ 필요)
rtk proxy .\gradlew.bat test --tests '*Execution*Test' --offline --console=plain
# frontend
rtk proxy npx.cmd tsc --noEmit
rtk proxy npx.cmd eslint "src/app/(app)/execution/page.tsx" "src/components/app-shell/AppNavigation.tsx"
rtk proxy npm.cmd run build
```

## 검증의 한계

H2 검증은 MySQL 8의 잠금·격리·CHECK 동작 검증을 대체하지 않는다. Python 프로세스 재시작을 검증했지만 OS/JVM 강제 종료 중 MySQL 커밋 복구는 검증하지 않았다. 저장 중 실패는 트랜잭션 롤백으로 재현했다. 별도 Python HTTP fixture는 production reducer를 사용하되 FastAPI 서버 전체를 구동하지 않는다.

실제 브라우저 조작, 운영 배포, 실시간 시세, 실제 증권사·거래소, 부하 성능은 이번 자동 테스트의 검증 대상이 아니다. Docker 엔진이 실행 중이지 않아 MySQL 컨테이너 검증은 수행하지 않았다. 테스트가 통과했다는 이유로 실거래 안정성이나 프로덕션 운용 경험을 주장하지 않는다.
