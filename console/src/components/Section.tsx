import type { CSSProperties, ReactNode } from 'react'

import { cx } from '../lib/cx'
import { Glass } from './glass/Glass'
import styles from './Section.module.css'

export type SectionLevel = 'panel' | 'inset' | 'none'

interface SectionProps {
  title: string
  /** 标题右侧的一句话说明：范围、口径或新鲜度 */
  note?: string
  actions?: ReactNode
  level?: SectionLevel
  /** 入场动画的错开延时（毫秒） */
  delay?: number
  className?: string
  children: ReactNode
}

export function Section({
  title,
  note,
  actions,
  level = 'panel',
  delay = 0,
  className,
  children,
}: SectionProps) {
  const head = (
    <header className={cx(styles.head, level === 'none' && styles.headBare)}>
      <h2 className={styles.title}>{title}</h2>
      {note && <p className={styles.note}>{note}</p>}
      {actions && <div className={styles.actions}>{actions}</div>}
    </header>
  )

  const style = { '--delay': `${delay}ms` } as CSSProperties

  if (level === 'none') {
    return (
      <section className={cx(styles.bare, styles.section, className)} style={style} aria-label={title}>
        {head}
        <div className={styles.bodyBare}>{children}</div>
      </section>
    )
  }

  return (
    <Glass level={level} as="section" label={title} className={cx(styles.section, className)} style={style}>
      {head}
      <div className={styles.body}>{children}</div>
    </Glass>
  )
}
