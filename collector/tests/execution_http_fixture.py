"""Loopback-only integration fixture using the production reducer (no dependencies)."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.execution import replay
from app.auto_trading import replay_auto, compare_auto


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        try:
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            auto = compare_auto if payload.get("comparison") else replay_auto
            state = auto(payload["configuration"], payload["bars"], payload.get("warmup")) if self.path == "/execution/auto-replay" else replay(payload["configuration"], payload["commands"])
            body = dict(valid=True, state=state)
            status = 200
        except (ValueError, KeyError, TypeError) as exc:
            body, status = dict(detail=str(exc)), 422
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *_):
        pass


server = ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 0), Handler)
print(server.server_port, flush=True)
server.serve_forever()
