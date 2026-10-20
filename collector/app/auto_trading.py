"""Offline daily-bar replay. Signals only use closes before the execution bar."""
from decimal import Decimal, ROUND_DOWN
from datetime import date
from app.execution import decimal, money, encode

VERSION = "sma-paper-v1"


def replay_auto(configuration, bars, warmup=None):
    if configuration.get("version") != VERSION:
        raise ValueError("지원하지 않는 자동 전략 버전")
    window = configuration["window"]
    if isinstance(window, bool) or not isinstance(window, int) or not 2 <= window <= 200:
        raise ValueError("이동평균 기간은 2~200입니다.")
    capital = decimal(configuration["initialCapital"])
    fee = decimal(configuration["feeBps"]) / 10000
    slip = decimal(configuration["slippageBps"]) / 10000
    weight = decimal(configuration["maxPositionWeight"])
    limit = decimal(configuration["maxOrderValue"])
    if not 1 <= capital <= 100000000 or not 0 <= fee <= Decimal(".01") or not 0 <= slip <= Decimal(".01"):
        raise ValueError("자금 또는 거래비용 범위 오류")
    if not 0 < weight <= 1 or not 0 < limit <= capital or len(bars) > 1000:
        raise ValueError("비중, 주문한도 또는 가격 개수 오류")
    warmup = warmup or []
    if warmup and len(warmup) != window:
        raise ValueError("준비 가격은 이동평균 기간과 같아야 합니다.")
    if len(warmup) + len(bars) > 1000:
        raise ValueError("준비 가격 포함 최대 1,000개입니다.")
    warmup_closes, warmup_date = [], None
    for bar in warmup:
        day = date.fromisoformat(bar["date"])
        opening, close = decimal(bar["open"]), decimal(bar["close"])
        if (warmup_date is not None and day <= warmup_date) or not 0 < opening <= 1000000 or not 0 < close <= 1000000:
            raise ValueError("준비 가격 날짜 또는 값 오류")
        warmup_closes.append(close)
        warmup_date = day
    cash, shares, basis, realized, fees = capital, Decimal(0), Decimal(0), Decimal(0), Decimal(0)
    peak, max_drawdown = capital, Decimal(0)
    closes, previous_date, entries = warmup_closes, warmup_date, []
    state = dict(cash=capital, shares=shares, costBasis=basis, realizedPnl=realized,
                 unrealizedPnl=Decimal(0), fees=fees, equity=capital, returnPct=Decimal(0), maxDrawdownPct=Decimal(0))
    for index, bar in enumerate(bars):
        day = date.fromisoformat(bar["date"])
        opening, close = decimal(bar["open"]), decimal(bar["close"])
        if previous_date is not None and day <= previous_date:
            raise ValueError("가격은 거래일당 한 개이며 날짜 오름차순이어야 합니다.")
        if not 0 < opening <= 1000000 or not 0 < close <= 1000000:
            raise ValueError("잘못된 가격")
        side, reason, quantity, fill_price, paid_fee = "HOLD", "이동평균 준비 기간", Decimal(0), None, Decimal(0)
        average = sum(closes[-window:]) / window if len(closes) >= window else None
        signal_close = closes[-1] if closes else None
        if average is not None:
            if signal_close > average and shares == 0:
                fill_price = money(opening * (1 + slip))
                budget = min(limit, cash / (1 + fee), cash * weight / (1 + weight * fee))
                quantity = (budget / fill_price).quantize(Decimal(".0001"), rounding=ROUND_DOWN)
                if quantity > 0:
                    side, reason = "BUY", "전일 종가가 이동평균 위: 현금에서 매수"
                else:
                    reason = "주문 한도 내 최소 수량 미달"
            elif signal_close < average and shares > 0:
                fill_price = money(opening * (1 - slip))
                quantity = min(shares, (limit / fill_price).quantize(Decimal(".0001"), rounding=ROUND_DOWN))
                if quantity > 0:
                    side, reason = "SELL", "전일 종가가 이동평균 아래: 보유 수량 매도"
                else:
                    reason = "주문 한도 내 최소 수량 미달"
            else:
                reason = "추세 유지 또는 신호 없음: 보유 상태 유지"
        if side != "HOLD":
            notional = money(quantity * fill_price)
            paid_fee = money(notional * fee)
            if side == "BUY":
                cash -= notional + paid_fee
                shares += quantity
                basis += notional + paid_fee
            else:
                removed = basis if quantity == shares else money(basis * quantity / shares)
                cash += notional - paid_fee
                realized += notional - paid_fee - removed
                basis -= removed
                shares -= quantity
            fees += paid_fee
        else:
            fill_price = None
        equity = money(cash + shares * close)
        unrealized = money(shares * close) - basis
        peak = max(peak, equity)
        max_drawdown = max(max_drawdown, (peak - equity) / peak * 100)
        if cash < 0 or shares < 0 or equity != money(capital + realized + unrealized):
            raise ValueError("모의 원장 보존 위반")
        state = dict(cash=cash, shares=shares, costBasis=basis, realizedPnl=realized,
                     unrealizedPnl=unrealized, fees=fees, equity=equity,
                     returnPct=money((equity / capital - 1) * 100), maxDrawdownPct=money(max_drawdown))
        entries.append(dict(step=index + 1, date=bar["date"], signalDate=previous_date.isoformat() if previous_date else None,
                            signalClose=signal_close, movingAverage=money(average) if average is not None else None,
                            open=opening, close=close, action=side, reason=reason, quantity=quantity,
                            fillPrice=fill_price, fee=paid_fee, state=state))
        closes.append(close)
        previous_date = day
    return encode(dict(state=state, entries=entries))


def compare_auto(configuration, bars, warmup=None):
    """Compare only the processed snapshot, with the same warmup and entry limits."""
    strategy = replay_auto(configuration, bars, warmup)
    entry_index = 0 if warmup else configuration["window"]
    capital = decimal(configuration["initialCapital"])
    cash, shares, fees = capital, Decimal(0), Decimal(0)
    peak, drawdown = capital, Decimal(0)
    fee = decimal(configuration["feeBps"]) / 10000
    slip = decimal(configuration["slippageBps"]) / 10000
    weight = decimal(configuration["maxPositionWeight"])
    trades, curve = 0, []
    equity = capital
    for index, bar in enumerate(bars):
        if index == entry_index:
            fill = money(decimal(bar["open"]) * (1 + slip))
            budget = min(decimal(configuration["maxOrderValue"]), cash / (1 + fee),
                         cash * weight / (1 + weight * fee))
            shares = (budget / fill).quantize(Decimal(".0001"), rounding=ROUND_DOWN)
            notional = money(shares * fill)
            fees = money(notional * fee)
            cash -= notional + fees
            trades = int(shares > 0)
        equity = money(cash + shares * decimal(bar["close"]))
        peak = max(peak, equity)
        drawdown = max(drawdown, (peak - equity) / peak * 100)
        curve.append(dict(date=bar["date"], strategy=strategy["entries"][index]["state"]["equity"],
                          benchmark=equity))
    state = strategy["state"]
    return encode(dict(
        version="sma-comparison-v1", steps=len(bars),
        entryDate=bars[entry_index]["date"] if len(bars) > entry_index else None,
        asOf=bars[-1]["date"] if bars else None,
        priceQuality=dict(adjustment="UNKNOWN", dividends="UNKNOWN", missingSessions="NOT_CHECKED",
                          source="FROZEN_PRICE_HISTORY"),
        strategy=dict(**state, trades=sum(e["action"] != "HOLD" for e in strategy["entries"])),
        benchmark=dict(equity=equity, cash=cash, shares=shares, fees=fees, trades=trades,
                       returnPct=money((equity / capital - 1) * 100), maxDrawdownPct=money(drawdown)),
        excessReturnPct=money(decimal(state["returnPct"]) - (equity / capital - 1) * 100),
        curve=curve))
