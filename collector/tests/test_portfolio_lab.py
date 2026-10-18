from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import json

import numpy as np
import pytest

from app.portfolio_lab import PortfolioLabRequest, calculate, daily_returns, solve_weights, performance_summary


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
    assert result['performance'] is None


def test_performance_matches_buy_and_hold_and_cash(histories):
    result = calculate(request(), histories, {})
    comparison = result['performance']
    days = comparison['dates']
    prices = {ticker: {ts.date().isoformat(): close for ts, _, close in bars} for ticker, bars in histories.items()}
    for candidate in result['candidates']+result['frontier']:
        history = candidate['performance']
        assert len(history['values']) == len(days)
        assert history['values'][0] == pytest.approx(10000)
        for i in [1, len(days)//2, len(days)-1]:
            expected = 1000 + sum(10000 * a['weightPct']/100 * prices[a['ticker']][days[i]]/prices[a['ticker']][days[0]]
                                  for a in candidate['allocations'])
            assert history['values'][i] == pytest.approx(expected)
        assert history['periodReturnPct'] == pytest.approx(candidate['periodReturnPct'])
    spy = comparison['benchmark']
    assert spy['values'][0] == 10000
    assert spy['endValue'] == pytest.approx(1000+9000*prices['SPY'][days[-1]]/prices['SPY'][days[0]])


def test_history_gap_is_omitted_without_losing_endpoint_change(histories):
    missing_day = histories['QQQ'][80][0].date().isoformat()
    del histories['QQQ'][80]
    result = calculate(request(), histories, {})
    assert missing_day not in result['performance']['dates']
    test_performance_matches_buy_and_hold_and_cash(histories)


def test_benchmark_missing_does_not_block_portfolio(histories):
    histories['EFA'] = histories.pop('SPY')
    result = calculate(request(tickers=['EFA', 'QQQ']), histories, {})
    assert result['status'] == 'READY'
    assert result['performance']['benchmark'] is None
    assert result['performance']['benchmarkIssue']


def test_runner_loads_spy_without_adding_it_to_candidate_universe(monkeypatch, histories):
    from app import portfolio_lab
    histories['EFA'] = histories['SPY']
    calls = []
    def load(start, end, tickers):
        calls.append(tickers)
        return histories
    monkeypatch.setattr(portfolio_lab, 'load_daily_bars', load)
    result = portfolio_lab.run_portfolio_lab(request(tickers=['EFA', 'QQQ']).model_dump(mode='json'))
    assert calls[0] == ['EFA', 'QQQ', 'SPY']
    assert calls[1] == ['EFA', 'QQQ']
    assert [a['ticker'] for a in result['assets']] == ['EFA', 'QQQ']
    assert result['performance']['benchmark'] is not None


def test_benchmark_missing_endpoint_is_explicit(histories):
    histories['EFA'] = histories['SPY']
    histories['SPY'] = histories['EFA'][1:]
    result = calculate(request(tickers=['EFA', 'QQQ']), histories, {})
    assert result['performance']['benchmark'] is None
    assert result['performance']['benchmarkIssue']


@pytest.mark.parametrize('values, drawdown, recovery_days, underwater', [
    ([100, 120, 90, 110, 120], -25, 3, 0),
    ([100, 120, 90, 100, 119], -25, None, 3),
    ([100, 100, 100, 100, 100], 0, 0, 0),
    ([100, 110, 120, 130, 140], 0, 0, 0),
    ([100, 90, 100, 80, 101], -20, 2, 0),
])
def test_drawdown_and_calendar_recovery(values, drawdown, recovery_days, underwater):
    days = [date(2024, 1, 1)+timedelta(days=i) for i in range(len(values))]
    result = performance_summary(days, values)
    assert result['maxDrawdownPct'] == pytest.approx(drawdown)
    assert result['recoveryDays'] == recovery_days
    assert result['underwaterDays'] == underwater


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


@pytest.fixture
def holdout_histories(histories):
    days = [date(2025, 1, 2), date(2025, 1, 3), date(2025, 1, 6), date(2025, 3, 31)]
    for ticker, values in [('SPY', [100, 90, 110, 120]), ('QQQ', [200, 220, 210, 180])]:
        histories[ticker] += [(datetime.combine(d, datetime.min.time(), timezone.utc)+timedelta(hours=16), v, v)
                              for d, v in zip(days, values)]
    return histories


def holdout_request(**kwargs):
    return request(validationStartDate='2025-01-01', validationEndDate='2025-03-31', **kwargs)


def test_holdout_does_not_change_training_weights_or_performance(holdout_histories):
    baseline = calculate(request(), holdout_histories, {})
    result = calculate(holdout_request(), holdout_histories, {})
    assert result['candidates'] == baseline['candidates']
    assert result['frontier'] == baseline['frontier']
    original = result['validation']['candidates'][0]['performance']['endValue']
    ts, op, close = holdout_histories['QQQ'][-1]
    holdout_histories['QQQ'][-1] = (ts, op, close*4)
    changed = calculate(holdout_request(), holdout_histories, {})
    assert changed['candidates'] == result['candidates']
    assert changed['frontier'] == result['frontier']
    assert changed['validation']['candidates'][0]['performance']['endValue'] != original


def test_holdout_fixed_weights_cash_and_observed_dates(holdout_histories):
    result = calculate(holdout_request(), holdout_histories, {})
    validation = result['validation']
    assert validation['status'] == 'READY'
    assert validation['comparison']['dates'] == ['2025-01-02', '2025-01-03', '2025-01-06', '2025-03-31']
    assert validation['excludedDates'] == 0  # weekends are not missing data
    for c, v in zip(result['candidates']+result['frontier'], validation['candidates']):
        assert c['id'] == v['id']
        w = [a['weightPct']/100 for a in c['allocations']]
        assert v['performance']['values'][0] == pytest.approx(10000)
        assert v['performance']['endValue'] == pytest.approx(1000+10000*(w[0]*1.2+w[1]*.9))
    assert validation['comparison']['benchmark']['endValue'] == pytest.approx(11800)
    json.dumps(result, allow_nan=False)


def test_holdout_gap_is_omitted_and_missing_asset_preserves_training(holdout_histories):
    del holdout_histories['QQQ'][-3]
    result = calculate(holdout_request(), holdout_histories, {})
    assert result['validation']['observations'] == 3
    assert result['validation']['excludedDates'] == 1
    assert '2025-01-03' not in result['validation']['comparison']['dates']
    holdout_histories['QQQ'] = [r for r in holdout_histories['QQQ'] if r[0].year == 2024]
    result = calculate(holdout_request(), holdout_histories, {})
    assert result['status'] == 'READY' and result['candidates']
    assert result['validation']['status'] == 'INSUFFICIENT_DATA'
    assert result['validation']['assets'][1]['issue']
    assert result['validation']['comparison'] is None


def test_invalid_holdout_price_does_not_erase_training(holdout_histories):
    holdout_histories['QQQ'].append(holdout_histories['QQQ'][-1])
    result = calculate(holdout_request(), holdout_histories, {})
    assert result['status'] == 'READY'
    assert result['validation']['status'] == 'INSUFFICIENT_DATA'
    assert result['validation']['assets'][1]['issue']


def test_holdout_spy_missing_is_independent(holdout_histories):
    holdout_histories['EFA'] = holdout_histories.pop('SPY')
    result = calculate(holdout_request(tickers=['EFA', 'QQQ']), holdout_histories, {})
    assert result['validation']['status'] == 'READY'
    assert result['validation']['comparison']['benchmark'] is None
    assert result['validation']['comparison']['benchmarkIssue']


@pytest.mark.parametrize('dates', [
    {'validationStartDate':'2025-01-01'},
    {'validationEndDate':'2025-03-31'},
    {'validationStartDate':'2024-12-31', 'validationEndDate':'2025-03-31'},
    {'validationStartDate':'2025-03-31', 'validationEndDate':'2025-03-31'},
    {'validationStartDate':'2025-03-31', 'validationEndDate':'2025-01-01'},
    {'validationStartDate':'2025-01-01', 'validationEndDate':'2100-01-01'},
])
def test_invalid_holdout_periods(dates):
    with pytest.raises(ValueError): request(**dates)


def test_runner_loads_through_holdout_end(monkeypatch, holdout_histories):
    from app import portfolio_lab
    calls = []
    def load(start, end, tickers):
        calls.append((start, end))
        return holdout_histories
    monkeypatch.setattr(portfolio_lab, 'load_daily_bars', load)
    result = portfolio_lab.run_portfolio_lab(holdout_request().model_dump(mode='json'))
    assert calls[0] == (date(2024, 1, 1), date(2025, 3, 31))
    assert result['validation']['status'] == 'READY'


def test_holdout_unavailable_for_cash_or_failed_training(histories):
    cash = calculate(holdout_request(cash=10000), {}, {})
    assert cash['validation']['status'] == 'UNAVAILABLE'
    failed = calculate(holdout_request(), {}, {})
    assert failed['validation']['status'] == 'UNAVAILABLE'
