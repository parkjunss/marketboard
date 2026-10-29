import unittest
import numpy as np
import pandas as pd

from .evaluate import split_of, top_k_returns
from .factors import monthly_panel


def prices(tickers=("A", "B", "C"), days=320, seed=0):
    generator = np.random.default_rng(seed)
    dates = [d.date().isoformat() for d in pd.bdate_range("2000-01-03", periods=days)]
    rows = []
    for ticker in tickers:
        close = 100 * np.cumprod(1 + generator.normal(0, .01, days))
        rows.append(pd.DataFrame(dict(date=dates, ticker=ticker, adj_close=close, adj_open=close * .999)))
    return pd.concat(rows, ignore_index=True)


class FactorTest(unittest.TestCase):
    def test_future_bars_do_not_change_past_scores(self):
        base = prices()
        changed = base.copy()
        cutoff = sorted(base.date.unique())[300]
        changed.loc[changed.date > cutoff, ["adj_close", "adj_open"]] *= 3
        a, b = monthly_panel(base), monthly_panel(changed)
        signal = a[a.eligible].signalDate.min()
        self.assertLess(signal, cutoff)
        columns = ["mom_12_1", "mom_6_1", "mom_3", "low_vol_60", "reversal_1m", "trend_200", "combo"]
        pd.testing.assert_frame_equal(a[a.signalDate == signal][columns].reset_index(drop=True),
                                      b[b.signalDate == signal][columns].reset_index(drop=True))

    def test_label_runs_from_next_open_to_next_rebalance_open(self):
        panel = monthly_panel(prices(days=60))
        row = panel.iloc[0]
        frame = prices(days=60).set_index(["date", "ticker"]).adj_open
        self.assertGreater(row.executionDate, row.signalDate)
        self.assertAlmostEqual(row.label, frame[(row.exitDate, row.ticker)] / frame[(row.executionDate, row.ticker)] - 1)
        self.assertFalse(panel.eligible.any())  # under a year of history

    def test_top_k_charges_cost_on_full_entry_then_no_trade_when_unchanged(self):
        panel = pd.DataFrame(dict(signalDate=["m1"] * 2 + ["m2"] * 2, ticker=["A", "B"] * 2,
                                  score=[2, 1, 2, 1], label=[0.0, 0.0, 0.0, 0.0]))
        result = top_k_returns(panel, "score", 1)
        self.assertAlmostEqual(result.loc["m1", "topNet"], -0.001)
        self.assertAlmostEqual(result.loc["m2", "topNet"], 0.0)

    def test_holdout_is_its_own_split(self):
        self.assertEqual(split_of("2014-12-31"), "development")
        self.assertEqual(split_of("2015-01-30"), "validation")
        self.assertEqual(split_of("2020-01-31"), "holdout")


if __name__ == "__main__":
    unittest.main()
