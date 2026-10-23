import { Button } from '@astryxdesign/core/Button';
import styles from './resource-state.module.css';

export function ResourceState({ status, label, onRetry }: {
  status: 'loading' | 'error' | 'empty'; label: string; onRetry?: () => void;
}) {
  return <div className={styles.state} role={status === 'error' ? 'alert' : 'status'} aria-busy={status === 'loading'}>
    {status === 'loading' && <div className={styles.skeleton} aria-hidden="true" />}
    <p>{status === 'loading' ? `${label} 불러오는 중` : status === 'error'
      ? `${label} 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.`
      : `${label} 정보가 없습니다.`}</p>
    {status === 'error' && onRetry && <Button label={`${label} 다시 불러오기`} variant="secondary" onClick={onRetry} />}
  </div>;
}
