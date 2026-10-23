import type { SectorPerformance } from './types';

export type SectorPeriod = 'changePct1d' | 'changePct1w' | 'changePct1m';

export function sortSectors(sectors: SectorPerformance[], period: SectorPeriod): SectorPerformance[] {
  return [...sectors].sort((a, b) => {
    const left = a[period];
    const right = b[period];
    const leftValid = left != null && Number.isFinite(left);
    const rightValid = right != null && Number.isFinite(right);
    if (!leftValid || !rightValid) return Number(rightValid) - Number(leftValid) || a.slug.localeCompare(b.slug);
    return right - left || a.slug.localeCompare(b.slug);
  });
}
