import os

from dotenv import load_dotenv

load_dotenv()

FINNHUB_API_KEY = os.environ["FINNHUB_API_KEY"]
FINNHUB_WS_URL = f"wss://ws.finnhub.io?token={FINNHUB_API_KEY}"

# Local development uses the isolated Redis from compose.local.yml. The deployment
# compose file explicitly supplies redis:6379, so this host-only default cannot leak there.
REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "16380"))

MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "13308"))
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "stockmonitordb")
MYSQL_USER = os.getenv("MYSQL_USER", "stockmonitor")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "marketboard-local")

DEFAULT_SYMBOLS = [
    s.strip().upper()
    for s in os.getenv("DEFAULT_SYMBOLS", "AAPL,MSFT,GOOGL,TSLA,NVDA").split(",")
    if s.strip()
]

REALTIME_SYMBOL_LIMIT = int(os.getenv("REALTIME_SYMBOL_LIMIT", "30"))
if REALTIME_SYMBOL_LIMIT < 1:
    raise ValueError("REALTIME_SYMBOL_LIMIT must be at least 1")

TICK_THROTTLE_SECONDS = float(os.getenv("TICK_THROTTLE_SECONDS", "1.0"))
REST_FALLBACK_POLL_SECONDS = float(os.getenv("REST_FALLBACK_POLL_SECONDS", "60"))
REST_FALLBACK_STALE_AFTER_SECONDS = float(os.getenv("REST_FALLBACK_STALE_AFTER_SECONDS", "90"))

SP500_BATCH_PERIOD = os.getenv("SP500_BATCH_PERIOD", "2y")
DAILY_BATCH_HOUR_UTC = int(os.getenv("DAILY_BATCH_HOUR_UTC", "6"))
if not 0 <= DAILY_BATCH_HOUR_UTC <= 23:
    raise ValueError("DAILY_BATCH_HOUR_UTC must be between 0 and 23")
# Unset/blank = full S&P 500 universe. Set to a small number (e.g. "20") to verify the batch
# job cheaply before letting it run against all ~500 constituents.
_sp500_limit_raw = os.getenv("SP500_BATCH_LIMIT", "").strip()
SP500_BATCH_LIMIT = int(_sp500_limit_raw) if _sp500_limit_raw else None
