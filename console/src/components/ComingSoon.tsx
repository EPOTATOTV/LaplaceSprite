import { Construction, type LucideIcon } from 'lucide-react'

import { Glass } from './glass/Glass'
import styles from './ComingSoon.module.css'

export interface ComingSoonProps {
  code: string
  title: string
  summary: string
  /** 该屏计划承载的几块内容，按设计稿顺序列 */
  planned: string[]
  icon?: LucideIcon
}

export function ComingSoon({ code, title, summary, planned, icon }: ComingSoonProps) {
  const Icon = icon ?? Construction
  return (
    <div className={styles.wrap}>
      <Glass level="panel" className={styles.lead}>
        <div className={styles.badge}>
          <Icon size={15} strokeWidth={1.7} aria-hidden="true" />
          <span className="tabular">{code}</span>
        </div>
        <h2 className={styles.title}>{title}</h2>
        <p className={styles.summary}>{summary}</p>
        <div className={styles.rule} aria-hidden="true" />
      </Glass>

      <ol className={styles.plan}>
        {planned.map((item, index) => (
          <li key={item} className={styles.planItem} style={{ animationDelay: `${index * 55}ms` }}>
            <span className={styles.planIndex}>{index + 1}</span>
            <span className={styles.planText}>{item}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}
