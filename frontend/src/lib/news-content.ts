import type { NewsItem } from './types';

function normalized(text: string): string {
  return text.normalize('NFKC').toLocaleLowerCase('en-US').replace(/[^\p{L}\p{N}]/gu, '');
}

export function newsSummary(headline: string, summary?: string): string | null {
  const text = summary?.trim();
  if (!text || normalized(headline) === normalized(text)) return null;
  return text;
}

export function uniqueNews(items: NewsItem[]): NewsItem[] {
  const urls = new Set<string>();
  const headlines = new Set<string>();
  return items.filter(item => {
    const headline = `${normalized(item.source || '')}:${normalized(item.headline)}`;
    if ((item.url && urls.has(item.url)) || headlines.has(headline)) return false;
    if (item.url) urls.add(item.url);
    headlines.add(headline);
    return true;
  });
}
