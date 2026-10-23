import { ImageResponse } from 'next/og';

export const dynamic = 'force-static';

export function GET() {
  return new ImageResponse(
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', background: '#101b2b', padding: '90px', color: '#f4f7fb' }}>
      <div style={{ display: 'flex', color: '#7ce5cf', fontSize: 28, letterSpacing: 6 }}>MARKETS · STOCKS · NEWS</div>
      <div style={{ display: 'flex', fontSize: 108, fontWeight: 700, marginTop: 30 }}>MarketBoard</div>
      <div style={{ display: 'flex', fontSize: 36, color: '#b9c9de', marginTop: 28 }}>Market data, in context.</div>
      <div style={{ display: 'flex', width: 160, height: 8, background: '#7ce5cf', marginTop: 50 }} />
    </div>,
    { width: 1200, height: 630 },
  );
}
