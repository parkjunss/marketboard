import styles from './dashboard.module.css';

export function DashboardHeader({ name }: { name: string }) {
  return <header className={styles.header}>
    <h1>안녕하세요, {name}님</h1>
    <p>오늘도 성공적인 투자를 응원합니다.</p>
  </header>;
}
