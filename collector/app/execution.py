"""Deterministic SPY execution sandbox. No network calls or real orders.

Spring serializes and persists commands; this reducer is the sole calculator.
Broker events are durable in the command journal and may be delivered later.
"""
from decimal import Decimal, ROUND_HALF_UP, ROUND_DOWN
from hashlib import sha256
import json

VERSION = "execution-v1"
TERMINAL = {"FILLED", "CANCELLED", "REJECTED"}
OPERATIONS = {"BUY", "SELL", "STRATEGY_BUY", "STALE_BUY", "DISPATCH", "TIMEOUT", "PARTIAL_FILL",
              "FILL", "CANCEL", "REJECT", "RECONCILE", "REVERSE", "DUPLICATE", "STOP"}
Q = Decimal("0.00000001")


def decimal(value):
    if not isinstance(value, (str, int)) or isinstance(value, bool):
        raise ValueError("금액과 수량은 십진수 문자열이어야 합니다.")
    try:
        result = Decimal(value)
    except Exception as exc:
        raise ValueError("잘못된 십진수") from exc
    if not result.is_finite() or abs(result) > Decimal("1000000000") or result.as_tuple().exponent < -8:
        raise ValueError("금액·수량 범위 또는 소수점 자릿수 초과")
    return result


def money(value):
    return value.quantize(Q, rounding=ROUND_HALF_UP)


def encode(value):
    if isinstance(value, Decimal):
        return format(value, ".8f")
    if isinstance(value, dict):
        return {k: encode(v) for k, v in value.items()}
    if isinstance(value, list):
        return [encode(v) for v in value]
    return value


def replay(configuration, commands):
    if configuration.get("version") != VERSION:
        raise ValueError("지원하지 않는 실행 버전")
    capital = decimal(configuration["initialCapital"])
    fee_bps = decimal(configuration["feeBps"])
    max_order = decimal(configuration["maxOrderValue"])
    weight = decimal(configuration["maxPositionWeight"])
    if not 1 <= capital <= 1_000_000_000 or not 0 <= fee_bps <= 100 or not 0 < max_order <= capital or not 0 < weight <= 1:
        raise ValueError("잘못된 실행 설정")
    if len(commands) > 500:
        raise ValueError("실행당 최대 500개 명령입니다. 새 실행을 만드세요.")
    price = Decimal("100")  # Frozen synthetic quote, never a live SPY price.
    state = dict(cash=capital, reservedCash=Decimal(0), shares=Decimal(0), reservedShares=Decimal(0),
                 costBasis=Decimal(0), realizedPnl=Decimal(0), fees=Decimal(0), stopped=False,
                 reentryBlocked=False, orders={}, broker={}, pending={}, applied=[], log=[])
    seen = {}

    def log(kind, order, reason):
        state["log"].append(dict(kind=kind, orderId=order, reason=reason))

    def reserve(order):
        remaining = order["quantity"] - order["filled"]
        return money(remaining * price * (1 + fee_bps / 10000))

    def deliver(event):
        order = state["orders"][event["orderId"]]
        key = event["id"]
        if key in state["applied"]:
            return
        state["pending"][key] = event
        while True:
            next_key = f'{order["id"]}:{order["sequence"] + 1}'
            current = state["pending"].pop(next_key, None)
            if current is None:
                break
            kind = current["kind"]
            if order["status"] in TERMINAL:
                raise ValueError("종료 주문에 후속 이벤트가 있습니다.")
            if kind == "FILL":
                quantity = current["quantity"]
                notional = money(quantity * price)
                fee = money(notional * fee_bps / 10000)
                if quantity <= 0 or order["filled"] + quantity > order["quantity"]:
                    raise ValueError("잘못된 체결 수량")
                old_reserve = reserve(order)
                order["filled"] += quantity
                if order["side"] == "BUY":
                    state["cash"] -= notional + fee
                    state["reservedCash"] += reserve(order) - old_reserve
                    state["shares"] += quantity
                    state["costBasis"] += notional + fee
                else:
                    removed_cost = state["costBasis"] if quantity == state["shares"] else money(state["costBasis"] * quantity / state["shares"])
                    state["cash"] += notional - fee
                    state["shares"] -= quantity
                    state["reservedShares"] -= quantity
                    state["costBasis"] -= removed_cost
                    state["realizedPnl"] += notional - fee - removed_cost
                    if state["shares"] == 0:
                        state["reentryBlocked"] = True
                state["fees"] += fee
                order["status"] = "FILLED" if order["filled"] == order["quantity"] else "PARTIALLY_FILLED"
            elif kind in {"CANCELLED", "REJECTED"}:
                if order["side"] == "BUY":
                    state["reservedCash"] -= reserve(order)
                else:
                    state["reservedShares"] -= order["quantity"] - order["filled"]
                order["status"] = kind
            elif kind == "ACK":
                order["status"] = "ACCEPTED"
            else:
                raise ValueError("알 수 없는 거래소 이벤트")
            order["sequence"] += 1
            state["applied"].append(next_key)
            log(kind, order["id"], "모의 거래소 이벤트 반영")

    for command in commands:
        key, operation = command.get("key"), command.get("operation")
        if not isinstance(key, str) or not 1 <= len(key) <= 64 or operation not in OPERATIONS:
            raise ValueError("잘못된 실행 명령")
        if key in seen:
            if seen[key] != command:
                raise ValueError("같은 명령 키에 다른 요청을 사용할 수 없습니다.")
            continue
        seen[key] = command
        if operation == "STOP":
            state["stopped"] = True
            log("STOP", None, "신규 주문 차단; 기존 주문은 별도 취소·대사 필요")
            continue
        if operation in {"BUY", "SELL", "STRATEGY_BUY", "STALE_BUY"}:
            if operation == "STRATEGY_BUY":
                nav = state["cash"] + state["shares"] * price
                target = (nav * weight - state["shares"] * price) / (1 + weight * fee_bps / 10000)
                budget = min(max_order, target, (state["cash"] - state["reservedCash"]) / (1 + fee_bps / 10000))
                quantity = (max(Decimal(0), budget) / price).quantize(Decimal("0.0001"), rounding=ROUND_DOWN)
                if quantity == 0:
                    log("BLOCKED", key, "SPY 보유 목표에 도달했거나 주문 가능 금액 없음")
                    continue
            else:
                quantity = decimal(command.get("quantity", "0"))
            if quantity <= 0 or quantity % Decimal("0.0001"):
                raise ValueError("주문 수량은 양수이고 0.0001 단위여야 합니다.")
            side = "SELL" if operation == "SELL" else "BUY"
            required = money(quantity * price * (1 + fee_bps / 10000))
            reason = None
            if state["stopped"]:
                reason = "실행 중지"
            elif operation == "STALE_BUY":
                reason = "가격 신선도 기준 초과(모의 120초, 허용 60초)"
            elif any(o["status"] not in TERMINAL for o in state["orders"].values()):
                reason = "SPY 미확정 주문을 먼저 대사·종료하세요."
            elif side == "BUY" and state["reentryBlocked"]:
                reason = "전량 청산 후 이 실행에서는 재진입 금지"
            elif quantity * price > max_order:
                reason = "최대 주문 금액 초과"
            elif side == "BUY" and required > state["cash"] - state["reservedCash"]:
                reason = "주문 가능 현금 부족"
            elif side == "BUY" and (state["shares"] + quantity) * price > (state["cash"] + state["shares"] * price - money(quantity * price * fee_bps / 10000)) * weight:
                reason = "최대 종목 비중 초과"
            elif side == "SELL" and quantity > state["shares"] - state["reservedShares"]:
                reason = "주문 가능 수량 부족"
            if reason:
                log("BLOCKED", key, reason)
                continue
            state["orders"][key] = dict(id=key, side=side, quantity=quantity, filled=Decimal(0),
                                        status="SUBMITTING", sequence=0, referencePrice=price)
            if side == "BUY":
                state["reservedCash"] += required
            else:
                state["reservedShares"] += quantity
            log("INTENT", key, "주문 의도 및 자금 예약; 거래소 제출 전")
            continue
        order_id = command.get("orderId")
        if order_id not in state["orders"]:
            raise ValueError("주문을 선택하세요.")
        order = state["orders"][order_id]
        broker = state["broker"].get(order_id)
        if operation == "DISPATCH":
            if broker is None:
                broker = dict(status="ACCEPTED", filled=Decimal(0), events=[])
                state["broker"][order_id] = broker
                broker["events"].append(dict(id=f"{order_id}:1", orderId=order_id, kind="ACK"))
                log("DISPATCH", order_id, "거래소 접수 완료, 응답 전달은 대사 단계에서 수행")
        elif operation == "TIMEOUT":
            if order["status"] not in TERMINAL:
                order["status"] = "UNKNOWN"
                log("UNKNOWN", order_id, "응답 불명; 예약 유지, 재주문 금지")
        elif operation in {"PARTIAL_FILL", "FILL", "CANCEL", "REJECT"}:
            if broker is None or broker["status"] in TERMINAL:
                raise ValueError("접수된 미종료 거래소 주문이 필요합니다.")
            if operation == "REJECT" and broker["filled"]:
                raise ValueError("체결된 주문은 거절할 수 없습니다. 취소를 사용하세요.")
            event = dict(id=f'{order_id}:{len(broker["events"]) + 1}', orderId=order_id)
            if operation in {"PARTIAL_FILL", "FILL"}:
                remaining = order["quantity"] - broker["filled"]
                quantity = remaining if operation == "FILL" else decimal(command.get("quantity", "0"))
                if not 0 < quantity <= remaining or quantity % Decimal("0.0001"):
                    raise ValueError("체결 수량은 미체결 수량 이하의 양수이며 0.0001 단위여야 합니다.")
                event.update(kind="FILL", quantity=quantity)
                broker["filled"] += quantity
                broker["status"] = "FILLED" if broker["filled"] == order["quantity"] else "PARTIALLY_FILLED"
            else:
                broker["status"] = "CANCELLED" if operation == "CANCEL" else "REJECTED"
                event["kind"] = broker["status"]
            broker["events"].append(event)
            log("BROKER", order_id, f'{event["kind"]}: 이벤트 저장, 아직 계좌 미반영')
        elif operation in {"RECONCILE", "REVERSE", "DUPLICATE"}:
            if broker is None:
                log("NOT_FOUND", order_id, "거래소에 주문 없음; 동일 주문 ID로 제출 가능")
            else:
                events = broker["events"]
                delivery = list(reversed(events)) if operation == "REVERSE" else events * (2 if operation == "DUPLICATE" else 1)
                for event in delivery:
                    deliver(event)
                # Repeated ACKs need not be applied to resolve a timeout after an earlier ACK.
                order["status"] = broker["status"]
                log("RECONCILED", order_id, "거래소 이벤트와 계좌 상태 대사 완료")

    if state["cash"] < 0 or state["reservedCash"] < 0 or state["cash"] < state["reservedCash"] or not 0 <= state["reservedShares"] <= state["shares"]:
        raise ValueError("현금·수량 보존 위반")
    state["availableCash"] = state["cash"] - state["reservedCash"]
    state["averageCost"] = money(state["costBasis"] / state["shares"]) if state["shares"] else Decimal(0)
    state["equity"] = state["cash"] + state["shares"] * price
    state["unrealizedPnl"] = state["shares"] * price - state["costBasis"]
    if state["equity"] != capital + state["realizedPnl"] + state["unrealizedPnl"]:
        raise ValueError("원장 등식 위반")
    state = encode(state)
    economic = {k: state[k] for k in ("cash", "reservedCash", "shares", "reservedShares", "costBasis", "realizedPnl", "fees", "orders", "stopped", "reentryBlocked")}
    state["economicHash"] = sha256(json.dumps(economic, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return state
