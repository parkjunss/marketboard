"""Local-only UI fixture: indices fail once, news times out once, retry succeeds."""
import json
import time
import sys
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock

counts = Counter()
lock = Lock()
normal = '--normal' in sys.argv

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split('?')[0]
        with lock:
            counts[path] += 1
            count = counts[path]
        status, data = 200, []
        if path == '/api/market-indices':
            if count == 1 and not normal:
                status, data = 503, {'message': 'Local fixture failure'}
            else:
                data = [{'slug': 'SPX', 'name': '검증용 지수 (가상 데이터)'}]
        elif path == '/api/news' and count == 1 and not normal:
            time.sleep(12)
        elif normal and path == '/api/market-breadth':
            data = dict(snapshotDate='2026-10-22', computedAt='2026-10-23T00:10:00Z',
                        advancingCount=10, decliningCount=5, unchangedCount=1,
                        new52wHighCount=2, new52wLowCount=0, universeSize=16)
        elif normal and path == '/api/market-sentiment/fear-greed':
            data = dict(score=31, rating='fear', timestamp='2026-10-22T16:30:00Z',
                        history={'1w': 27, '1m': 34}, indicators={})
        elif normal and path == '/api/market-sentiment/put-call-ratio':
            data = dict(ticker='SPY', expirationsUsed=8, callVolume=100, putVolume=107, putCallRatio=1.07)
        elif normal and path.startswith('/api/quotes') and not path.endswith('/history'):
            data = [dict(symbol='NVDA', name='검증용 가상 종목', price=102, volume=1000, ts='2026-10-22T16:30:00Z')]
        elif path.startswith('/api/market-sentiment/') or path == '/api/market-breadth':
            data = None
        elif path.endswith('/history'):
            data = [dict(ts=f'2026-10-{day:02d}T00:00:00Z', open=100, high=103,
                         low=99, close=101 + day % 2, volume=1000) for day in range(18, 22)]
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', 18081), Handler).serve_forever()
