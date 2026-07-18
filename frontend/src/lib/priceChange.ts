// When markets are closed (weekend/holiday/pre-open), the live quote feed just echoes the last
// trade -- which is also the most recent stored daily bar's close. Comparing "live price" against
// "latest daily close" in that situation compares a value to itself and always yields 0. Detect
// that case (live price ~= latest close) and fall back to the prior session's close instead, so
// "전일대비" shows how the last actual trading day performed rather than a flat zero. During real
// trading hours the live price differs from yesterday's close by more than this epsilon, so the
// normal (and correct) live-vs-yesterday comparison is used as-is.
const SAME_CLOSE_EPSILON = 0.005;

/**
 * `closes` must be ascending by date (oldest first), matching CandleResponse[] order from the API.
 */
export function resolvePrevClose(closes: number[], livePrice: number | null): number | null {
  if (livePrice == null || closes.length === 0) return null;
  const latest = closes[closes.length - 1];
  if (closes.length > 1 && Math.abs(livePrice - latest) < SAME_CLOSE_EPSILON) {
    return closes[closes.length - 2];
  }
  return latest;
}
