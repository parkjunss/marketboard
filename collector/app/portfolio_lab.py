"""Read-only, long-only portfolio design. All returns are historical estimates."""
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, ROUND_FLOOR
import re

import numpy as np
from pydantic import BaseModel, Field, field_validator, model_validator
from scipy.optimize import minimize, linprog

from .etf_momentum import NEW_YORK, load_daily_bars


class PortfolioLabRequest(BaseModel):
    tickers: list[str] = Field(min_length=2, max_length=10)
    startDate: date
    endDate: date
    capital: float = Field(ge=1, le=1_000_000_000, allow_inf_nan=False)
    cash: float = Field(ge=0, le=1_000_000_000, allow_inf_nan=False)
    maxWeightPct: float = Field(gt=0, le=100, allow_inf_nan=False)
    riskFreePct: float = Field(ge=0, le=20, allow_inf_nan=False)
    riskLimitPct: float = Field(gt=0, le=200, allow_inf_nan=False)

    @field_validator('tickers')
    @classmethod
    def normalize(cls, values):
        values = list(dict.fromkeys(v.strip().upper() for v in values))
        if not 2 <= len(values) <= 10 or any(not re.fullmatch(r'[A-Z][A-Z0-9.\-]{0,14}', v) for v in values):
            raise ValueError('서로 다른 주식·ETF 티커를 2~10개 입력하세요.')
        return values

    @model_validator(mode='after')
    def validate_conditions(self):
        if self.startDate.year < 1991 or not 60 <= (self.endDate-self.startDate).days <= 3660:
            raise ValueError('분석 기간은 1991년 이후, 60일~10년이어야 합니다.')
        if self.endDate >= datetime.now(NEW_YORK).date():
            raise ValueError('종료일은 뉴욕 기준 오늘 이전이어야 합니다.')
        if self.cash > self.capital:
            raise ValueError('남길 현금이 투자금보다 큽니다.')
        return self


def observed_prices(rows, start, end):
    prices = {}
    for ts, _, close in rows:
        if not isinstance(ts, datetime):
            raise ValueError('유효하지 않은 일봉 날짜')
        day = (ts.replace(tzinfo=timezone.utc) if ts.tzinfo is None else ts).astimezone(NEW_YORK).date()
        if not start <= day <= end:
            continue
        value = float(close)
        if day in prices or not np.isfinite(value) or value <= 0:
            raise ValueError('중복 날짜 또는 유효하지 않은 종가')
        prices[day] = value
    return dict(sorted(prices.items()))


def daily_returns(prices):
    days = list(prices)
    # shortcut: conservatively omit weekday holiday intervals until exchange calendars are recorded.
    return {(a, b): prices[b]/prices[a]-1 for a, b in zip(days, days[1:])
            if np.busday_count(a, b) == 1 and a.weekday() < 5 and b.weekday() < 5}


def solve_weights(mu, covariance, cap, target=None, risk_limit=None):
    n = len(mu)
    constraints = [{'type': 'eq', 'fun': lambda w: w.sum()-1, 'jac': lambda w: np.ones(n)}]
    if target is not None:
        constraints.append({'type': 'ineq', 'fun': lambda w: w@mu-target, 'jac': lambda w: mu})
    if risk_limit is not None:
        constraints.append({'type': 'ineq', 'fun': lambda w: risk_limit**2-w@covariance@w,
                            'jac': lambda w: -2*covariance@w})
    objective = (lambda w: -w@mu) if risk_limit is not None else (lambda w: w@covariance@w)
    jac = (lambda w: -mu) if risk_limit is not None else (lambda w: 2*covariance@w)
    result = minimize(objective, np.full(n, 1/n), jac=jac, bounds=[(0, cap)]*n,
                      constraints=constraints, method='SLSQP', options={'ftol': 1e-12, 'maxiter': 500})
    w = result.x
    if (not result.success or not np.isfinite(w).all() or abs(w.sum()-1) > 1e-7
            or w.min() < -1e-8 or w.max() > cap+1e-7
            or (target is not None and w@mu < target-1e-7)
            or (risk_limit is not None and w@covariance@w > risk_limit**2+1e-7)):
        raise ValueError('최적화가 제약을 만족하는 해로 수렴하지 않았습니다.')
    return w


def calculate(request, histories, latest):
    p = request
    assets, returns, prices = [], {}, {}
    warnings = ['과거 평균·공분산의 표본 내 추정치이며 미래 수익 예측이 아닙니다.',
                '가격 조정 일관성은 미확인입니다. 결측 보간 없이 동일 구간만 사용합니다.',
                '휴장일 캘린더 대신 평일 간격을 보수적으로 검사해 휴일을 낀 구간도 제외할 수 있습니다.',
                '고정 현금 수익률은 0%이며 무위험 수익률 입력은 샤프 비교에만 사용합니다. 거래비용·세금은 제외합니다.']
    for ticker in p.tickers:
        row = dict(ticker=ticker, observations=0, returnObservations=0, startDate=None, endDate=None,
                   totalReturnPct=None, meanReturnPct=None, volatilityPct=None, maxDrawdownPct=None,
                   price=None, priceDate=None, issue=None, commonMeanReturnPct=None, commonVolatilityPct=None)
        assets.append(row)
        try:
            values = observed_prices(histories.get(ticker, []), p.startDate, p.endDate)
            quotes = observed_prices(latest.get(ticker, []), datetime.now(NEW_YORK).date()-timedelta(days=14),
                                     datetime.now(NEW_YORK).date()-timedelta(days=1))
        except (ValueError, TypeError, OverflowError) as exc:
            row['issue'] = str(exc)
            continue
        if quotes:
            day = next(reversed(quotes))
            row.update(price=quotes[day], priceDate=day.isoformat())
        if not values:
            row['issue'] = '선택 기간의 일봉이 없습니다. 종목 등록·기간·수집 상태를 확인하세요.'
            continue
        prices[ticker] = values
        returns[ticker] = daily_returns(values)
        a = np.array(list(values.values()))
        r = np.array(list(returns[ticker].values()))
        if not np.isfinite(r).all() or not np.isfinite(a[-1]/a[0]):
            row['issue'] = '계산 가능한 수익률 범위를 초과했습니다.'
            continue
        row.update(observations=len(a), returnObservations=len(r), startDate=min(values).isoformat(),
                   endDate=max(values).isoformat(), totalReturnPct=float((a[-1]/a[0]-1)*100),
                   maxDrawdownPct=float(np.min(a/np.maximum.accumulate(a)-1)*100))
        if len(r) >= 2:
            row.update(meanReturnPct=float(r.mean()*25200), volatilityPct=float(r.std(ddof=1)*np.sqrt(252)*100))
        if len(r) < 60:
            row['issue'] = '최적화에 필요한 유효 일간 수익률 60개 미만'
    result = dict(status='INSUFFICIENT_DATA', message=None, configuration=p.model_dump(mode='json'),
                  assets=assets, commonObservations=0, excludedIntervals=0, commonStart=None, commonEnd=None,
                  correlation=None, candidates=[], frontier=[], warnings=warnings, calculationVersion='portfolio-lab-v1')
    if p.cash == p.capital:
        result.update(status='READY', message='전액 현금 조건입니다. 위험자산 최적화는 수행하지 않았습니다.', candidates=[dict(
            id='cash', label='전액 현금', meanReturnPct=0, volatilityPct=0, sharpe=None, allocations=[],
            cashAmount=float(Decimal(str(p.capital)).quantize(Decimal('.01'), rounding=ROUND_FLOOR)),
            integerShareCash=float(Decimal(str(p.capital)).quantize(Decimal('.01'), rounding=ROUND_FLOOR)))])
        return result
    if any(a['issue'] for a in assets):
        result['message'] = '개별 지표는 표시했습니다. 데이터 부족 종목의 기간을 조정하거나 직접 제외해 주세요.'
        return result
    common = sorted(set.intersection(*(set(returns[t]) for t in p.tickers)))
    result.update(commonObservations=len(common), excludedIntervals=len(set.union(*(set(returns[t]) for t in p.tickers)))-len(common))
    if len(common) < 60:
        result['message'] = '동일 시작·종료일을 가진 공통 일간 수익률이 60개 미만입니다.'
        return result
    result.update(commonStart=common[0][0].isoformat(), commonEnd=common[-1][1].isoformat())
    matrix = np.array([[returns[t][interval] for t in p.tickers] for interval in common])
    mu, covariance = matrix.mean(axis=0)*252, np.cov(matrix, rowvar=False)*252
    if not np.isfinite(mu).all() or not np.isfinite(covariance).all():
        result.update(status='DEGENERATE_DATA', message='수익률 또는 공분산의 숫자 범위를 초과했습니다.')
        return result
    std = np.sqrt(np.diag(covariance))
    for index, asset in enumerate(assets):
        asset.update(commonMeanReturnPct=float(mu[index]*100), commonVolatilityPct=float(std[index]*100))
    result['correlation'] = [[float(covariance[i,j]/(std[i]*std[j])) if std[i]*std[j] > 0 else None
                              for j in range(len(mu))] for i in range(len(mu))]
    fraction = (p.capital-p.cash)/p.capital
    if fraction > 0 and len(mu)*p.maxWeightPct/100 < fraction-1e-10:
        result.update(status='INFEASIBLE', message='종목별 최대 비중의 합이 투자할 비중보다 작습니다. 상한·현금·종목 수를 조정하세요.')
        return result
    def candidate(w, label, key):
        weights = w*fraction
        ret, vol = float(weights@mu), float(np.sqrt(max(0, weights@covariance@weights)))
        rows = []
        total = Decimal(str(p.capital)).quantize(Decimal('.01'), rounding=ROUND_FLOOR)
        for asset, weight in zip(assets, weights):
            amount = (total*Decimal(str(max(0, float(weight))))).quantize(Decimal('.01'), rounding=ROUND_FLOOR)
            quote = asset['price']
            fresh = asset['priceDate'] and (datetime.now(NEW_YORK).date()-date.fromisoformat(asset['priceDate'])).days <= 4
            quantity = int(amount/Decimal(str(quote))) if quote and fresh else None
            rows.append(dict(ticker=asset['ticker'], weightPct=float(weight*100), amount=float(amount),
                             price=quote, priceDate=asset['priceDate'], quantity=quantity,
                             purchaseAmount=float(Decimal(str(quote))*quantity) if quantity is not None else None))
        cash = total-sum(Decimal(str(row['amount'])) for row in rows)
        remainder = total-sum(Decimal(str(row['purchaseAmount'])) for row in rows) if all(r['quantity'] is not None for r in rows) else None
        return dict(id=key, label=label, meanReturnPct=ret*100, volatilityPct=vol*100,
                    sharpe=(ret-p.riskFreePct/100)/vol if vol > 1e-12 else None, allocations=rows,
                    cashAmount=float(cash), integerShareCash=float(remainder) if remainder is not None else None)
    cap = min(1, p.maxWeightPct/100/fraction)
    if np.linalg.eigvalsh(covariance).min() <= 1e-12:
        result.update(status='DEGENERATE_DATA', message='공분산이 특이하거나 변동성이 거의 없습니다. 중복 노출 자산이나 분석 기간을 조정하세요.')
        return result
    try:
        minimum = solve_weights(mu, covariance, cap)
        result['candidates'] = [candidate(np.full(len(mu), 1/len(mu)), '동일 비중', 'equal'), candidate(minimum, '최소 변동성', 'minimum')]
        maximum = linprog(-mu, A_eq=[np.ones(len(mu))], b_eq=[1], bounds=[(0, cap)]*len(mu), method='highs')
        if not maximum.success:
            raise ValueError('가능한 수익률 상한을 계산하지 못했습니다.')
        targets = np.linspace(minimum@mu, maximum.x@mu, 21)
        result['frontier'] = [candidate(solve_weights(mu, covariance, cap, float(target)), f'투자선 {i+1}', f'frontier-{i}') for i, target in enumerate(targets)]
        if np.sqrt(minimum@covariance@minimum)*fraction <= p.riskLimitPct/100+1e-9:
            selected = solve_weights(mu, covariance, cap, risk_limit=p.riskLimitPct/100/fraction)
            result['candidates'].append(candidate(selected, '위험 한도 내 수익 추정치 최대', 'risk-limit'))
        else:
            warnings.append('설정한 위험 한도를 만족하는 조합이 없습니다. 최소 변동성 후보를 비교하세요.')
    except ValueError as exc:
        result.update(status='SOLVER_FAILED', message=str(exc), frontier=[])
        return result
    result.update(status='READY', message='과거 자료 기반 배분안입니다. 후보를 선택해 금액과 비중을 비교하세요.')
    return result


def run_portfolio_lab(payload):
    request = PortfolioLabRequest.model_validate(payload)
    today = datetime.now(NEW_YORK).date()
    histories = load_daily_bars(request.startDate, request.endDate, request.tickers)
    latest = load_daily_bars(today-timedelta(days=14), today-timedelta(days=1), request.tickers)
    return calculate(request, histories, latest)
