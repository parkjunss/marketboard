"""Monthly ETF signals shared by the research API and future backtests.

Only stored daily bars are read. Missing data never becomes a cash allocation.
"""

from calendar import monthrange
from datetime import date, datetime, time, timedelta, timezone
from math import fsum, isfinite
from zoneinfo import ZoneInfo

ETF_UNIVERSE = ("SPY", "QQQ", "EFA", "EEM", "IEF", "GLD")
NEW_YORK = ZoneInfo("America/New_York")


def last_completed_month(now: datetime | None = None) -> date:
    today = (now or datetime.now(timezone.utc)).astimezone(NEW_YORK).date()
    return today.replace(day=1) - timedelta(days=1)


def _start_date(as_of: date) -> date:
    return date(as_of.year - 1, as_of.month, monthrange(as_of.year - 1, as_of.month)[1])


def build_etf_momentum(histories: dict, as_of: date) -> dict:
    if as_of.day != monthrange(as_of.year, as_of.month)[1]:
        raise ValueError("월말 기준일이 필요합니다.")
    start_date = _start_date(as_of)
    end_date = as_of.replace(day=1) - timedelta(days=1)
    rows = []
    date_sets = []
    for ticker in ETF_UNIVERSE:
        row = dict(ticker=ticker, rank=None, momentum=None, close=None, sma200=None,
                   sessionDate=None, weight=None, reason="")
        rows.append(row)
        bars = []
        for timestamp, close in histories.get(ticker, []):
            if not isinstance(timestamp, datetime):
                row["reason"] = "잘못된 일봉 날짜"
                break
            # MySQL DATETIME values are stored as UTC but returned without tzinfo.
            session = timestamp.replace(tzinfo=timezone.utc) if timestamp.tzinfo is None else timestamp
            session_date = session.astimezone(NEW_YORK).date()
            if session_date > as_of:
                continue
            try:
                close = float(close)
            except (ValueError, TypeError, OverflowError):
                close = float("nan")
            if not isfinite(close) or close <= 0:
                row["reason"] = "유효하지 않은 종가"
                break
            bars.append((session_date, close))
        if row["reason"]:
            continue
        bars.sort(key=lambda bar: bar[0])
        if not bars:
            row["reason"] = "일봉 데이터 없음"
            continue
        if len({day for day, _ in bars}) != len(bars):
            row["reason"] = "중복 일봉 날짜"
            continue
        latest = bars[-1]
        start = next((bar for bar in reversed(bars) if bar[0] <= start_date), None)
        end = next((bar for bar in reversed(bars) if bar[0] <= end_date), None)
        # Keep observable metrics even when history quality prevents portfolio weights.
        sma = fsum(close / 200 for _, close in bars[-200:]) if len(bars) >= 200 else None
        momentum = (end[1] / start[1] - 1) * 100 if start is not None and end is not None else None
        row.update(close=latest[1], sessionDate=latest[0].isoformat(),
                   sma200=sma if sma is not None and isfinite(sma) else None,
                   momentum=momentum if momentum is not None and isfinite(momentum) else None)
        if (len(bars) < 200 or start is None or end is None
                or (as_of - latest[0]).days > 7
                or (start_date - start[0]).days > 7 or (end_date - end[0]).days > 7):
            row["reason"] = "12개월 이력·200일봉 또는 기준일 인접 데이터 부족"
            continue
        dates = [day for day, _ in bars if day >= start[0]]
        # shortcut: use gap/date agreement until an exchange calendar is added before live trading.
        if any((right - left).days > 7 for left, right in zip(dates, dates[1:])):
            row["reason"] = "일봉 이력에 7일 초과 공백 · 수치는 관측 일봉 기준, 비중 제안 보류"
            continue
        if not isfinite(momentum) or not isfinite(sma):
            row["reason"] = "계산 가능한 가격 범위 초과"
            continue
        date_sets.append(dates)

    ready = len(date_sets) == len(ETF_UNIVERSE) and all(dates == date_sets[0] for dates in date_sets)
    cash_weight = None
    if ready:
        rows.sort(key=lambda row: (-row["momentum"], row["ticker"]))
        for rank, row in enumerate(rows, 1):
            selected = rank <= 3 and row["close"] > row["sma200"]
            row.update(rank=rank, weight=1 / 3 if selected else 0,
                       reason="상위 3위 밖" if rank > 3 else "상위 3위 · 200일선 상회" if selected
                       else "200일선 이하 · 해당 비중 현금")
        cash_weight = (3 - sum(row["weight"] > 0 for row in rows)) / 3
    else:
        for row in rows:
            if not row["reason"]:
                row["reason"] = "전체 ETF 데이터·거래일 정합성 확인 전 제안 보류"
    return dict(asOf=as_of.isoformat(), startDate=start_date.isoformat(), endDate=end_date.isoformat(),
                ready=ready, rows=rows, cashWeight=cash_weight)


def target_weights(signal: dict, strategy_id: str) -> dict:
    if strategy_id not in ("SPY_HOLD", "MOMENTUM", "MOMENTUM_TREND"):
        raise ValueError("지원하지 않는 ETF 전략입니다.")
    if not signal["ready"]:
        raise ValueError("ETF 신호 데이터가 부족합니다.")
    if strategy_id == "SPY_HOLD":
        return {ticker: float(ticker == "SPY") for ticker in ETF_UNIVERSE}
    return {row["ticker"]: (row["weight"] if strategy_id == "MOMENTUM_TREND"
            else 1 / 3 if row["rank"] <= 3 else 0.0) for row in signal["rows"]}


def load_daily_bars(start_date: date, end_date: date, tickers=ETF_UNIVERSE) -> dict:
    from . import mysql_writer

    start = datetime.combine(start_date, time.min, NEW_YORK)
    end = datetime.combine(end_date + timedelta(days=1), time.min, NEW_YORK)
    conn = mysql_writer.connect()
    try:
        with conn.cursor() as cur:
            placeholders = ",".join(["%s"] * len(tickers))
            cur.execute(f"SELECT id, ticker FROM symbols WHERE ticker IN ({placeholders})", tuple(tickers))
            tickers = {symbol_id: ticker for symbol_id, ticker in cur.fetchall()}
            if not tickers:
                return {}
            placeholders = ",".join(["%s"] * len(tickers))
            cur.execute(
                f"SELECT symbol_id, ts, open, close FROM price_history WHERE symbol_id IN ({placeholders}) "
                "AND timeframe = '1d' AND ts >= %s AND ts < %s ORDER BY ts",
                (*tickers, start.astimezone(timezone.utc).replace(tzinfo=None),
                 end.astimezone(timezone.utc).replace(tzinfo=None)),
            )
            histories = {ticker: [] for ticker in tickers.values()}
            for symbol_id, timestamp, opening, close in cur.fetchall():
                histories[tickers[symbol_id]].append((timestamp, opening, close))
            return histories
    finally:
        conn.close()


def _load_histories(as_of: date) -> dict:
    bars = load_daily_bars(_start_date(as_of) - timedelta(days=7), as_of)
    return {ticker: [(ts, close) for ts, _, close in rows] for ticker, rows in bars.items()}


def run_etf_momentum() -> dict:
    as_of = last_completed_month()
    return build_etf_momentum(_load_histories(as_of), as_of)
