# 포트폴리오 기초잔고 이관 런북

이 절차는 기존 `portfolio_positions`를 `OPENING_BALANCE` 원장으로 옮길 때 사용한다. 원장 거래 API를 공개하기 전에 한 번 실행하며, `portfolio_positions` 자체는 변경하지 않는다.

## 실행 전

1. `PORTFOLIO_LEDGER_WRITES_ENABLED=false`와 `PORTFOLIO_LEDGER_OPENING_BACKFILL_ENABLED=false`를 유지한다.
2. MySQL 백업을 생성하고 복원 가능한지 확인한다.
3. 다음 검증값을 작업 기록에 저장한다.

```sql
SELECT COUNT(*) AS position_count,
       COALESCE(SUM(quantity), 0) AS total_quantity,
       COALESCE(SUM(quantity * avg_cost), 0) AS total_cost
FROM portfolio_positions;
```

## 실행

1. `PORTFOLIO_LEDGER_OPENING_BACKFILL_ENABLED=true`로 백엔드를 한 번 시작한다.
2. `Portfolio opening-balance backfill completed` 로그의 `total`, `inserted`, `quantity`, `cost`를 작업 기록에 저장한다.
3. 백엔드가 healthy인지 확인한다. 변환 또는 검증이 실패하면 시작이 실패하고 같은 DB 트랜잭션의 신규 기초잔고는 모두 롤백된다.
4. 다음 쿼리 결과가 실행 전 검증값과 같은지 확인한다.

```sql
SELECT COUNT(*) AS opening_count,
       COALESCE(SUM(quantity), 0) AS total_quantity,
       COALESCE(SUM(quantity * unit_price), 0) AS total_cost
FROM portfolio_transactions
WHERE transaction_type = 'OPENING_BALANCE';
```

5. 같은 설정으로 한 번 더 시작해 `inserted=0`인지 확인한다.
6. `PORTFOLIO_LEDGER_OPENING_BACKFILL_ENABLED=false`로 되돌린다. PR 3 완료 전까지 `PORTFOLIO_LEDGER_WRITES_ENABLED=false`를 유지한다.

## 실패와 복구

- 검증 실패 시 원장 쓰기를 활성화하지 않는다. 오류를 수정한 뒤 같은 배치를 재실행한다.
- 아직 신규 원장 거래가 없고 기초잔고만 존재한다면 백업 복원 또는 검증된 기초잔고 행 제거 후 재실행할 수 있다.
- 신규 거래가 기록된 뒤에는 DB 전체를 과거로 복원해 거래를 유실하지 않는다. 새 거래를 차단하고 별도 정정·재투영 절차를 수립한다.
