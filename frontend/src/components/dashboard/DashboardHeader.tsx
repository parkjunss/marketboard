import styles from './dashboard.module.css';

export function DashboardHeader({ name, showWatchlist }: { name: string; showWatchlist: boolean }) {
  return <header className={styles.header}>
    <h1>대시보드</h1>
    <p>{name}님의 시장 현황{showWatchlist ? '과 관심종목을' : '을'} 한눈에 확인하세요.</p>
  </header>;
}
