import json
import logging

from .redis_publisher import get_client

logger = logging.getLogger("collector.alerts")


async def check_alerts(symbol: str, price: float) -> None:
    """Check active alerts for `symbol` against `price`; publish + clear any that cross their
    target. Called on every raw tick (not the throttled/published one) so a fast crossing
    isn't missed between throttled publishes."""
    client = get_client()
    alert_ids = await client.smembers(f"alerts:{symbol}")
    if not alert_ids:
        return

    for alert_id in alert_ids:
        details = await client.hgetall(f"alert:{alert_id}")
        if not details:
            # Stale set member left behind by a race with deletion — clean it up.
            await client.srem(f"alerts:{symbol}", alert_id)
            continue

        condition = details.get("condition")
        target_price = float(details.get("targetPrice", "0"))
        user_id = details.get("userId")

        triggered = (condition == "ABOVE" and price >= target_price) or (
            condition == "BELOW" and price <= target_price
        )
        if not triggered:
            continue

        # One-shot: remove from the mirror before publishing so a burst of ticks can't re-fire it
        # while the backend is still processing the first trigger.
        await client.srem(f"alerts:{symbol}", alert_id)
        await client.delete(f"alert:{alert_id}")

        payload = {
            "alertId": int(alert_id),
            "userId": int(user_id),
            "symbol": symbol,
            "condition": condition,
            "targetPrice": target_price,
            "price": price,
        }
        await client.publish("alert-triggers", json.dumps(payload))
        logger.info(
            "Alert %s triggered for %s (%s %.4f, price=%.4f)", alert_id, symbol, condition, target_price, price
        )
