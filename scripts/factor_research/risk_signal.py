"""Pre-registered loss-reduction signal evaluation (docs/Risk_Signal_Plan.md); holdout stays closed."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path

import numpy as np
import pandas as pd

from .data import sha256
from .evaluate import SPLITS, HOLDOUT_START

HISTORY_DAYS = 252
HORIZON = 21
COST = 0.001
VOL_TARGET = 0.15
BAND = 0.02
RULES = ("R1_trend", "R2_vol_target", "R3_trend_vol", "R4_trend_band")
SCORES = ("vol_20", "below_200", "drawdown_252")
GATES = dict(minAssetsWithLowerDrawdown=12, minMedianDrawdownReduction=0.20, minMedianSharpeDelta=-0.05)


def signals(close):
    """Scores and rule weights at each close; every value reads closes <= t only."""
    returns = close.pct_change(fill_method=None)
    sma200 = close.rolling(200).mean()
    frame = pd.DataFrame(index=close.index)
    frame["vol_20"] = returns.rolling(20).std(ddof=1) * np.sqrt(252)
    frame["below_200"] = -(close / sma200 - 1)
    frame["drawdown_252"] = 1 - close / close.rolling(252, min_periods=1).max()
    above = (close >= sma200).astype(float)
    vol_weight = np.minimum(1, VOL_TARGET / frame.vol_20)
    frame["R1_trend"] = above
    frame["R2_vol_target"] = vol_weight
    frame["R3_trend_vol"] = above * vol_weight
    band, state = [], 1.0
    for distance in (close / sma200 - 1).to_numpy():
        if distance < -BAND:
            state = 0.0
        elif distance > BAND:
            state = 1.0
        band.append(state)
    frame["R4_trend_band"] = band
    return frame


def next_open_returns(opens):
    """Return earned by a weight set at close t: open t+1 -> open t+2."""
    return opens.shift(-2) / opens.shift(-1) - 1


def performance(weights, forward):
    """Cost on every weight change, including the first entry from cash."""
    forward = forward.reindex(weights.index)
    valid = forward.notna()
    weights, forward = weights[valid], forward[valid]
    turnover = weights.diff().abs().fillna(weights.abs())
    daily = weights * forward - turnover * COST
    equity = (1 + daily).cumprod()
    curve = pd.concat([pd.Series([1.0]), equity], ignore_index=True)  # starting capital counts as the first peak
    years = len(daily) / 252
    sd = daily.std(ddof=1)
    return dict(days=len(daily), annualReturn=float(equity.iloc[-1] ** (1 / years) - 1),
                maxDrawdown=float((1 - curve / curve.cummax()).max()),
                sharpe=float(daily.mean() / sd * np.sqrt(252)) if sd > 0 else None,
                annualTurnover=float(turnover.sum() / years), meanWeight=float(weights.mean()))


def future_risk(close):
    returns = close.pct_change(fill_method=None)
    vol = returns[::-1].rolling(HORIZON).std(ddof=1)[::-1].shift(-1) * np.sqrt(252)
    path = np.column_stack([close.shift(-k).to_numpy() for k in range(1, HORIZON + 1)])
    peak = np.maximum.accumulate(np.column_stack([close.to_numpy(), path]), axis=1)[:, 1:]
    drawdown = np.nanmax(1 - path / peak, axis=1)
    drawdown[np.isnan(path).any(axis=1)] = np.nan
    return pd.DataFrame({"future_vol": vol, "future_drawdown": drawdown}, index=close.index)


def split_rows(index):
    dates = pd.Series(index, index=index)
    result = {}
    for name, start, end in SPLITS:
        mask = (dates <= end) & ((dates >= start) if start else True)
        result[name] = dates[mask].index
    return result


def evaluate_asset(ticker, series):
    series = series[series.date < HOLDOUT_START].set_index("date")  # holdout prices are never read
    close, forward = series.adj_close, next_open_returns(series.adj_open)
    frame = signals(close).join(future_risk(close))
    frame = frame.iloc[HISTORY_DAYS:]
    performance_rows, predictive_rows = [], []
    for split, index in split_rows(frame.index).items():
        if len(index) < 252:
            continue
        part = frame.loc[index]
        for rule in ("buy_hold",) + RULES:
            weights = pd.Series(1.0, index=index) if rule == "buy_hold" else part[rule]
            performance_rows.append(dict(ticker=ticker, split=split, rule=rule, **performance(weights, forward)))
        predictive_rows += predictive(ticker, split, part)
    return performance_rows, predictive_rows


def predictive(ticker, split, part):
    """Spearman of each score against next-21-day risk on non-overlapping 21-day samples."""
    sample = part.iloc[::HORIZON].dropna(subset=["future_vol", "future_drawdown"])
    return [dict(ticker=ticker, split=split, score=score, target=target, samples=len(sample),
                 spearman=float(sample[score].rank().corr(sample[target].rank())))
            for score in SCORES for target in ("future_vol", "future_drawdown")]


def holdout_predictive(prices):
    """Pre-registered holdout check: predictiveness only, no exposure rule is ever scored on these dates."""
    rows = []
    for ticker, series in prices.groupby("ticker"):
        close = series.sort_values("date").set_index("date").adj_close
        frame = signals(close).join(future_risk(close)).iloc[HISTORY_DAYS:]
        rows += predictive(ticker, "holdout", frame[frame.index >= HOLDOUT_START])
    table = pd.DataFrame(rows)
    gates = []
    for target in ("future_vol", "future_drawdown"):
        part = table[(table.score == "vol_20") & (table.target == target)]
        median, positive = float(part.spearman.median()), int((part.spearman > 0).sum())
        gates.append(dict(score="vol_20", target=target, assets=len(part), medianSpearman=median,
                          positiveAssets=positive, passed=bool(median > 0 and positive >= 12)))
    return table, pd.DataFrame(gates)


def judge(performance_frame):
    rows = []
    hold = performance_frame[performance_frame.rule == "buy_hold"].set_index(["ticker", "split"])
    for (rule, split), part in performance_frame[performance_frame.rule != "buy_hold"].groupby(["rule", "split"]):
        part = part.set_index(["ticker", "split"])
        base = hold.loc[part.index]
        reduction = 1 - part.maxDrawdown / base.maxDrawdown
        sharpe = part.sharpe - base.sharpe
        row = dict(rule=rule, split=split, assets=len(part),
                   assetsWithLowerDrawdown=int((part.maxDrawdown < base.maxDrawdown).sum()),
                   medianDrawdownReduction=float(reduction.median()), medianSharpeDelta=float(sharpe.median()),
                   medianReturnDelta=float((part.annualReturn - base.annualReturn).median()),
                   medianTurnover=float(part.annualTurnover.median()), medianWeight=float(part.meanWeight.median()))
        row["passed"] = bool(row["assetsWithLowerDrawdown"] >= GATES["minAssetsWithLowerDrawdown"]
                             and row["medianDrawdownReduction"] >= GATES["minMedianDrawdownReduction"]
                             and row["medianSharpeDelta"] >= GATES["minMedianSharpeDelta"])
        rows.append(row)
    table = pd.DataFrame(rows)
    both = table.groupby("rule").passed.all()
    candidates = table[(table.split == "validation") & table.rule.isin(both[both].index)]
    selected = (candidates.sort_values(["medianSharpeDelta", "medianTurnover"], ascending=[False, True]).rule.iloc[0]
                if len(candidates) else None)
    return table, selected


def run_holdout(snapshot, output):
    snapshot, output = Path(snapshot), Path(output)
    if output.exists():
        raise ValueError("Choose a fresh holdout output directory")
    prices = pd.read_csv(snapshot / "prices.csv")
    table, gates = holdout_predictive(prices)
    summary = table.groupby(["score", "target"]).agg(
        assets=("spearman", "size"), medianSpearman=("spearman", "median"), medianSamples=("samples", "median"),
        positiveAssets=("spearman", lambda x: int((x > 0).sum()))).reset_index()
    output.mkdir(parents=True)
    table.to_csv(output / "predictive.csv", index=False)
    summary.to_csv(output / "predictive-summary.csv", index=False)
    gates.to_csv(output / "gates.csv", index=False)
    (output / "protocol.json").write_text(json.dumps(dict(
        createdAt=datetime.now(timezone.utc).isoformat(), snapshotSha256=sha256(snapshot / "prices.csv"),
        holdoutStart=HOLDOUT_START, lastPriceDate=prices.date.max(), horizon=HORIZON, scope="predictiveness only",
        exposureRulesEvaluated=False, gates="vol_20 median Spearman > 0 and >= 12/15 positive, per target",
        sourceSha256=sha256(Path(__file__))), indent=2), encoding="utf-8")
    print(summary.round(3).to_string(index=False))
    print(gates.round(3).to_string(index=False))


def run(snapshot, output):
    snapshot, output = Path(snapshot), Path(output)
    if output.exists():
        raise ValueError("Choose a fresh risk-signal output directory")
    prices = pd.read_csv(snapshot / "prices.csv")
    performance_rows, predictive_rows = [], []
    for ticker, series in prices.groupby("ticker"):
        p, q = evaluate_asset(ticker, series.sort_values("date"))
        performance_rows += p
        predictive_rows += q
    performance_frame, predictive = pd.DataFrame(performance_rows), pd.DataFrame(predictive_rows)
    table, selected = judge(performance_frame)
    predictive_summary = predictive.groupby(["split", "score", "target"]).agg(
        assets=("spearman", "size"), medianSpearman=("spearman", "median"),
        positiveAssets=("spearman", lambda s: int((s > 0).sum()))).reset_index()
    output.mkdir(parents=True)
    performance_frame.to_csv(output / "performance.csv", index=False)
    predictive.to_csv(output / "predictive.csv", index=False)
    predictive_summary.to_csv(output / "predictive-summary.csv", index=False)
    table.to_csv(output / "gates.csv", index=False)
    (output / "protocol.json").write_text(json.dumps(dict(
        createdAt=datetime.now(timezone.utc).isoformat(), snapshotSha256=sha256(snapshot / "prices.csv"),
        splits=SPLITS, holdoutStart=HOLDOUT_START, holdoutEvaluated=False, rules=RULES, scores=SCORES,
        horizon=HORIZON, oneWayCost=COST, volTarget=VOL_TARGET, band=BAND, gates=GATES, selected=selected,
        sourceSha256=sha256(Path(__file__))), indent=2), encoding="utf-8")
    print(predictive_summary.round(3).to_string(index=False))
    print(table.round(3).to_string(index=False))
    print("selected:", selected)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", default="artifacts/factor-research/etf-snapshot-2026-10-29")
    parser.add_argument("--output", required=True)
    parser.add_argument("--holdout-predictive", action="store_true", help="pre-registered 2020+ predictiveness check")
    args = parser.parse_args()
    (run_holdout if args.holdout_predictive else run)(args.snapshot, args.output)
