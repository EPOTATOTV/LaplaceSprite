import { useMemo } from 'react'

import { cx } from '../../lib/cx'
import type { Tone } from '../../lib/tone'
import styles from './charts.module.css'

interface SparklineProps {
  values: number[]
  width?: number
  height?: number
  tone?: Tone
  /** 与 values 等长；true 处标为越界 */
  breached?: boolean[]
}

/** 微型趋势线，只做视觉提示；具体数值由旁边的文本承担，故对读屏隐藏。 */
export function Sparkline({
  values,
  width = 132,
  height = 34,
  tone = 'accent',
  breached,
}: SparklineProps) {
  const geometry = useMemo(() => {
    if (values.length === 0) return null
    const min = Math.min(...values)
    const max = Math.max(...values)
    const span = max - min || 1
    const stepX = values.length > 1 ? width / (values.length - 1) : 0
    const pad = 3
    const points = values.map((value, index) => ({
      x: values.length > 1 ? index * stepX : width / 2,
      y: pad + (1 - (value - min) / span) * (height - pad * 2),
      breached: breached?.[index] ?? false,
    }))
    const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' ')
    const area = `${line} L${width} ${height} L0 ${height} Z`
    return { points, line, area }
  }, [values, width, height, breached])

  if (!geometry) return null

  return (
    <svg
      className={cx(styles.spark, styles[`tone${capitalize(tone)}`])}
      width={width}
      height={height}
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      aria-hidden="true"
      focusable="false"
    >
      <path d={geometry.area} className={cx(styles.areaFill, styles[`fill${capitalize(tone)}`])} />
      <path d={geometry.line} className={cx(styles.sparkLine, styles[`tone${capitalize(tone)}`])} />
      {geometry.points
        .filter((p) => p.breached)
        .map((p) => (
          <circle key={`b-${p.x}`} cx={p.x} cy={p.y} r={2.4} className={styles.breach} />
        ))}
    </svg>
  )
}

function capitalize(value: Tone): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}
