import type { Metadata } from 'next';
import './globals.css';
import { Providers } from './providers';

export const metadata: Metadata = {
  metadataBase: new URL('https://marketboard.duckdns.org'),
  title: 'MarketBoard',
  description: '시장 지표와 종목, 뉴스를 살펴보고 관심종목과 투자 기록을 관리하는 MarketBoard입니다.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
