import pandas as pd
import pytest

from app.technical_indicators import atr, bollinger, ema, macd, relative_volume, wilder_rsi


def test_selected_indicator_formulas():
    assert ema(pd.Series([1, 2, 3, 4]), 3) == pytest.approx(3.0)
    assert wilder_rsi(pd.Series([10, 11, 10, 12, 11]), 3) == pytest.approx(54.545455)
    assert macd(pd.Series([1, 2, 3, 4, 5, 6]), 2, 3, 2) == pytest.approx(
        {"line": 0.5, "signal": 0.5, "histogram": 0.0}
    )
    assert bollinger(pd.Series([1, 2, 3]), 3, 2) == pytest.approx(
        {"upper": 4.0, "middle": 2.0, "lower": 0.0, "percentB": 0.75}
    )
    frame = pd.DataFrame({"high": [10, 13, 14], "low": [9, 11, 12], "close": [9, 12, 13]})
    assert atr(frame, 2) == pytest.approx(2.25)
    assert relative_volume(pd.Series([100, 200, 450]), 2) == pytest.approx(3.0)


def test_flat_series_edge_cases():
    assert wilder_rsi(pd.Series([10] * 20), 14) == 50.0
    assert bollinger(pd.Series([10] * 20), 20)["percentB"] is None
