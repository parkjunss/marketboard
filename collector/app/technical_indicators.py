"""Canonical technical-indicator formulas used by collector jobs."""

import pandas as pd


def ema(values: pd.Series, period: int) -> float | None:
    values = values.dropna().astype(float)
    if period < 1 or len(values) < period:
        return None
    current = float(values.iloc[:period].mean())
    alpha = 2 / (period + 1)
    for value in values.iloc[period:]:
        current = alpha * float(value) + (1 - alpha) * current
    return current


def wilder_rsi(prices: pd.Series, period: int = 14) -> float | None:
    delta = prices.dropna().astype(float).diff().dropna()
    if period < 1 or len(delta) < period:
        return None
    gains = delta.clip(lower=0)
    losses = -delta.clip(upper=0)
    avg_gain = float(gains.iloc[:period].mean())
    avg_loss = float(losses.iloc[:period].mean())
    for i in range(period, len(delta)):
        avg_gain = (avg_gain * (period - 1) + float(gains.iloc[i])) / period
        avg_loss = (avg_loss * (period - 1) + float(losses.iloc[i])) / period
    if avg_gain == 0 and avg_loss == 0:
        return 50.0
    if avg_loss == 0:
        return 100.0
    return 100 - 100 / (1 + avg_gain / avg_loss)


def macd(prices: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> dict | None:
    values = prices.dropna().astype(float)
    if fast < 1 or fast >= slow or signal < 1 or len(values) < slow + signal - 1:
        return None

    def series(period: int) -> pd.Series:
        result = pd.Series(index=values.index, dtype=float)
        current = float(values.iloc[:period].mean())
        result.iloc[period - 1] = current
        alpha = 2 / (period + 1)
        for i in range(period, len(values)):
            current = alpha * float(values.iloc[i]) + (1 - alpha) * current
            result.iloc[i] = current
        return result

    line = (series(fast) - series(slow)).dropna()
    signal_value = ema(line, signal)
    if signal_value is None:
        return None
    line_value = float(line.iloc[-1])
    return {"line": line_value, "signal": signal_value, "histogram": line_value - signal_value}


def bollinger(prices: pd.Series, period: int = 20, multiplier: float = 2.0) -> dict | None:
    values = prices.dropna().astype(float)
    if period < 2 or len(values) < period:
        return None
    window = values.tail(period)
    middle = float(window.mean())
    distance = float(window.std(ddof=1)) * multiplier
    upper, lower = middle + distance, middle - distance
    width = upper - lower
    return {
        "upper": upper,
        "middle": middle,
        "lower": lower,
        "percentB": None if width == 0 else (float(values.iloc[-1]) - lower) / width,
    }


def atr(frame: pd.DataFrame, period: int = 14) -> float | None:
    values = frame[["high", "low", "close"]].dropna().astype(float)
    if period < 1 or len(values) < period:
        return None
    previous_close = values["close"].shift(1)
    true_range = pd.concat(
        [values["high"] - values["low"], (values["high"] - previous_close).abs(), (values["low"] - previous_close).abs()],
        axis=1,
    ).max(axis=1)
    current = float(true_range.iloc[:period].mean())
    for value in true_range.iloc[period:]:
        current = (current * (period - 1) + float(value)) / period
    return current


def relative_volume(volumes: pd.Series, period: int = 20) -> float | None:
    values = volumes.dropna().astype(float)
    if period < 1 or len(values) < period + 1:
        return None
    average = float(values.iloc[-period - 1 : -1].mean())
    return None if average == 0 else float(values.iloc[-1]) / average
