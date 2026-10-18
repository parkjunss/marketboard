from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import json

import numpy as np
import pytest

from app.portfolio_lab import PortfolioLabRequest, calculate, daily_returns, solve_weights


def request(**kwargs):
    return PortfolioLabRequest.model_validate(dict(tickers=['SPY', 'QQQ'], startDate='2024-01-01', endDate='2024-12-31',
        capital=10000, cash=1000, maxWeightPct=80, riskLimitPct=30) | kwargs)


@pytest.fixture
def histories():
    rng = np.random.default_rng(41)
    days = np.busday_offset('2024-01-01', np.arange(260)).astype('datetime64[D]').astype(object)
    return {ticker: [(datetime.combine(day, datetime.min.time(), timezone.utc)+timedelta(hours=16), float(price), float(price))
                      for day, price in zip(days, 100*np.cumprod(1+rng.normal(.0006, sigma, len(days))))]
            for ticker, sigma in [('SPY', .008), ('QQQ', .015)]}


def test_minimum_variance_matches_two_asset_analytic_solution():
    cov = np.array([[.04, .006], [.006, .09]])
    w = solve_weights(np.array([.06, .10]), cov, 1)
    assert w[0] == pytest.approx((.09-.006)/(.04+.09-2*.006), abs=1e-6)
    assert np.sqrt(w@cov@w) < .2


def test_returns_use_actual_endpoints_and_buy_and_hold_weights(histories):
    result = calculate(request(), histories, {})
    start, end = date.fromisoformat(result['commonStart']), date.fromisoformat(result['commonEnd'])
    actual = {}
    for ticker, bars in histories.items():
        prices = {ts.date(): close for ts, _, close in bars}
        actual[ticker] = (prices[end]/prices[start]-1)*100
    for asset in result['assets']:
        assert asset['commonPeriodReturnPct'] == pytest.approx(actual[asset['ticker']])
        assert 'meanReturnPct' not in asset
    for candidate in result['candidates']+result['frontier']:
        expected = sum(row['weightPct']/100*actual[row['ticker']] for row in candidate['allocations'])
        assert candidate['periodReturnPct'] == pytest.approx(expected)
        assert 'sharpe' not in candidate


def test_cumulative_return_includes_price_change_across_omitted_gap(histories):
    del histories['SPY'][80:85]
    test_returns_use_actual_endpoints_and_buy_and_hold_weights(histories)


def test_frontier_allocation_constraints_and_capital_scaling(histories):
    first = calculate(request(), histories, {})
    second = calculate(request(capital=20000, cash=2000), histories, {})
    assert first['status'] == 'READY'
    assert len(first['frontier']) == 21
    for candidate in first['candidates']+first['frontier']:
        assert sum(r['weightPct'] for r in candidate['allocations']) == pytest.approx(90)
        assert all(0 <= r['weightPct'] <= 80+1e-5 for r in candidate['allocations'])
        assert sum(Decimal(str(r['amount'])) for r in candidate['allocations'])+Decimal(str(candidate['cashAmount'])) == 10000
        assert candidate['integerShareCash'] is None
    for a, b in zip(first['frontier'], second['frontier']):
        assert a['volatilityPct'] == pytest.approx(b['volatilityPct'])
    assert all(a['periodReturnPct'] <= b['periodReturnPct']+1e-5 and a['volatilityPct'] <= b['volatilityPct']+1e-5
               for a, b in zip(first['frontier'], first['frontier'][1:]))
    json.dumps(first, allow_nan=False)


def test_missing_asset_keeps_other_asset_metrics(histories):
    del histories['QQQ']
    result = calculate(request(), histories, {})
    assert result['status'] == 'INSUFFICIENT_DATA'
    assert result['assets'][0]['volatilityPct'] is not None
    assert result['assets'][1]['issue'] and not result['candidates']


def test_gap_never_becomes_one_day_return():
    prices = {date(2024, 1, 5):100, date(2024, 1, 8):101, date(2024, 1, 12):110}
    returns = daily_returns(prices)
    assert len(returns) == 1
    assert next(iter(returns.values())) == pytest.approx(.01)


def test_infeasible_cap_and_risk_limit(histories):
    assert calculate(request(maxWeightPct=20), histories, {})['status'] == 'INFEASIBLE'
    result = calculate(request(riskLimitPct=.1), histories, {})
    assert result['status'] == 'READY'
    assert not any(c['id']=='risk-limit' for c in result['candidates'])
    assert any('위험 한도' in w for w in result['warnings'])


def test_singular_data_is_explicit(histories):
    histories['QQQ'] = histories['SPY']
    assert calculate(request(), histories, {})['status'] == 'DEGENERATE_DATA'


def test_cash_only_needs_no_price_history():
    result = calculate(request(cash=10000), {}, {})
    assert result['status'] == 'READY'
    assert result['candidates'][0]['cashAmount'] == 10000
    assert result['candidates'][0]['volatilityPct'] == 0


def test_reference_quantity_never_overspends_and_stale_quotes_are_not_used(histories):
    from app.portfolio_lab import NEW_YORK
    today = datetime.now(NEW_YORK).date()
    ts = datetime.combine(today-timedelta(days=1), datetime.min.time(), timezone.utc)+timedelta(hours=16)
    quotes = {t:[(ts, 123.45, 123.45)] for t in histories}
    result = calculate(request(), histories, quotes)
    for c in result['candidates']+result['frontier']:
        assert all(a['quantity']*123.45 <= a['amount']+.000001 for a in c['allocations'])
        assert sum(a['purchaseAmount'] for a in c['allocations'])+c['integerShareCash'] == pytest.approx(10000)
    quotes = {t:[(ts-timedelta(days=6), 123.45, 123.45)] for t in histories}
    result = calculate(request(), histories, quotes)
    assert result['candidates'][0]['integerShareCash'] is None


@pytest.mark.parametrize('kwargs', [{'cash':10001}, {'tickers':['SPY','SPY']}, {'capital':float('nan')},
    {'tickers':['SPY','bad/ticker']}, {'endDate':'2100-01-01'}])
def test_invalid_requests(kwargs):
    with pytest.raises(ValueError): request(**kwargs)
