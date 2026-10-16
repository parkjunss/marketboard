"""Cost-aware, next-session-open comparison using the shared monthly ETF signal."""

from datetime import date, datetime, time, timedelta, timezone
from math import fsum, isfinite, prod

import pandas as pd

from .backtest import InsufficientDataError, performance_metrics
from .etf_momentum import ETF_UNIVERSE, NEW_YORK, _start_date, build_etf_momentum, load_daily_bars, target_weights

STRATEGIES = (("SPY_HOLD", "SPY 단순 보유"), ("MOMENTUM", "모멘텀 상위 3개"),
              ("MOMENTUM_TREND", "모멘텀 + 200일선"))


def validate_request(start: date, end: date, capital: float, fee_bps: float, slippage_bps: float):
    if start.year < 1991 or start >= end or (end - start).days > 3660:
        raise ValueError("기간은 1991년 이후, 시작일 < 종료일, 최대 10년이어야 합니다.")
    if end >= datetime.now(NEW_YORK).date():
        raise ValueError("종료일은 뉴욕 기준 오늘 이전이어야 합니다.")
    if not isfinite(capital) or not 1 <= capital <= 1_000_000_000:
        raise ValueError("초기 자본은 1~1,000,000,000 USD여야 합니다.")
    if any(not isfinite(value) or not 0 <= value <= 100 for value in (fee_bps, slippage_bps)):
        raise ValueError("수수료와 슬리피지는 각각 0~100bp여야 합니다.")


def _prepare(bars: dict, start: date, end: date):
    prices = {}
    for ticker in ETF_UNIVERSE:
        data = {}
        for timestamp, opening, close in bars.get(ticker, []):
            if not isinstance(timestamp, datetime):
                raise InsufficientDataError(f"{ticker}: 잘못된 일봉 날짜")
            instant = timestamp.replace(tzinfo=timezone.utc) if timestamp.tzinfo is None else timestamp
            day = instant.astimezone(NEW_YORK).date()
            if day > end:
                continue
            try:
                opening, close = float(opening), float(close)
            except (TypeError, ValueError, OverflowError) as exc:
                raise InsufficientDataError(f"{ticker}: 잘못된 일봉 가격") from exc
            if not all(isfinite(p) and p > 0 for p in (opening, close)) or day in data:
                raise InsufficientDataError(f"{ticker}: 비정상 가격 또는 중복 일봉")
            data[day] = (opening, close)
        if not data:
            raise InsufficientDataError(f"{ticker}: 일봉 데이터가 없습니다.")
        prices[ticker] = data
    dates = sorted(prices["SPY"])
    if any(set(data) != set(dates) for data in prices.values()):
        raise InsufficientDataError("6개 ETF의 거래일이 일치하지 않습니다. 누락 데이터를 복구해 주세요.")
    if any((right - left).days > 7 for left, right in zip(dates, dates[1:])):
        raise InsufficientDataError("일봉 이력에 7일 초과 공백이 있습니다.")
    trading_dates = [day for day in dates if start <= day <= end]
    if len(trading_dates) < 2 or (trading_dates[0] - start).days > 7 or (end - trading_dates[-1]).days > 7:
        raise InsufficientDataError("요청 기간의 시작/종료 일봉이 부족합니다.")
    return prices, dates, trading_dates


def _rebalance(shares, cash, openings, weights, fee_bps, slippage_bps):
    tickers = list(openings)
    old_values = {ticker: shares.get(ticker, 0) * openings[ticker] for ticker in tickers}
    nav = cash + fsum(old_values.values())
    rate = (fee_bps + slippage_bps) / 10_000
    # Solve post-cost NAV so fully invested targets never borrow cash to pay costs.
    low, high = 0.0, nav
    for _ in range(60):
        target_nav = (low + high) / 2
        cost = fsum(abs(weights.get(t, 0) * target_nav - old_values[t]) for t in tickers) * rate
        if target_nav + cost > nav:
            high = target_nav
        else:
            low = target_nav
    target_nav = low
    trades = []
    for ticker in tickers:
        target = weights.get(ticker, 0) * target_nav
        delta = target - old_values[ticker]
        if abs(delta) > nav * 1e-12:
            notional = abs(delta)
            trades.append(dict(ticker=ticker, side="BUY" if delta > 0 else "SELL",
                               quantity=notional / openings[ticker], price=openings[ticker], notional=notional,
                               fee=notional * fee_bps / 10_000, slippage=notional * slippage_bps / 10_000))
        shares[ticker] = target / openings[ticker]
    cost = fsum(trade["fee"] + trade["slippage"] for trade in trades)
    cash = max(0.0, nav - fsum(shares[t] * openings[t] for t in tickers) - cost)
    return cash, trades, fsum(trade["notional"] for trade in trades) / nav


def compare_etf_strategies(bars: dict, start: date, end: date, initial_capital: float,
                           fee_bps: float, slippage_bps: float) -> dict:
    validate_request(start, end, initial_capital, fee_bps, slippage_bps)
    prices, dates, trading_dates = _prepare(bars, start, end)
    signals = {}
    regimes = {}
    previous_signal_date = None
    for day in trading_dates:
        signal_date = day.replace(day=1) - timedelta(days=1)
        if signal_date != previous_signal_date:
            warmup = _start_date(signal_date) - timedelta(days=7)
            histories = {ticker: [(datetime.combine(d, time.min, NEW_YORK), prices[ticker][d][1])
                                  for d in dates if warmup <= d <= signal_date] for ticker in ETF_UNIVERSE}
            signal = build_etf_momentum(histories, signal_date)
            if not signal["ready"]:
                reasons = "; ".join(f'{row["ticker"]}: {row["reason"]}' for row in signal["rows"])
                raise InsufficientDataError(f"{signal_date} 신호 계산 불가: {reasons}")
            signals[day] = signal
            previous_signal_date = signal_date
        index = dates.index(day)
        if index < 64:
            raise InsufficientDataError("시장 구간 계산을 위한 사전 이력이 부족합니다.")
        spy_return = prices["SPY"][dates[index - 1]][1] / prices["SPY"][dates[index - 64]][1] - 1
        regimes[day] = "상승" if spy_return > 0.05 else "하락" if spy_return < -0.05 else "횡보"

    results = []
    for strategy_id, label in STRATEGIES:
        shares = dict.fromkeys(ETF_UNIVERSE, 0.0)
        cash = initial_capital
        values = [initial_capital]
        trades, rebalances, curve = [], [], []
        turnover = 0.0
        regime_returns = {name: [] for name in ("상승", "하락", "횡보")}
        for day in trading_dates:
            if day in signals and (strategy_id != "SPY_HOLD" or day == trading_dates[0]):
                signal = signals[day]
                weights = target_weights(signal, strategy_id)
                openings = {ticker: prices[ticker][day][0] for ticker in ETF_UNIVERSE}
                cash, executed, traded_ratio = _rebalance(shares, cash, openings, weights, fee_bps, slippage_bps)
                turnover += traded_ratio
                trades.extend(dict(trade, signalDate=signal["asOf"], executionDate=day.isoformat()) for trade in executed)
                rebalances.append(dict(signalDate=signal["asOf"], executionDate=day.isoformat(),
                                       cashWeight=1 - fsum(weights.values()),
                                       targets=[dict(ticker=t, weight=weights[t]) for t in ETF_UNIVERSE]))
            value = cash + fsum(shares[t] * prices[t][day][1] for t in ETF_UNIVERSE)
            if not isfinite(value) or value <= 0:
                raise InsufficientDataError("유효하지 않은 자산 가치입니다. 가격 데이터를 확인해 주세요.")
            regime_returns[regimes[day]].append(value / values[-1] - 1)
            values.append(value)
            curve.append(dict(date=day.isoformat(), value=round(value, 2),
                              returnPct=round((value / initial_capital - 1) * 100, 4)))
        # Include initial cash to retain first-fill costs and the first day's return in all metrics.
        series = pd.Series(values, index=[trading_dates[0] - timedelta(days=1), *trading_dates])
        metrics = performance_metrics(series)
        if any(v is not None and not isfinite(v) for v in metrics.values()):
            raise InsufficientDataError("성과 지표 계산 범위를 초과했습니다.")
        results.append(dict(strategyId=strategy_id, label=label, metrics=metrics, equityCurve=curve,
                            totalFees=round(fsum(t["fee"] for t in trades), 2),
                            totalSlippage=round(fsum(t["slippage"] for t in trades), 2),
                            turnoverPct=round(turnover * 100, 2), trades=trades, rebalances=rebalances,
                            regimes=[dict(label=name, days=len(returns),
                                          returnPct=round((prod(1 + r for r in returns) - 1) * 100, 2) if returns else None)
                                     for name, returns in regime_returns.items()]))
    return dict(ready=True, message=None, requestedStartDate=start.isoformat(), requestedEndDate=end.isoformat(),
                startDate=trading_dates[0].isoformat(), endDate=trading_dates[-1].isoformat(),
                initialCapital=initial_capital, feeBps=fee_bps, slippageBps=slippage_bps, cashRatePct=0,
                currency="USD", strategies=results)


def run_etf_comparison(start: date, end: date, initial_capital: float, fee_bps: float, slippage_bps: float):
    validate_request(start, end, initial_capital, fee_bps, slippage_bps)
    first_signal = start.replace(day=1) - timedelta(days=1)
    bars = load_daily_bars(_start_date(first_signal) - timedelta(days=7), end)
    return compare_etf_strategies(bars, start, end, initial_capital, fee_bps, slippage_bps)
