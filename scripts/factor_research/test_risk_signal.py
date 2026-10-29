import unittest
import numpy as np
import pandas as pd

from .risk_signal import future_risk, holdout_predictive, next_open_returns, performance, signals


def series(days=400, seed=0):
    generator = np.random.default_rng(seed)
    index = [d.date().isoformat() for d in pd.bdate_range("2000-01-03", periods=days)]
    return pd.Series(100 * np.cumprod(1 + generator.normal(0, .01, days)), index=index)


class RiskSignalTest(unittest.TestCase):
    def test_signals_ignore_future_closes(self):
        close = series()
        changed = close.copy()
        changed.iloc[300:] *= 0.5
        pd.testing.assert_frame_equal(signals(close).iloc[:300], signals(changed).iloc[:300])

    def test_band_keeps_previous_state_inside_band(self):
        close = pd.Series([100.0] * 200 + [97.0, 99.0, 103.0, 100.0])
        weights = signals(close).R4_trend_band.iloc[200:].tolist()
        self.assertEqual(weights, [0.0, 0.0, 1.0, 1.0])  # 99 and 100 sit inside the +-2% band

    def test_weight_earns_next_open_to_following_open_with_entry_cost(self):
        opens = pd.Series([100.0, 100.0, 100.0, 110.0])
        result = performance(pd.Series([1.0, 1.0]), next_open_returns(opens))
        self.assertEqual(result["days"], 2)
        self.assertAlmostEqual(result["maxDrawdown"], 0.001)  # flat first day: entry cost alone is a loss from starting capital

    def test_future_risk_reads_only_following_window(self):
        close = pd.Series([100.0] + [100.0, 90.0] + [95.0] * 19 + [50.0])
        risk = future_risk(close)
        self.assertAlmostEqual(risk.future_drawdown.iloc[0], 0.10)  # the day-22 crash is outside t+1..t+21


    def test_holdout_check_scores_only_holdout_dates(self):
        days = pd.bdate_range("2018-01-01", "2021-12-31")
        generator = np.random.default_rng(3)
        prices = pd.concat([pd.DataFrame(dict(ticker=t, date=[d.date().isoformat() for d in days],
                                              adj_close=100 * np.cumprod(1 + generator.normal(0, .01, len(days)))))
                            for t in ("A", "B")])
        table, gates = holdout_predictive(prices)
        self.assertEqual(set(table.split), {"holdout"})
        self.assertTrue((table.samples <= len(days[days >= "2020-01-01"]) // 21 + 1).all())
        self.assertEqual(set(gates.target), {"future_vol", "future_drawdown"})


if __name__ == "__main__":
    unittest.main()
