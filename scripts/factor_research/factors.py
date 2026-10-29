"""Point-in-time monthly factor scores and next-open labels from a long daily-bar frame."""
import numpy as np
import pandas as pd

FACTORS = ("mom_12_1", "mom_6_1", "mom_3", "low_vol_60", "reversal_1m", "trend_200", "combo")
HISTORY_DAYS = 252


def wide(prices, field):
    return prices.pivot(index="date", columns="ticker", values=field).sort_index()


def daily_factors(close):
    """Every value at row t reads rows <= t only (shift/rolling look backward)."""
    returns = close.pct_change(fill_method=None)
    return {
        "mom_12_1": close.shift(21) / close.shift(252) - 1,
        "mom_6_1": close.shift(21) / close.shift(126) - 1,
        "mom_3": close / close.shift(63) - 1,
        "low_vol_60": -returns.rolling(60).std(ddof=1),
        "reversal_1m": -(close / close.shift(21) - 1),
        "trend_200": close / close.rolling(200).mean() - 1,
    }


def month_ends(dates):
    index = pd.Series(pd.to_datetime(dates), index=dates)
    return index.groupby(index.dt.to_period("M")).idxmax().tolist()


def monthly_panel(prices):
    """One row per (signal month-end, ticker) with factors, eligibility and the next-open holding label."""
    close, opens = wide(prices, "adj_close"), wide(prices, "adj_open")
    dates = close.index.tolist()
    position = {date: i for i, date in enumerate(dates)}
    raw = daily_factors(close)
    history = close.notna().cumsum() - 1  # number of earlier bars
    ends = month_ends(dates)
    rows = []
    for current, following in zip(ends, ends[1:] + [None]):
        i = position[current]
        if i + 1 >= len(dates):
            continue
        execution = dates[i + 1]
        exit_ = dates[position[following] + 1] if following and position[following] + 1 < len(dates) else None
        frame = pd.DataFrame({name: values.loc[current] for name, values in raw.items()})
        frame["eligible"] = close.loc[current].notna() & (history.loc[current] >= HISTORY_DAYS) & frame.notna().all(axis=1)
        frame["label"] = (opens.loc[exit_] / opens.loc[execution] - 1) if exit_ else np.nan
        frame["signalDate"], frame["executionDate"], frame["exitDate"] = current, execution, exit_
        rows.append(frame.rename_axis("ticker").reset_index())
    panel = pd.concat(rows, ignore_index=True)
    return add_combo(panel)


def add_combo(panel):
    eligible = panel[panel.eligible]
    ranks = eligible.groupby("signalDate")[["mom_12_1", "low_vol_60"]].rank(pct=True)
    panel["combo"] = np.nan
    panel.loc[eligible.index, "combo"] = ranks.mean(axis=1)
    return panel
