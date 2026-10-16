import { useMemo, useState } from 'react'

import { cx } from '../../lib/cx'
import { formatPercent } from '../../lib/format'
import type { CheatTier, DetectionByCheckTier } from '../../types'
import styles from './charts.module.css'

interface GroupedBarChartProps {
  data: DetectionByCheckTier
  tiers: readonly CheatTier[]
  ariaLabel: string
  /** 参考线，画在 0..1 的横向刻度上 */
  reference?: number
  viewWidth?: number
}

const DEFAULT_VIEW_W = 780
const LABEL_W = 94
const TAG_W = 32
const VALUE_W = 56
const ROW_H = 46
const BAR_H = 7
const BAR_GAP = 3.4
const TOP_OFFSET = 24

const TIER_TAG: Record<CheatTier, string> = {
  MIN: 'MIN',
  DEFAULT: 'DEF',
  MAX: 'MAX',
  HIDDEN: 'HID',
}

const TIER_FILL: Record<CheatTier, string> = {
  MIN: 'tierMin',
  DEFAULT: 'tierDefault',
  MAX: 'tierMax',
  HIDDEN: 'tierHidden',
}

const TIER_SWATCH: Record<CheatTier, string> = {
  MIN: 'swatchMin',
  DEFAULT: 'swatchDefault',
  MAX: 'swatchMax',
  HIDDEN: 'swatchHidden',
}

export function GroupedBarChart({
  data,
  tiers,
  ariaLabel,
  reference = 0.95,
  viewWidth = DEFAULT_VIEW_W,
}: GroupedBarChartProps) {
  const [hover, setHover] = useState<{ row: number; tier: number } | null>(null)

  const rows = useMemo(() => Object.entries(data), [data])
  const trackX = LABEL_W + TAG_W
  const trackW = viewWidth - LABEL_W - TAG_W - VALUE_W
  const height = TOP_OFFSET + rows.length * ROW_H + 10

  const hoverInfo = useMemo(() => {
    if (!hover) return null
    const row = rows[hover.row]
    const tier = tiers[hover.tier]
    if (!row || !tier) return null
    return { check: row[0], tier, value: row[1][tier] }
  }, [hover, rows, tiers])

  return (
    <div className={styles.wrap}>
      <div className={styles.legend}>
        {tiers.map((tier) => (
          <span key={tier} className={styles.legendItem}>
            <span className={cx(styles.legendSwatchBox, styles[TIER_SWATCH[tier]])} />
            {tier}
          </span>
        ))}
        <span className={styles.legendItem}>
          <span className={cx(styles.legendSwatchBox, styles.swatchDanger)} />
          低于参考线 {formatPercent(reference, 0)}
        </span>
      </div>

      <svg
        className={styles.svg}
        viewBox={`0 0 ${viewWidth} ${height}`}
        role="img"
        aria-label={ariaLabel}
        onPointerLeave={() => setHover(null)}
      >
        <line
          x1={trackX + trackW * reference}
          x2={trackX + trackW * reference}
          y1={TOP_OFFSET - 8}
          y2={TOP_OFFSET + rows.length * ROW_H - 6}
          className={styles.refLine}
        />
        <text
          x={trackX + trackW * reference}
          y={TOP_OFFSET - 12}
          textAnchor="middle"
          className={styles.micro}
        >
          参考线 {formatPercent(reference, 0)}
        </text>

        {rows.map(([check, rates], rowIndex) => {
          const rowTop = TOP_OFFSET + rowIndex * ROW_H
          return (
            <g key={check}>
              <text
                x={0}
                y={rowTop + 22}
                className={styles.rowLabel}
                style={{ animationDelay: `${rowIndex * 38}ms` }}
              >
                {check}
              </text>

              {tiers.map((tier, tierIndex) => {
                const value = rates[tier]
                const barY = rowTop + 8 + tierIndex * (BAR_H + BAR_GAP)
                const barW = Math.max(2, trackW * value)
                const weak = value < reference
                return (
                  <g key={`${check}-${tier}`}>
                    <rect
                      x={trackX}
                      y={barY}
                      width={trackW}
                      height={BAR_H}
                      className={styles.barTrack}
                    />
                    <rect
                      x={trackX}
                      y={barY}
                      width={barW}
                      height={BAR_H}
                      className={cx(styles.bar, styles[TIER_FILL[tier]])}
                      style={{ animationDelay: `${120 + rowIndex * 34 + tierIndex * 22}ms` }}
                      onPointerEnter={() => setHover({ row: rowIndex, tier: tierIndex })}
                    />
                    <text
                      x={trackX - TAG_W + 4}
                      y={barY + BAR_H - 0.4}
                      className={cx(styles.micro, styles[TIER_FILL[tier]])}
                    >
                      {TIER_TAG[tier]}
                    </text>
                    <text
                      x={trackX + trackW + 8}
                      y={barY + BAR_H - 0.4}
                      className={cx(styles.valueText, weak && styles.tooltipBad)}
                    >
                      {formatPercent(value, 1)}
                    </text>
                  </g>
                )
              })}
            </g>
          )
        })}
      </svg>

      {hover && hoverInfo && (
        <div
          className={styles.tooltip}
          style={{
            left: `${((trackX + trackW * hoverInfo.value) / viewWidth) * 100}%`,
            top: `${((TOP_OFFSET + hover.row * ROW_H + 8 + hover.tier * (BAR_H + BAR_GAP)) / height) * 100}%`,
            transform: 'translate(-50%, -114%)',
          }}
        >
          <div className={styles.tooltipTitle}>
            {hoverInfo.check} · {hoverInfo.tier}
          </div>
          <div className={styles.tooltipRow}>
            <span>检出率</span>
            <span
              className={cx(styles.tooltipValue, hoverInfo.value < reference && styles.tooltipBad)}
            >
              {formatPercent(hoverInfo.value, 2)}
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
