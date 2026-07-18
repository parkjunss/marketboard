"""Real fundamentals (income statement, balance sheet, cash flow, and derived ratios) via yfinance.

`.info` only gives the latest snapshot of things like P/E or quick ratio; the reference dashboard
this module feeds shows multi-year trends, so most ratios here are computed per fiscal year from
the raw annual statements instead of pulled pre-computed.
"""

import math

import yfinance as yf


def _safe_float(value) -> float | None:
    if value is None:
        return None
    try:
        f = float(value)
    except (TypeError, ValueError):
        return None
    return None if math.isnan(f) else f


def _row(df, label: str) -> dict[int, float | None]:
    """Returns {fiscal_year: value} for a statement row; {} if the row isn't present."""
    if df.empty or label not in df.index:
        return {}
    return {column.year: _safe_float(value) for column, value in df.loc[label].items()}


def _price_near(price_history, target_ts) -> float | None:
    if price_history.empty:
        return None
    aligned_target = target_ts.tz_localize(price_history.index.tz) if target_ts.tzinfo is None else target_ts
    idx = price_history.index.asof(aligned_target)
    if idx is None or (isinstance(idx, float) and math.isnan(idx)):
        return None
    return _safe_float(price_history.loc[idx])


def get_financials(ticker: str) -> dict:
    t = yf.Ticker(ticker)
    income = t.financials
    balance = t.balance_sheet
    cashflow = t.cashflow
    info = t.info

    year_columns = {column.year: column for column in income.columns} if not income.empty else {}
    revenue = _row(income, "Total Revenue")
    # yfinance's oldest column is often entirely unpopulated (a boundary artifact of the
    # annual-statement window) — drop years with no revenue rather than show a dangling null point.
    years = sorted(y for y in year_columns if revenue.get(y) is not None)

    gross_profit = _row(income, "Gross Profit")
    operating_income = _row(income, "Operating Income")
    net_income = _row(income, "Net Income")
    diluted_eps = _row(income, "Diluted EPS")
    diluted_shares = _row(income, "Diluted Average Shares")
    ebit = _row(income, "EBIT")
    interest_expense = _row(income, "Interest Expense")

    total_assets = _row(balance, "Total Assets")
    stockholders_equity = _row(balance, "Stockholders Equity")
    shares_outstanding = _row(balance, "Ordinary Shares Number")

    operating_cash_flow = _row(cashflow, "Operating Cash Flow")
    free_cash_flow = _row(cashflow, "Free Cash Flow")

    price_history = t.history(period="6y", interval="1d")["Close"]

    earnings_analysis = [
        {
            "year": y,
            "revenue": revenue.get(y),
            "grossProfit": gross_profit.get(y),
            "operatingIncome": operating_income.get(y),
            "netIncome": net_income.get(y),
        }
        for y in years
    ]

    def pct_change(series: dict[int, float | None], year: int, prev_year: int) -> float | None:
        current, previous = series.get(year), series.get(prev_year)
        if current is None or not previous:
            return None
        return (current - previous) / abs(previous) * 100

    growth_analysis = [
        {
            "year": years[i],
            "revenueGrowthPct": pct_change(revenue, years[i], years[i - 1]),
            "operatingIncomeGrowthPct": pct_change(operating_income, years[i], years[i - 1]),
        }
        for i in range(1, len(years))
    ]

    def ratio_pct(numerator: float | None, denominator: float | None) -> float | None:
        return (numerator / denominator * 100) if numerator is not None and denominator else None

    profitability_analysis = [
        {
            "year": y,
            "roePct": ratio_pct(net_income.get(y), stockholders_equity.get(y)),
            "roaPct": ratio_pct(net_income.get(y), total_assets.get(y)),
        }
        for y in years
    ]

    cash_flow_analysis = [
        {"year": y, "operatingCashFlow": operating_cash_flow.get(y), "freeCashFlow": free_cash_flow.get(y)}
        for y in years
    ]

    margins_analysis = [
        {
            "year": y,
            "grossMarginPct": ratio_pct(gross_profit.get(y), revenue.get(y)),
            "operatingMarginPct": ratio_pct(operating_income.get(y), revenue.get(y)),
            "netMarginPct": ratio_pct(net_income.get(y), revenue.get(y)),
        }
        for y in years
    ]

    market_analysis = []
    for y in years:
        price = _price_near(price_history, year_columns[y])
        eps = diluted_eps.get(y)
        equity, shares = stockholders_equity.get(y), shares_outstanding.get(y)
        rev, dshares = revenue.get(y), diluted_shares.get(y)
        book_value_per_share = (equity / shares) if equity is not None and shares else None
        sales_per_share = (rev / dshares) if rev is not None and dshares else None
        market_analysis.append(
            {
                "year": y,
                "peRatio": (price / eps) if price and eps else None,
                "pbRatio": (price / book_value_per_share) if price and book_value_per_share else None,
                "psRatio": (price / sales_per_share) if price and sales_per_share else None,
            }
        )

    total_debt = _safe_float(info.get("totalDebt"))
    total_cash = _safe_float(info.get("totalCash"))
    latest_year = years[-1] if years else None
    latest_equity = stockholders_equity.get(latest_year) if latest_year else None
    latest_fcf = free_cash_flow.get(latest_year) if latest_year else None
    latest_ebit = ebit.get(latest_year) if latest_year else None
    latest_interest_expense = interest_expense.get(latest_year) if latest_year else None

    kpis = {
        "quickRatio": _safe_float(info.get("quickRatio")),
        "currentRatio": _safe_float(info.get("currentRatio")),
        "debtToCapitalPct": (
            (total_debt / (total_debt + latest_equity) * 100)
            if total_debt is not None and latest_equity
            else None
        ),
        "totalDebtToFcf": (total_debt / latest_fcf) if total_debt is not None and latest_fcf else None,
        "cashAndEquivalents": total_cash,
        "interestCoverage": (
            (latest_ebit / latest_interest_expense)
            if latest_ebit is not None and latest_interest_expense
            else None
        ),
    }

    return {
        "ticker": ticker.upper(),
        "name": info.get("longName") or info.get("shortName") or ticker.upper(),
        "years": years,
        "earningsAnalysis": earnings_analysis,
        "growthAnalysis": growth_analysis,
        "profitabilityAnalysis": profitability_analysis,
        "cashFlowAnalysis": cash_flow_analysis,
        "marginsAnalysis": margins_analysis,
        "marketAnalysis": market_analysis,
        "kpis": kpis,
    }
