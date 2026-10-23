"""Read public HTML without browser cookies; save metadata evidence only."""
import json
import time
import urllib.request
from html.parser import HTMLParser
from pathlib import Path

class Head(HTMLParser):
    def __init__(self):
        super().__init__()
        self.meta, self.links, self.title = [], [], ''
        self.in_title = False
        self.hidden = 0
        self.texts = []
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'meta': self.meta.append(attrs)
        if tag == 'link' and attrs.get('rel') == 'canonical': self.links.append(attrs)
        if tag == 'title': self.in_title = True
        if tag in ('script', 'style'): self.hidden += 1
    def handle_endtag(self, tag):
        if tag == 'title': self.in_title = False
        if tag in ('script', 'style'): self.hidden = max(0, self.hidden - 1)
    def handle_data(self, data):
        if self.in_title: self.title += data
        if not self.hidden and data.strip(): self.texts.append(data.strip())

results = []
for route in ['/dashboard', '/market', '/stock-list', '/news', '/symbols/NVDA', '/terms']:
    start = time.monotonic()
    try:
        with urllib.request.urlopen('https://marketboard.duckdns.org' + route, timeout=20) as response:
            html = response.read().decode('utf-8')
            parser = Head()
            parser.feed(html)
            results.append(dict(route=route, status=response.status, final_url=response.url,
                                seconds=round(time.monotonic()-start, 3), title=parser.title,
                                meta=parser.meta, canonical=parser.links,
                                initial_text=' '.join(parser.texts)[:1500],
                                has_disclaimer='투자 권유' in html, bytes=len(html.encode())))
    except Exception as exc:
        results.append(dict(route=route, error=str(exc)))
payload = json.dumps(results, ensure_ascii=False, indent=2)
Path(__file__).with_name('public-html.json').write_text(payload, encoding='utf-8')
print(payload)
