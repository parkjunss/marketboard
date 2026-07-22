from unittest.mock import Mock, patch

import pytest

from app.options_levels import OptionsLevelsUnavailableError, _compute_max_pain, get_options_levels


def _contract(option: str, open_interest: float) -> dict:
    return {"option": option, "open_interest": open_interest}


def _cboe_response(current_price: float, contracts: list[dict]) -> Mock:
    response = Mock()
    response.raise_for_status = Mock()
    response.json.return_value = {"data": {"current_price": current_price, "options": contracts}}
    return response


def test_max_pain_is_the_strike_with_smallest_total_writer_payout():
    # Below 100: only the 100 call is ITM (worth 0 at strike 100 itself). Below 105: the 105
    # put is ITM. 100 is the strike where neither side owes anything -- the obvious minimum.
    calls_by_strike = {100.0: 10, 110.0: 5}
    puts_by_strike = {90.0: 5, 100.0: 10}

    assert _compute_max_pain(calls_by_strike, puts_by_strike) == 100.0


def test_max_pain_is_none_when_there_are_no_strikes():
    assert _compute_max_pain({}, {}) is None


@patch("app.options_levels.requests.get")
def test_get_options_levels_uses_the_nearest_expiration_and_ranks_strikes_by_open_interest(mock_get):
    mock_get.return_value = _cboe_response(
        100.0,
        [
            # Nearest expiration (2026-07-22): the one that should be used.
            _contract("AAPL260722C00105000", 50),
            _contract("AAPL260722C00110000", 200),  # top resistance candidate
            _contract("AAPL260722P00095000", 300),  # top support candidate
            _contract("AAPL260722P00090000", 20),
            # A later expiration -- must be excluded from the result entirely.
            _contract("AAPL260814C00110000", 9999),
        ],
    )

    result = get_options_levels("AAPL")

    assert result["ticker"] == "AAPL"
    assert result["expiration"] == "2026-07-22"
    assert result["spotPrice"] == 100.0
    assert result["resistanceLevels"] == [{"strike": 105.0, "openInterest": 50}, {"strike": 110.0, "openInterest": 200}]
    assert result["supportLevels"] == [{"strike": 90.0, "openInterest": 20}, {"strike": 95.0, "openInterest": 300}]


@patch("app.options_levels.requests.get")
def test_get_options_levels_raises_when_the_response_has_no_parseable_contracts(mock_get):
    mock_get.return_value = _cboe_response(100.0, [])

    with pytest.raises(OptionsLevelsUnavailableError):
        get_options_levels("ZZZZ")


@patch("app.options_levels.requests.get")
def test_get_options_levels_raises_when_the_request_itself_fails(mock_get):
    import requests

    mock_get.side_effect = requests.RequestException("403")

    with pytest.raises(OptionsLevelsUnavailableError):
        get_options_levels("ZZZZ")
