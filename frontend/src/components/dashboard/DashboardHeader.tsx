import styles from './dashboard.module.css';

export function DashboardHeader({ name }: { name: string }) {
  return <header className={styles.header}>
    <h1>대시보드</h1>
    <p>{name}님의 시장 현황과 관심종목을 한눈에 확인하세요.</p>
  </header>;
}
