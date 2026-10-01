# Backtest workbench design QA

- Source visual truth:
  - User-provided backtest desktop reference 1 (1457 x 1046)
  - User-provided backtest desktop reference 2 (1435 x 524)
- Implementation: `http://127.0.0.1:13100/backtest`
- Intended viewport: desktop, dark theme
- Implementation screenshot: unavailable because the browser session redirects to `/login`
- Density normalization: not applicable; implementation capture unavailable
- State: unauthenticated browser session

## Full-view comparison evidence

Blocked. The source images were opened at original resolution, but the authenticated implementation could not be captured in the available browser session.

## Focused region comparison evidence

Blocked for the same authentication reason. Code-level checks confirm the intended order exists: strategy settings, candlestick/SMA chart, attached equity curve, then comparison/history tabs.

## Findings

- No browser-rendered P0/P1/P2 visual findings can be asserted without an authenticated capture.
- Build and lint checks passed, but they do not substitute for visual QA.

## Comparison history

- Initial implementation: browser capture blocked by authentication; no visual iteration claimed.
- Layout revision: strategy settings moved above the analysis workbench, the equity curve was attached below the candlestick chart, and comparison/history remain as the only result tabs. Browser comparison remains blocked by authentication.

## Follow-up polish

- Verify desktop proportions and chart height with populated data.
- Verify the 780px responsive transition and horizontal candle-chart behavior.

final result: blocked
