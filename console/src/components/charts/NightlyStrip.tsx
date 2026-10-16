import { useMemo, useState } from 'react'

import { cx } from '../../lib/cx'
import { formatDuration } from '../../lib/format'
import type { NightlyBenchmarkResult } from '../../types'
import styles from './charts.module.css'

interface NightlyStripProps {
  nightly: NightlyBenchmarkResult[]
  ariaLabel: string
  viewWidth?: number
}

const DEFAULT_VIEW_W = 780
const VIEW_H = 168
const PAD_X = 26
const BASELINE = 116
const MAX_BAR_H = 84

export function NightlyStrip({
  nightly,
  ariaLabel,
  viewWidth = DEFAULT_VIEW_W,
}: NightlyStripProps) {
  const [hover, setHover] = useState<number | null>(null)

  const layout = useMemo(() => {
    const count = Math.max(1, nightly.length)
    const innerW = viewWidth - PAD_X * 2
    const colW = innerW / count
    const barW = Math.min(42, colW * 0.44)
    const maxDuration = Math.max(...nightly.map((n) => n.durationMs), 1)
    return { colW, barW, maxDuration, innerW }
  }, [nightly, viewWidth])

  const centerX = (index: number): number => PAD_X + layout.colW * (index + 0.5)

  const active = hover !== null ? nightly[hover] : null

  return (
    <div className={styles.wrap}>
      <svg
        className={styles.svg}
        viewBox={`0 0 ${viewWidth} ${VIEW_H}`}
        role="img"
        aria-label={ariaLabel}
        onPointerLeave={() => setHover(null)}
      >
        <line
          x1={PAD_X - 6}
          x2={viewWidth - PAD_X + 6}
          y1={BASELINE}
          y2={BASELINE}
          className={styles.axis}
        />

        {nightly.map((night, index) => {
          const ratio = night.durationMs / layout.maxDuration
          const barH = Math.max(8, ratio * MAX_BAR_H)
          const isHovered = hover === index
          return (
            <g key={night.night}>
              <rect
                x={PAD_X + layout.colW * index}
                y={12}
                width={layout.colW}
                height={VIEW_H - 24}
                className={cx(styles.column, isHovered && styles.columnActive)}
                onPointerEnter={() => setHover(index)}
              />
              <rect
                x={centerX(index) - layout.barW / 2}
                y={BASELINE - barH}
                width={layout.barW}
                height={barH}
                className={cx(
                  styles.nightBar,
                  night.passed ? styles.nightPass : styles.nightFail,
                )}
                style={{ animationDelay: `${index * 52}ms` }}
              />
              {!night.passed && (
                <circle
                  cx={centerX(index)}
                  cy={BASELINE - barH - 9}
                  r={3.2}
                  className={styles.breach}
                  style={{ animationDelay: `${380 + index * 40}ms` }}
                />
              )}
              <text
                x={centerX(index)}
                y={BASELINE + 16}
                textAnchor="middle"
                className={styles.micro}
              >
                {night.night}
              </text>
              <text
                x={centerX(index)}
                y={BASELINE + 30}
                textAnchor="middle"
                className={cx(styles.micro, !night.passed && styles.tooltipBad)}
              >
                {night.passed ? `${night.totalRuns} 次` : `失败 ${night.failedRuns}`}
              </text>
            </g>
          )
        })}
      </svg>

      {active && (
        <div
          className={styles.tooltip}
          style={{
            left: `${(centerX(hover ?? 0) / viewWidth) * 100}%`,
            top: 2,
          }}
        >
          <div className={styles.tooltipTitle}>
            {active.night} 夜间基准 · {active.passed ? '通过' : '失败'}
          </div>
          <div className={styles.tooltipRow}>
            <span>耗时</span>
            <span className={styles.tooltipValue}>{formatDuration(active.durationMs)}</span>
          </div>
          <div className={styles.tooltipRow}>
            <span>运行 / 失败</span>
            <span className={cx(styles.tooltipValue, !active.passed && styles.tooltipBad)}>
              {active.totalRuns} / {active.failedRuns}
            </span>
          </div>
          <div className={styles.tooltipRow}>
            <span>基线版本</span>
            <span className={styles.tooltipValue}>{active.baselineVersion}</span>
          </div>
          {active.regressionCreated && (
            <div className={cx(styles.tooltipRow, styles.tooltipBad)}>
              <span>已自动建回归用例</span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
