import type { CSSProperties } from 'react'
import type { LucideIcon } from 'lucide-react'

import { Sparkline } from '../../components/charts/Sparkline'
import { Glass } from '../../components/glass/Glass'
import { cx } from '../../lib/cx'
import type { Tone } from '../../lib/tone'
import styles from './MetricCard.module.css'

export interface MetricCardProps {
  label: string
  value: string
  unit?: string
  caption: string
  icon: LucideIcon
  tone?: Tone
  /** 主指标：占两列、字号更大，页面视觉重心落在这里 */
  hero?: boolean
  /** 入场动画的错开延时（毫秒） */
  delay?: number
  spark?: { values: number[]; breached?: boolean[] }
  /** 通过率之类的比例条 */
  ratio?: { value: number; label: string }
}

export function MetricCard({
  label,
  value,
  unit,
  caption,
  icon: Icon,
  tone = 'neutral',
  hero = false,
  delay = 0,
  spark,
  ratio,
}: MetricCardProps) {
  return (
    <Glass
      level={hero ? 'panel' : 'inset'}
      className={cx(styles.card, hero && styles.hero, styles[tone])}
      style={{ animationDelay: `${delay}ms` } as CSSProperties}
    >
      <div className={styles.head}>
        <span className={styles.icon}>
          <Icon size={hero ? 17 : 14} strokeWidth={1.8} aria-hidden="true" />
        </span>
        <span className={styles.label}>{label}</span>
      </div>

      <div className={styles.valueRow}>
        <span className={cx(styles.value, 'tabular')}>{value}</span>
        {unit && <span className={styles.unit}>{unit}</span>}
      </div>

      {spark && (
        <div className={styles.spark}>
          <Sparkline
            values={spark.values}
            breached={spark.breached}
            width={hero ? 240 : 150}
            height={hero ? 40 : 28}
            tone={tone}
          />
        </div>
      )}

      {ratio && (
        <div className={styles.ratio} title={`${label}：${ratio.label}`}>
          <span className={styles.ratioTrack}>
            <span className={styles.ratioFill} style={{ transform: `scaleX(${ratio.value})` }} />
          </span>
          <em className={cx(styles.ratioLabel, 'tabular')}>{ratio.label}</em>
        </div>
      )}

      <p className={styles.caption}>{caption}</p>
    </Glass>
  )
}
