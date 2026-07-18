import os

from dotenv import load_dotenv

load_dotenv()

FINNHUB_API_KEY = os.environ["FINNHUB_API_KEY"]
FINNHUB_WS_URL = f"wss://ws.finnhub.io?token={FINNHUB_API_KEY}"

# "localhost" makes redis.asyncio hang indefinitely on this Windows/Docker Desktop
# setup (IPv6 resolution never fails over to IPv4) — default to the literal IPv4 address.
REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))

MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "stockmonitordb")
MYSQL_USER = os.getenv("MYSQL_USER", "stockmonitor")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "stockmonitor1234")

DEFAULT_SYMBOLS = [
    s.strip().upper()
    for s in os.getenv("DEFAULT_SYMBOLS", "AAPL,MSFT,GOOGL,TSLA,NVDA").split(",")
    if s.strip()
]

TICK_THROTTLE_SECONDS = float(os.getenv("TICK_THROTTLE_SECONDS", "1.0"))
REST_FALLBACK_POLL_SECONDS = float(os.getenv("REST_FALLBACK_POLL_SECONDS", "60"))
REST_FALLBACK_STALE_AFTER_SECONDS = float(os.getenv("REST_FALLBACK_STALE_AFTER_SECONDS", "90"))

SP500_BATCH_INTERVAL_SECONDS = float(os.getenv("SP500_BATCH_INTERVAL_SECONDS", str(24 * 60 * 60)))
# Unset/blank = full S&P 500 universe. Set to a small number (e.g. "20") to verify the batch
# job cheaply before letting it run against all ~500 constituents.
_sp500_limit_raw = os.getenv("SP500_BATCH_LIMIT", "").strip()
SP500_BATCH_LIMIT = int(_sp500_limit_raw) if _sp500_limit_raw else None

# Keeps the currently-active (is_active=TRUE) symbols' daily bars current -- independent of
# S&P 500 membership, since index ETF proxies (SPY/QQQ/DIA) aren't S&P 500 constituents and would
# never get refreshed by the S&P 500 batch otherwise.
ACTIVE_SYMBOLS_REFRESH_INTERVAL_SECONDS = float(os.getenv("ACTIVE_SYMBOLS_REFRESH_INTERVAL_SECONDS", str(24 * 60 * 60)))
