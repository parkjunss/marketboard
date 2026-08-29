from datetime import date

import pandas as pd
import pytest

from app.screener import (
    DEFAULT_CORRELATION_THRESHOLD,
    DEFAULT_MOMENTUM_WINDOW_DAYS,
    DEFAULT_TREND_MA_WINDOW,
    InvalidScreenerParamsError,
    _rsi,
    _select_diversified,
    _ticker_metrics,
)


def _rising_prices(n: int, start: float = 100.0, daily_pct: float = 0.001) -> pd.Series:
    dates = pd.date_range(date(2020, 1, 1), periods=n, freq="D").date
    values = [start * (1 + daily_pct) ** i for i in range(n)]
    return pd.Series(values, index=dates)


def test_ticker_metrics_none_when_history_too_short():
    prices = _rising_prices(DEFAULT_TREND_MA_WINDOW + DEFAULT_MOMENTUM_WINDOW_DAYS - 1)
    assert _ticker_metrics(prices, DEFAULT_MOMENTUM_WINDOW_DAYS, DEFAULT_TREND_MA_WINDOW) is None


def test_ticker_metrics_excludes_a_series_with_an_implausible_one_day_move():
    # Regression test: a real S&P 500 ticker's price_history was observed with a bogus +177%
    # one-day jump (almost certainly bad data -- an unadjusted split or a bad print, not a real
    # move), which blew up its reported volatility to 640%+ and would dominate the ranking with a
    # data artifact rather than a genuine momentum signal.
    prices = _rising_prices(DEFAULT_TREND_MA_WINDOW + DEFAULT_MOMENTUM_WINDOW_DAYS + 5)
    prices.iloc[-10] = prices.iloc[-11] * 2.5  # a +150% one-day "gain"

    assert _ticker_metrics(prices, DEFAULT_MOMENTUM_WINDOW_DAYS, DEFAULT_TREND_MA_WINDOW) is None


def test_ticker_metrics_reports_uptrend_and_positive_momentum_for_a_steady_climb():
    prices = _rising_prices(DEFAULT_TREND_MA_WINDOW + DEFAULT_MOMENTUM_WINDOW_DAYS + 5)
    metrics = _ticker_metrics(prices, DEFAULT_MOMENTUM_WINDOW_DAYS, DEFAULT_TREND_MA_WINDOW)

    assert metrics is not None
    assert metrics["trendUp"] is True
    assert metrics["momentumPct"] > 0
    assert metrics["volatilityPct"] >= 0
    assert 0 <= metrics["rsi14"] <= 100


def test_ticker_metrics_reports_downtrend_for_a_steady_decline():
    prices = _rising_prices(DEFAULT_TREND_MA_WINDOW + DEFAULT_MOMENTUM_WINDOW_DAYS + 5, daily_pct=-0.001)
    metrics = _ticker_metrics(prices, DEFAULT_MOMENTUM_WINDOW_DAYS, DEFAULT_TREND_MA_WINDOW)

    assert metrics is not None
    assert metrics["trendUp"] is False
    assert metrics["momentumPct"] < 0


def test_ticker_metrics_respects_a_shorter_momentum_window_and_trend_ma():
    # With a 30-day momentum window and 50-day trend MA, an 85-day series is already screenable
    # (needs momentum_window_days + trend_ma_window = 80 days), well short of what the 200+126-day
    # default windows would require.
    prices = _rising_prices(85)
    metrics = _ticker_metrics(prices, momentum_window_days=30, trend_ma_window=50)

    assert metrics is not None
    assert metrics["trendUp"] is True
    assert _ticker_metrics(prices, DEFAULT_MOMENTUM_WINDOW_DAYS, DEFAULT_TREND_MA_WINDOW) is None


def test_rsi_is_100_for_an_unbroken_uptrend_and_0_for_an_unbroken_downtrend():
    up = _rising_prices(30, daily_pct=0.01)
    down = _rising_prices(30, daily_pct=-0.01)

    assert _rsi(up) == pytest.approx(100.0, abs=0.01)
    assert _rsi(down) == pytest.approx(0.0, abs=0.01)


def test_select_diversified_skips_highly_correlated_candidates():
    dates = pd.date_range(date(2020, 1, 1), periods=150, freq="D").date
    # AAA and BBB move in lockstep (perfectly correlated); CCC moves independently (anti-correlated
    # with AAA/BBB). Ranked AAA > BBB > CCC by momentum -- BBB should be skipped as too correlated
    # with AAA, so the diversified top-2 should be [AAA, CCC], not [AAA, BBB].
    trend = [1.001**i for i in range(150)]
    anti_trend = [1.0005**i * (1 - 0.02 * (i % 2)) for i in range(150)]
    closes = pd.DataFrame(
        {
            "AAA": [100 * t for t in trend],
            "BBB": [100 * t * 1.0001 for t in trend],
            "CCC": [50 * t for t in anti_trend],
        },
        index=dates,
    )

    selected = _select_diversified(["AAA", "BBB", "CCC"], closes, top_n=2, correlation_threshold=DEFAULT_CORRELATION_THRESHOLD)

    assert selected == ["AAA", "CCC"]


def test_select_diversified_respects_top_n():
    dates = pd.date_range(date(2020, 1, 1), periods=150, freq="D").date
    closes = pd.DataFrame(
        {
            "AAA": [100 * 1.001**i for i in range(150)],
            "BBB": [50 * (1.0005**i) * (1 - 0.03 * (i % 3)) for i in range(150)],
            "CCC": [30 * (1.0007**i) * (1 + 0.03 * (i % 5)) for i in range(150)],
        },
        index=dates,
    )

    selected = _select_diversified(["AAA", "BBB", "CCC"], closes, top_n=1, correlation_threshold=DEFAULT_CORRELATION_THRESHOLD)

    assert selected == ["AAA"]


def test_select_diversified_a_stricter_correlation_threshold_excludes_more():
    dates = pd.date_range(date(2020, 1, 1), periods=150, freq="D").date
    closes = pd.DataFrame(
        {
            "AAA": [100 * (1.001**i) for i in range(150)],
            "BBB": [50 * (1.0008**i) * (1 + 0.01 * ((i * 7) % 5 - 2)) for i in range(150)],
        },
        index=dates,
    )
    # _select_diversified only looks at the trailing CORRELATION_LOOKBACK_DAYS window -- match that
    # here, or a correlation computed over the full series would disagree with what the function
    # actually used internally.
    from app.screener import CORRELATION_LOOKBACK_DAYS

    actual_corr = closes[["AAA", "BBB"]].tail(CORRELATION_LOOKBACK_DAYS).pct_change().corr().loc["AAA", "BBB"]

    lenient = _select_diversified(["AAA", "BBB"], closes, top_n=2, correlation_threshold=actual_corr + 0.05)
    strict = _select_diversified(["AAA", "BBB"], closes, top_n=2, correlation_threshold=actual_corr - 0.05)

    assert lenient == ["AAA", "BBB"]
    assert strict == ["AAA"]


def test_run_screener_rejects_invalid_params():
    from app.screener import run_screener

    with pytest.raises(InvalidScreenerParamsError):
        run_screener(momentum_window_days=0)
    with pytest.raises(InvalidScreenerParamsError):
        run_screener(trend_ma_window=-5)
    with pytest.raises(InvalidScreenerParamsError):
        run_screener(correlation_threshold=1.5)
    with pytest.raises(InvalidScreenerParamsError):
        run_screener(max_rsi=150)
    with pytest.raises(InvalidScreenerParamsError):
        run_screener(min_market_cap=-1)
    with pytest.raises(InvalidScreenerParamsError):
        run_screener(min_revenue=-1)


def test_run_screener_backfills_past_candidates_that_fail_the_market_cap_filter(monkeypatch):
    # 5 candidates, ranked A > B > C > D > E by momentum, all uncorrelated enough to survive
    # diversification (correlation_threshold=1.0 makes that a non-factor here so the test stays
    # focused on the market-cap backfill behavior). A and B are enriched but fail the market-cap
    # filter -- with top_n=2 and the pool oversampled to 4 (2x), C and D should backfill into the
    # final result instead of the screener just returning fewer than top_n picks.
    import app.screener as screener

    n = screener.DEFAULT_TREND_MA_WINDOW + screener.DEFAULT_MOMENTUM_WINDOW_DAYS + 5
    dates = pd.date_range(date(2020, 1, 1), periods=n, freq="D").date
    tickers = ["A", "B", "C", "D", "E"]
    # Distinct daily-return rates (with a touch of alternating noise) keep pairwise correlation
    # comfortably below 1.0 and momentum strictly ordered A > B > C > D > E.
    rates = {"A": 0.0060, "B": 0.0050, "C": 0.0040, "D": 0.0030, "E": 0.0020}
    closes = pd.DataFrame(
        {t: [100.0 * ((1 + r + 0.0003 * (i % 2)) ** i) for i in range(n)] for t, r in rates.items()},
        index=dates,
    )
    monkeypatch.setattr(screener, "_load_universe_closes", lambda *a, **k: closes)

    market_caps = {"A": 1e9, "B": 2e9, "C": 100e9, "D": 200e9, "E": 300e9}

    def fake_enrich(ticker):
        return {**dict(screener._EMPTY_FUNDAMENTALS), "marketCap": market_caps[ticker], "newsSentiment": None, "newsCount": 0}

    monkeypatch.setattr(screener, "_enrich", fake_enrich)

    result = screener.run_screener(top_n=2, correlation_threshold=1.0, min_market_cap=50e9)

    assert [r["ticker"] for r in result["results"]] == ["C", "D"]
