"""Verify public metadata in server HTML without JavaScript (run against a built app)."""
import json
import struct
import sys
from html.parser import HTMLParser
from urllib.request import Request, urlopen


class Head(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tags = {}
        self.title = ''
        self.in_title = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == 'title':
            self.in_title = True
        if tag == 'meta':
            key = attrs.get('name') or attrs.get('property')
            self.tags.setdefault(key, []).append(attrs.get('content'))
        if tag == 'link' and attrs.get('rel') == 'canonical':
            self.tags.setdefault('canonical', []).append(attrs.get('href'))

    def handle_endtag(self, tag):
        if tag == 'title':
            self.in_title = False

    def handle_data(self, data):
        if self.in_title:
            self.title += data


base = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:13100'
origin = 'https://marketboard.duckdns.org'
results = []
for agent in ['Mozilla/5.0', 'Twitterbot/1.0']:
    for path, name in [('/dashboard', '시장 대시보드'), ('/market', '시장 동향'),
                       ('/stock-list', '종목 검색'), ('/news', '시장 뉴스')]:
        request = Request(base + path + '?utm_source=metadata-check', headers={'User-Agent': agent})
        with urlopen(request, timeout=30) as response:
            head = Head()
            head.feed(response.read().decode('utf-8'))
        title = name + ' · MarketBoard'
        assert head.title == title, (path, head.title)
        for key, expected in [('canonical', origin + path), ('og:url', origin + path),
                              ('og:title', title), ('twitter:title', title),
                              ('og:image', origin + '/share-image'),
                              ('twitter:card', 'summary_large_image')]:
            assert head.tags.get(key) == [expected], (path, key, head.tags.get(key))
        descriptions = head.tags.get('description', [])
        assert len(descriptions) == 1 and len(descriptions[0]) > 20
        assert descriptions == head.tags.get('og:description') == head.tags.get('twitter:description')
        assert '실시간 종목 리스트' not in descriptions[0]
        results.append({'path': path, 'agent': agent, 'title': title,
                        'description': descriptions[0], 'canonical': head.tags['canonical'][0]})

with urlopen(base + '/login', timeout=30) as response:
    head = Head()
    head.feed(response.read().decode('utf-8'))
    assert 'canonical' not in head.tags, 'Public canonical leaked into login'
with urlopen(base + '/share-image', timeout=30) as response:
    assert response.headers.get_content_type() == 'image/png'
    png = response.read()
    assert png[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', png[16:24]) == (1200, 630)
print(json.dumps({'passed': True, 'pages': results, 'image': 'image/png 1200x630',
                  'loginCanonicalAbsent': True}, ensure_ascii=False, indent=2))
