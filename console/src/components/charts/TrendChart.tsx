import { useMemo, useState } from 'react'

import { cx } from '../../lib/cx'
import type { Tone } from '../../lib/tone'
import type { MetricPoint } from '../../types'
import styles from './charts.module.css'

export interface TrendSeries {
  key: string
  label: string
  tone: Tone
  /** 双轴：误报率和检出率量级差太多，各占一边 */
  axis: 'left' | 'right'
  points: MetricPoint[]
  threshold: number
  format: (value: number) => string
}

interface TrendChartProps {
  series: TrendSeries[]
  ariaLabel: string
  height?: number
  /** viewBox 宽度：窄栏里传小一点，字号才不会被压扁 */
  viewWidth?: number
}

const DEFAULT_VIEW_W = 760
const PAD_TOP = 18
const PAD_BOTTOM = 30
const PAD_LEFT = 60
const PAD_RIGHT = 62

interface AxisDomain {
  left: [number, number]
  right: [number, number]
}

function buildDomain(points: number[]): [number, number] {
  if (points.length === 0) return [0, 1]
  const min = Math.min(...points)
  const max = Math.max(...points)
  if (max - min < 1e-12) {
    const bump = Math.abs(max) * 0.02 || 0.01
    return [min - bump, max + bump]
  }
  const pad = (max - min) * 0.14
  return [min - pad, max + pad]
}

export function TrendChart({
  series,
  ariaLabel,
  height = 224,
  viewWidth = DEFAULT_VIEW_W,
}: TrendChartProps) {
  const [active, setActive] = useState<number | null>(null)

  const hasRight = series.some((s) => s.axis === 'right')
  const padRight = hasRight ? PAD_RIGHT : 16
  const innerW = viewWidth - PAD_LEFT - padRight
  const innerH = height - PAD_TOP - PAD_BOTTOM

  const domains = useMemo<AxisDomain>(() => {
    const collect = (axis: 'left' | 'right'): number[] => {
      const values: number[] = []
      series
        .filter((s) => s.axis === axis)
        .forEach((s) => {
          s.points.forEach((p) => values.push(p.value))
          values.push(s.threshold)
        })
      return values
    }
    return { left: buildDomain(collect('left')), right: buildDomain(collect('right')) }
  }, [series])

  const count = useMemo(
    () => Math.max(2, ...series.map((s) => s.points.length)),
    [series],
  )

  const versions = useMemo(() => {
    const longest = series.reduce<TrendSeries | null>(
      (acc, s) => (acc === null || s.points.length > acc.points.length ? s : acc),
      null,
    )
    return longest ? longest.points.map((p) => p.version) : []
  }, [series])

  const x = (index: number): number => PAD_LEFT + (innerW * index) / (count - 1)

  const y = (value: number, axis: 'left' | 'right'): number => {
    const [lo, hi] = domains[axis]
    const ratio = (value - lo) / (hi - lo || 1)
    return PAD_TOP + innerH - ratio * innerH
  }

  const leftFormat = (series.find((s) => s.axis === 'left') ?? series[0])?.format ?? String
  const rightFormat = series.find((s) => s.axis === 'right')?.format ?? leftFormat

  const ticks = [0, 1, 2, 3].map((t) => t / 3)
  const stepX = count > 1 ? innerW / (count - 1) : innerW

  const activeIndex = active !== null && active >= 0 && active < versions.length ? active : null

  return (
    <div className={styles.wrap}>
      <div className={styles.legend}>
        {series.map((s) => {
          const latest = s.points[s.points.length - 1]
          return (
            <span key={s.key} className={styles.legendItem}>
              <span className={cx(styles.legendSwatch, styles[`swatch${cap(s.tone)}`])} />
              {s.label}
              <span className={styles.legendValue}>{latest ? s.format(latest.value) : '—'}</span>
            </span>
          )
        })}
        <span className={styles.legendItem}>
          <span className={cx(styles.legendSwatchBox, styles.swatchDanger)} />
          越界点
        </span>
      </div>

      <svg
        className={styles.svg}
        viewBox={`0 0 ${viewWidth} ${height}`}
        role="img"
        aria-label={ariaLabel}
        onPointerLeave={() => setActive(null)}
      >
        {ticks.map((t) => {
          const value = domains.left[0] + (domains.left[1] - domains.left[0]) * t
          const yPos = PAD_TOP + innerH - t * innerH
          return (
            <g key={`left-${t}`}>
              <line
                x1={PAD_LEFT}
                x2={PAD_LEFT + innerW}
                y1={yPos}
                y2={yPos}
                className={styles.grid}
              />
              <text x={PAD_LEFT - 8} y={yPos + 3.5} textAnchor="end" className={styles.tick}>
                {leftFormat(value)}
              </text>
              {hasRight && (
                <text
                  x={PAD_LEFT + innerW + 8}
                  y={yPos + 3.5}
                  textAnchor="start"
                  className={styles.tick}
                >
                  {rightFormat(domains.right[0] + (domains.right[1] - domains.right[0]) * t)}
                </text>
              )}
            </g>
          )
        })}

        <line
          x1={PAD_LEFT}
          x2={PAD_LEFT + innerW}
          y1={PAD_TOP + innerH}
          y2={PAD_TOP + innerH}
          className={styles.axis}
        />

        {activeIndex !== null && (
          <line
            x1={x(activeIndex)}
            x2={x(activeIndex)}
            y1={PAD_TOP}
            y2={PAD_TOP + innerH}
            className={styles.guide}
          />
        )}

        {series.map((s, index) => (
          <line
            key={`th-${s.key}`}
            x1={PAD_LEFT}
            x2={PAD_LEFT + innerW}
            y1={y(s.threshold, s.axis)}
            y2={y(s.threshold, s.axis)}
            className={cx(styles.threshold, styles[`tone${cap(s.tone)}`])}
            style={{ animation: `lc-fade var(--d-4) var(--ease-out) ${120 + index * 90}ms backwards` }}
          />
        ))}

        {series.map((s, index) => {
          const path = s.points
            .map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(2)} ${y(p.value, s.axis).toFixed(2)}`)
            .join(' ')
          return (
            <path
              key={`line-${s.key}`}
              d={path}
              pathLength={1}
              className={cx(styles.line, styles[`tone${cap(s.tone)}`])}
              style={{ animationDelay: `${160 + index * 130}ms` }}
            />
          )
        })}

        {series.map((s, seriesIndex) =>
          s.points.map((p, i) =>
            p.breached ? (
              <circle
                key={`${s.key}-b-${p.version}`}
                cx={x(i)}
                cy={y(p.value, s.axis)}
                r={3.7}
                className={styles.breach}
                style={{ animationDelay: `${480 + seriesIndex * 120 + i * 26}ms` }}
              />
            ) : (
              <circle
                key={`${s.key}-p-${p.version}`}
                cx={x(i)}
                cy={y(p.value, s.axis)}
                r={2.3}
                className={cx(styles.point, styles[`tone${cap(s.tone)}`])}
              />
            ),
          ),
        )}

        {versions.map((version, i) => (
          <rect
            key={`col-${version}`}
            x={x(i) - stepX / 2}
            y={PAD_TOP}
            width={stepX}
            height={innerH}
            className={cx(styles.column, activeIndex === i && styles.columnActive)}
            onPointerEnter={() => setActive(i)}
          />
        ))}

        {versions.map((version, i) =>
          i % 2 === 0 || versions.length <= 6 ? (
            <text
              key={`lb-${version}`}
              x={x(i)}
              y={PAD_TOP + innerH + 18}
              textAnchor="middle"
              className={styles.tick}
            >
              {version}
            </text>
          ) : null,
        )}
      </svg>

      {activeIndex !== null && (
        <div
          className={styles.tooltip}
          style={{
            left: `${clamp((x(activeIndex) / viewWidth) * 100, 12, 88)}%`,
            top: 8,
          }}
        >
          <div className={styles.tooltipTitle}>{versions[activeIndex]}</div>
          {series.map((s) => {
            const point = s.points[activeIndex]
            if (!point) return null
            return (
              <div key={`tt-${s.key}`} className={cx(styles.tooltipRow, point.breached && styles.tooltipBad)}>
                <span>{s.label}</span>
                <span className={styles.tooltipValue}>{s.format(point.value)}</span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function cap(value: Tone): string {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}
