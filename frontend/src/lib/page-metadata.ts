import type { Metadata } from 'next';

export function publicPageMetadata(path: string, name: string, description: string): Metadata {
  const title = `${name} · MarketBoard`;
  const images = [{ url: '/share-image', width: 1200, height: 630, alt: 'MarketBoard — Market data, in context' }];
  return {
    title,
    description,
    alternates: { canonical: path },
    openGraph: { title, description, url: path, siteName: 'MarketBoard', locale: 'ko_KR', type: 'website', images },
    twitter: { card: 'summary_large_image', title, description, images },
  };
}
