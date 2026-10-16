import type { CSSProperties } from 'react'

import { cx } from '../lib/cx'
import type { Tone } from '../lib/tone'
import styles from './ArcGauge.module.css'

const VIEW_W = 120
const VIEW_H = 74
const CX = 60
const CY = 58
const R = 44
const ARC = `M ${CX - R} ${CY} A ${R} ${R} 0 0 1 ${CX + R} ${CY}`

export interface ArcGaugeProps {
  value: number
  max: number
  /** 健康下限：低于它逐级降为 warn / danger */
  healthyFrom?: number
  size?: number
  /** 弧内的单位说明，例如 TPS */
  caption?: string
  /** 弧的数值刻度标注（左右端点） */
  scaleLabels?: [string, string]
  /** 读数排版：大号用于焦点卡，小号用于紧凑行 */
  readout?: 'lg' | 'sm'
  delay?: number
  tone?: Tone
  ariaLabel: string
}

function toneFor(value: number, healthyFrom: number | undefined, fallback: Tone): Tone {
  if (healthyFrom === undefined) return fallback
  if (value >= healthyFrom) return 'ok'
  if (value >= healthyFrom - 0.6) return 'warn'
  return 'danger'
}

/** 半圆仪表：把单个读数做成"仪表盘"视觉重心，而不是又一张等权重的卡片。 */
export function ArcGauge({
  value,
  max,
  healthyFrom,
  size = 168,
  caption = 'TPS',
  scaleLabels,
  readout = 'lg',
  delay = 0,
  tone = 'accent',
  ariaLabel,
}: ArcGaugeProps) {
  const ratio = max <= 0 ? 0 : Math.min(1, Math.max(0, value / max))
  const resolved = toneFor(value, healthyFrom, tone)

  let tick: { x1: number; y1: number; x2: number; y2: number } | null = null
  if (healthyFrom !== undefined && max > 0) {
    const t = Math.min(1, Math.max(0, healthyFrom / max))
    const angle = Math.PI * (1 - t)
    tick = {
      x1: CX + (R - 8) * Math.cos(angle),
      y1: CY - (R - 8) * Math.sin(angle),
      x2: CX + (R + 7) * Math.cos(angle),
      y2: CY - (R + 7) * Math.sin(angle),
    }
  }

  return (
    <div className={styles.wrap} style={{ width: size }}>
      <svg
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        className={styles.svg}
        role="img"
        aria-label={ariaLabel}
      >
        <path d={ARC} pathLength={1} className={styles.track} />
        <path
          d={ARC}
          pathLength={1}
          className={cx(styles.value, styles[resolved])}
          style={
            {
              '--gauge-offset': 1 - ratio,
              animationDelay: `${delay}ms`,
            } as CSSProperties
          }
        />
        {tick && (
          <line
            x1={tick.x1}
            y1={tick.y1}
            x2={tick.x2}
            y2={tick.y2}
            className={styles.threshold}
          />
        )}
      </svg>

      <div className={cx(styles.readout, readout === 'sm' && styles.readoutSm)} aria-hidden="true">
        <span className={cx(styles.num, 'tabular')}>{value.toFixed(2)}</span>
        <span className={styles.caption}>{caption}</span>
      </div>

      {scaleLabels && (
        <div className={styles.scale} aria-hidden="true">
          <span>{scaleLabels[0]}</span>
          <span>{scaleLabels[1]}</span>
        </div>
      )}
    </div>
  )
}
