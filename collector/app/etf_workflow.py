"""Frozen research evidence, prospective paper trading, and read-only rebalance proposals."""

from datetime import date, datetime, timedelta, timezone
from math import fsum, isfinite

from .backtest import InsufficientDataError
from .etf_backtest import STRATEGIES, _rebalance, compare_etf_strategies, validate_request
from .etf_momentum import ETF_UNIVERSE, NEW_YORK, _start_date, load_daily_bars, run_etf_momentum, target_weights

VERSION = "etf-momentum-v1"


def capture_research(configuration: dict, strategy_id: str):
    if strategy_id not in dict(STRATEGIES):
        raise ValueError("지원하지 않는 ETF 전략입니다.")
    start, end = configuration["startDate"], configuration["endDate"]
    validate_request(start, end, configuration["initialCapital"], configuration["feeBps"], configuration["slippageBps"])
    first_signal = start.replace(day=1) - timedelta(days=1)
    bars = load_daily_bars(_start_date(first_signal) - timedelta(days=7), end)
    try:
        comparison = compare_etf_strategies(bars, start, end, configuration["initialCapital"],
                                          configuration["feeBps"], configuration["slippageBps"])
    except InsufficientDataError as exc:
        return dict(calculationVersion=VERSION, strategyId=strategy_id, configuration=configuration,
                    comparison=dict(ready=False, message=str(exc), strategies=[]), inputBars={})
    # Prices are frozen with the result, so later provider adjustments cannot rewrite the evidence.
    source = {ticker: [dict(ts=ts.replace(tzinfo=timezone.utc).isoformat() if ts.tzinfo is None else ts.isoformat(),
                          open=float(opening), close=float(close)) for ts, opening, close in rows]
              for ticker, rows in bars.items()}
    return dict(calculationVersion=VERSION, strategyId=strategy_id, configuration=configuration,
                comparison=comparison, inputBars=source)


def _daily_prices(bars, tickers, end):
    prices = {}
    for ticker in tickers:
        prices[ticker] = {}
        for ts, opening, close in bars.get(ticker, []):
            instant = ts.replace(tzinfo=timezone.utc) if ts.tzinfo is None else ts
            day = instant.astimezone(NEW_YORK).date()
            if day > end:
                continue
            if day in prices[ticker] or not all(p is not None and isfinite(float(p)) and float(p) > 0 for p in (opening, close)):
                raise InsufficientDataError(f"{ticker}: 중복 또는 비정상 가격")
            prices[ticker][day] = (float(opening), float(close))
    return prices


def advance_paper(payload: dict, bars: dict, today: date):
    if payload["calculationVersion"] != VERSION:
        raise ValueError("지원하지 않는 모의 운용 계산 버전입니다.")
    state = payload["state"]
    last_date = date.fromisoformat(state["date"])
    if last_date > today or payload["strategyId"] not in dict(STRATEGIES):
        raise ValueError("비정상 모의 운용 상태입니다.")
    amounts = [state["cash"], *state["shares"].values()]
    if any(not isfinite(float(v)) or float(v) < 0 for v in amounts) or set(state["shares"]) - set(ETF_UNIVERSE):
        raise ValueError("모의 운용 현금과 수량은 유한한 음이 아닌 값이어야 합니다.")
    if not isfinite(payload["initialCapital"]) or payload["initialCapital"] <= 0:
        raise ValueError("초기 자본이 올바르지 않습니다.")
    for key in ("feeBps", "slippageBps"):
        if not isfinite(payload[key]) or not 0 <= payload[key] <= 100:
            raise ValueError("거래 비용이 올바르지 않습니다.")
    prices = _daily_prices(bars, ETF_UNIVERSE, today - timedelta(days=1))
    dates = sorted(prices["SPY"])
    if any(set(p) != set(dates) for p in prices.values()):
        raise InsufficientDataError("모의 운용 일봉의 거래일이 일치하지 않습니다.")
    dates = [d for d in dates if d > last_date]
    if not dates:
        return dict(events=[], state=state, message="새로 완료된 일봉을 기다리고 있습니다.")
    if any((right - left).days > 7 for left, right in zip([last_date, *dates], dates)):
        raise InsufficientDataError("모의 운용 일봉에 7일 초과 공백이 있습니다.")
    shares = {t: float(state["shares"].get(t, 0)) for t in ETF_UNIVERSE}
    cash = float(state["cash"])
    signal_id = state.get("signalId")
    events = []
    signals = sorted(payload["signals"], key=lambda s: s["capturedAt"])
    for day in dates:
        eligible = [s for s in signals if datetime.fromisoformat(s["capturedAt"].replace("Z", "+00:00")).astimezone(NEW_YORK).date() < day]
        signal = eligible[-1] if eligible else None
        trades = []
        if signal is not None and signal["id"] != signal_id and (payload["strategyId"] != "SPY_HOLD" or signal_id is None):
            weights = target_weights(signal["signal"], payload["strategyId"])
            cash, trades, _ = _rebalance(shares, cash, {t: prices[t][day][0] for t in ETF_UNIVERSE}, weights,
                                          payload["feeBps"], payload["slippageBps"])
            signal_id = signal["id"]
        value = cash + fsum(shares[t] * prices[t][day][1] for t in ETF_UNIVERSE)
        state = dict(date=day.isoformat(), shares=dict(shares), cash=cash, signalId=signal_id,
                     value=value, returnPct=(value / payload["initialCapital"] - 1) * 100)
        if not isfinite(value) or value <= 0:
            raise InsufficientDataError("모의 운용 평가 오류")
        events.append(dict(state=state, trades=trades,
                           prices={t: dict(open=prices[t][day][0], close=prices[t][day][1]) for t in ETF_UNIVERSE}))
    return dict(events=events, state=state, message=None)


def run_paper(payload):
    today = datetime.now(NEW_YORK).date()
    start = date.fromisoformat(payload["state"]["date"]) + timedelta(days=1)
    if start >= today:
        return dict(events=[], state=payload["state"], message="시작 이후 완료된 일봉을 기다리고 있습니다.")
    if (today - start).days > 3660:
        raise ValueError("모의 운용 갱신 범위는 최대 10년입니다.")
    return advance_paper(payload, load_daily_bars(start, today - timedelta(days=1)), today)


def rebalance_proposal(payload, signal, bars, today):
    weights = target_weights(signal, payload["strategyId"])
    if payload.get("maxPositionWeight") is not None and any(w > payload["maxPositionWeight"] for w in weights.values()):
        raise InsufficientDataError("전략 목표가 포트폴리오 최대 비중 규칙을 초과합니다. 규칙을 검토해 주세요.")
    holdings = {h["ticker"]: h for h in payload["holdings"]}
    if len(holdings) != len(payload["holdings"]):
        raise ValueError("중복 보유 종목")
    tickers = list(dict.fromkeys([*ETF_UNIVERSE, *holdings]))
    prices = _daily_prices(bars, tickers, today - timedelta(days=1))
    latest = {t: max(p) for t, p in prices.items() if p}
    if len(latest) != len(tickers) or len(set(latest.values())) != 1 or (today - next(iter(latest.values()))).days > 4:
        raise InsufficientDataError("모든 대상 종목에 동일한 최근 완료 일봉이 필요합니다.")
    day = next(iter(latest.values()))
    quotes = {t: prices[t][day][1] for t in tickers}
    current = {t: float(holdings[t]["quantity"]) if t in holdings else 0.0 for t in tickers}
    nav = payload["cashUsd"] + fsum(current[t] * quotes[t] for t in tickers)
    if nav <= 0:
        raise ValueError("보유 평가액과 입력 현금 합계가 0입니다.")
    proposed = dict(current)
    cash, trades, _ = _rebalance(proposed, payload["cashUsd"], quotes, weights, payload["feeBps"], payload["slippageBps"])
    return dict(ready=True, message=None, signalDate=signal["asOf"], priceDate=day.isoformat(), currency="USD",
                cashInput=payload["cashUsd"], totalValue=nav, proposedCash=cash,
                totalFees=fsum(t["fee"] for t in trades), totalSlippage=fsum(t["slippage"] for t in trades),
                rows=[dict(ticker=t, quantity=current[t], version=holdings[t]["version"] if t in holdings else None,
                           price=quotes[t], currentWeight=current[t] * quotes[t] / nav, targetWeight=weights.get(t, 0),
                           targetValue=proposed[t] * quotes[t], adjustmentValue=(proposed[t] - current[t]) * quotes[t],
                           adjustmentQuantity=proposed[t] - current[t]) for t in tickers])


def run_rebalance_proposal(payload):
    today = datetime.now(NEW_YORK).date()
    tickers = list(dict.fromkeys([*ETF_UNIVERSE, *(h["ticker"] for h in payload["holdings"])]))
    signal = run_etf_momentum()
    if not signal["ready"]:
        raise InsufficientDataError("월말 ETF 신호 데이터가 부족합니다.")
    return rebalance_proposal(payload, signal, load_daily_bars(today - timedelta(days=7), today - timedelta(days=1), tickers), today)
