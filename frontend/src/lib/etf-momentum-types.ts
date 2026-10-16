export interface EtfMomentumResult {
  asOf: string;
  startDate: string;
  endDate: string;
  ready: boolean;
  cashWeight: number | null;
  rows: {
    ticker: string;
    rank: number | null;
    momentum: number | null;
    close: number | null;
    sma200: number | null;
    sessionDate: string | null;
    weight: number | null;
    reason: string;
  }[];
}
