"""Step 3: pre-registered factor measurement on development/validation months; holdout stays closed."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path

import numpy as np
import pandas as pd

from .data import load_membership, members_on, sha256
from .etf_data import UNIVERSES
from .factors import FACTORS, add_combo, monthly_panel

SPLITS = (("development", None, "2014-12-31"), ("validation", "2015-01-01", "2019-12-31"))
HOLDOUT_START = "2020-01-01"
STOCK_RANGE = ("2018-01-01", "2026-07-31")
TOP_K = {"U1": 3, "U2": 3, "S": 20}
COST = 0.001  # one-way 10bps on traded notional
BLOCK = 12
SAMPLES = 2000


def split_of(date):
    for name, start, end in SPLITS:
        if (start is None or date >= start) and date <= end:
            return name
    return "holdout" if date >= HOLDOUT_START else None


def monthly_ic(panel, factor):
    def ic(group):
        return group[factor].rank().corr(group.label.rank())
    return panel.groupby("signalDate").apply(ic, include_groups=False)


def top_k_returns(panel, factor, k):
    """Net-of-cost monthly return of the top-k equal-weight basket and of the whole-universe equal weight."""
    rows, held, benchmark = [], pd.Series(dtype=float), pd.Series(dtype=float)
    for date, group in panel.groupby("signalDate"):
        group = group.set_index("ticker")
        chosen = group[factor].nlargest(k).index
        target = pd.Series(1 / len(chosen), index=chosen)
        universe = pd.Series(1 / len(group), index=group.index)
        top_trade = target.sub(held, fill_value=0).abs().sum()
        bench_trade = universe.sub(benchmark, fill_value=0).abs().sum()
        top_gross = float((target * group.label[chosen]).sum())
        bench_gross = float((universe * group.label).sum())
        rows.append(dict(signalDate=date, topNet=top_gross - top_trade * COST, benchNet=bench_gross - bench_trade * COST,
                         topTurnover=top_trade / 2))
        # Drift to next rebalance; sold-out names leave the book.
        held = target * (1 + group.label[chosen]) / (1 + top_gross)
        benchmark = universe * (1 + group.label) / (1 + bench_gross)
    return pd.DataFrame(rows).set_index("signalDate")


def block_mean_interval(values, seed=0):
    values = np.asarray(values, dtype=float)
    generator = np.random.default_rng(seed)
    starts = np.arange(len(values) - BLOCK + 1)
    means = [values[(generator.choice(starts, size=int(np.ceil(len(values) / BLOCK)))[:, None]
                     + np.arange(BLOCK)).ravel()[:len(values)]].mean() for _ in range(SAMPLES)]
    return [float(x) for x in np.percentile(means, [2.5, 97.5])]


def summarize(ic, returns, universe, factor, split):
    excess = returns.topNet - returns.benchNet
    se = ic.std(ddof=1) / np.sqrt(len(ic))
    return dict(universe=universe, factor=factor, split=split, months=len(ic),
                firstMonth=ic.index.min(), lastMonth=ic.index.max(),
                meanIC=float(ic.mean()), tIC=float(ic.mean() / se) if se > 0 else None,
                icCi95=block_mean_interval(ic.to_numpy()), icPositivePct=float((ic > 0).mean() * 100),
                meanExcessPct=float(excess.mean() * 100), excessCi95Pct=[x * 100 for x in block_mean_interval(excess)],
                annualExcessPct=float(((1 + returns.topNet).prod() ** (12 / len(returns))
                                       - (1 + returns.benchNet).prod() ** (12 / len(returns))) * 100),
                meanMonthlyTurnoverPct=float(returns.topTurnover.mean() * 100))


def etf_panel(prices, universe):
    panel = monthly_panel(prices[prices.ticker.isin(UNIVERSES[universe])])
    complete = panel.groupby("signalDate").apply(
        lambda g: g.eligible.all() and g.label.notna().all() and len(g) == len(UNIVERSES[universe]), include_groups=False)
    return panel[panel.signalDate.isin(complete[complete].index)]


def stock_panel(prices, membership):
    panel = monthly_panel(prices)
    panel = panel[(panel.signalDate >= STOCK_RANGE[0]) & (panel.signalDate <= STOCK_RANGE[1])]
    members = {date: members_on(membership, date) for date in panel.signalDate.unique()}
    current = [ticker in members[date] for ticker, date in zip(panel.ticker, panel.signalDate)]
    # Names without a next-open label (delisted mid-month) drop out: survivorship-biased by construction.
    panel = panel[np.asarray(current) & panel.eligible & panel.label.notna()]
    return add_combo(panel.drop(columns="combo"))  # rank the combo among then-current members only


def run(etf_snapshot, stock_snapshot, output):
    etf_snapshot, stock_snapshot, output = Path(etf_snapshot), Path(stock_snapshot), Path(output)
    if output.exists():
        raise ValueError("Choose a fresh evaluation directory")
    etf_prices = pd.read_csv(etf_snapshot / "prices.csv")
    rows, monthly = [], []
    for universe in ("U1", "U2"):
        panel = etf_panel(etf_prices, universe)
        panel["split"] = panel.signalDate.map(split_of)
        panel = panel[panel.split.isin(["development", "validation"])]  # holdout never enters the computation
        for factor in FACTORS:
            for split, part in panel.groupby("split"):
                ic, returns = monthly_ic(part, factor), top_k_returns(part, factor, TOP_K[universe])
                rows.append(summarize(ic, returns, universe, factor, split))
                monthly.append(returns.assign(ic=ic, universe=universe, factor=factor, split=split).reset_index())
        print(f"Evaluated {universe}", flush=True)
    stock_prices = pd.read_csv(stock_snapshot / "prices.csv.gz")
    membership = load_membership(stock_snapshot / "membership/S&P 500 Historical Components & Changes (Updated).csv")
    panel = stock_panel(stock_prices, membership)
    for factor in FACTORS:
        ic, returns = monthly_ic(panel, factor), top_k_returns(panel, factor, TOP_K["S"])
        rows.append(summarize(ic, returns, "S", factor, "supplementary"))
        monthly.append(returns.assign(ic=ic, universe="S", factor=factor, split="supplementary").reset_index())
    print("Evaluated S", flush=True)
    summary = pd.DataFrame(rows)
    gates = []
    for (universe, factor), part in summary[summary.universe != "S"].groupby(["universe", "factor"]):
        dev, val = (part[part.split == s].iloc[0] for s in ("development", "validation"))
        checks = dict(developmentIC=bool(dev.meanIC > 0 and (dev.tIC or 0) > 2),
                      validationIC=bool(val.meanIC > 0), validationExcess=bool(val.meanExcessPct > 0))
        gates.append(dict(universe=universe, factor=factor, **checks, passed=all(checks.values())))
    output.mkdir(parents=True)
    summary.to_csv(output / "summary.csv", index=False)
    pd.concat(monthly).to_csv(output / "monthly.csv", index=False)
    pd.DataFrame(gates).to_csv(output / "gates.csv", index=False)
    (output / "protocol.json").write_text(json.dumps(dict(
        createdAt=datetime.now(timezone.utc).isoformat(), etfSnapshotSha256=sha256(etf_snapshot / "prices.csv"),
        stockSnapshotSha256=sha256(stock_snapshot / "prices.csv.gz"), splits=SPLITS, holdoutStart=HOLDOUT_START,
        holdoutEvaluated=False, stockRange=STOCK_RANGE, topK=TOP_K, oneWayCost=COST, factors=FACTORS,
        bootstrap=dict(blockMonths=BLOCK, samples=SAMPLES), comparisons=len(gates),
        sourceSha256={name: sha256(Path(__file__).parent / name) for name in ("evaluate.py", "factors.py")}),
        indent=2), encoding="utf-8")
    print(pd.DataFrame(gates).to_string(index=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--etf-snapshot", default="artifacts/factor-research/etf-snapshot-2026-10-29")
    parser.add_argument("--stock-snapshot", default="artifacts/factor-research/snapshot-2026-10-29")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    run(args.etf_snapshot, args.stock_snapshot, args.output)
