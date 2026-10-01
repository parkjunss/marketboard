import styles from './page-layout.module.css';

export function PageLayout({ title, description, actions, children }: {
  title: string;
  description?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  return <main className={styles.page}>
    <header className={styles.header}><div><h1>{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className={styles.actions}>{actions}</div>}</header>
    <div className={styles.body}>{children}</div>
  </main>;
}

export function PageSurface({ title, children }: { title?: string; children: React.ReactNode }) {
  return <section className={styles.surface}>{title && <h2>{title}</h2>}<div className={styles.surfaceBody}>{children}</div></section>;
}
