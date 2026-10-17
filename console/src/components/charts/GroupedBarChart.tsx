import { useMemo, useState } from 'react'

import { cx } from '../../lib/cx'
import { formatPercent } from '../../lib/format'
import type { SampleCategory, SampleTier, SampleTypeBreakdown } from '../../types'
import styles from './charts.module.css'

interface GroupedBarChartProps {
  data: SampleTypeBreakdown
  tiers: readonly SampleTier[]
  ariaLabel: string
  /** 参考线，画在 0..1 的横向刻度上 */
  reference?: number
  viewWidth?: number
}

const DEFAULT_VIEW_W = 780
const LABEL_W = 112
const TAG_W = 30
const VALUE_W = 52
const ROW_H = 40
const BAR_H = 6
const BAR_GAP = 2.4
const TOP_OFFSET = 26
const GROUP_H = 24

const CATEGORY_ORDER: readonly SampleCategory[] = [
  'content',
  'tech',
  'perf',
  'network',
  'anomaly',
  'cheat',
]

const CATEGORY_LABEL: Record<SampleCategory, string> = {
  content: '内容型',
  tech: '科技·自动化',
  perf: '优化·渲染',
  network: '网络·多人',
  anomaly: '异常与装配',
  cheat: '检测类',
}

const TIER_TAG: Record<SampleTier, string> = {
  MIN: 'MIN',
  DEFAULT: 'DEF',
  MAX: 'MAX',
  HIDDEN: 'HID',
}

const TIER_FILL: Record<SampleTier, string> = {
  MIN: 'tierMin',
  DEFAULT: 'tierDefault',
  MAX: 'tierMax',
  HIDDEN: 'tierHidden',
}

const TIER_SWATCH: Record<SampleTier, string> = {
  MIN: 'swatchMin',
  DEFAULT: 'swatchDefault',
  MAX: 'swatchMax',
  HIDDEN: 'swatchHidden',
}

interface ChartRow {
  row: SampleTypeBreakdown[number]
  index: number
  top: number
}

interface ChartGroup {
  category: SampleCategory
  top: number
  rows: ChartRow[]
}

export function GroupedBarChart({
  data,
  tiers,
  ariaLabel,
  reference = 0.95,
  viewWidth = DEFAULT_VIEW_W,
}: GroupedBarChartProps) {
  const [hover, setHover] = useState<{ row: number; tier: number } | null>(null)

  const layout = useMemo(() => {
    const groups: ChartGroup[] = []
    let top = TOP_OFFSET
    let index = 0
    for (const category of CATEGORY_ORDER) {
      const groupRows = data.filter((row) => row.category === category)
      if (groupRows.length === 0) continue
      const group: ChartGroup = { category, top, rows: [] }
      top += GROUP_H
      for (const row of groupRows) {
        group.rows.push({ row, index, top })
        top += ROW_H
        index += 1
      }
      groups.push(group)
    }
    return { groups, height: top + 8 }
  }, [data])

  const flatRows = useMemo(
    () => layout.groups.flatMap((group) => group.rows),
    [layout],
  )

  const trackX = LABEL_W + TAG_W
  const trackW = viewWidth - LABEL_W - TAG_W - VALUE_W

  const hoverInfo = useMemo(() => {
    if (!hover) return null
    const item = flatRows[hover.row]
    const tier = tiers[hover.tier]
    const cell = item?.row.tiers.find((stat) => stat.tier === tier)
    if (!item || !tier || !cell) return null
    const value = cell.samples === 0 ? 0 : cell.hits / cell.samples
    return { item, tier, cell, value }
  }, [hover, flatRows, tiers])

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
        <span className={styles.legendNote}>按目标类型分组</span>
      </div>

      <svg
        className={styles.svg}
        viewBox={`0 0 ${viewWidth} ${layout.height}`}
        role="img"
        aria-label={ariaLabel}
        onPointerLeave={() => setHover(null)}
      >
        <line
          x1={trackX + trackW * reference}
          x2={trackX + trackW * reference}
          y1={TOP_OFFSET - 8}
          y2={layout.height - 8}
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

        {layout.groups.map((group) => (
          <g key={group.category}>
            <text x={0} y={group.top + 12} className={styles.groupLabel}>
              {CATEGORY_LABEL[group.category]}
              <tspan className={styles.groupCount}>
                {' '}
                · {group.rows.length} 个样本类型
              </tspan>
            </text>
            <line
              x1={0}
              x2={viewWidth}
              y1={group.top + 17}
              y2={group.top + 17}
              className={styles.groupRule}
            />

            {group.rows.map(({ row, index, top }) => (
              <g key={row.sampleType}>
                <text
                  x={0}
                  y={top + 24}
                  className={styles.rowLabel}
                  style={{ animationDelay: `${index * 32}ms` }}
                >
                  {row.sampleType}
                </text>

                {tiers.map((tier, tierIndex) => {
                  const cell = row.tiers.find((stat) => stat.tier === tier)
                  const samples = cell?.samples ?? 0
                  const hits = cell?.hits ?? 0
                  const value = samples === 0 ? 0 : hits / samples
                  const barY = top + 4 + tierIndex * (BAR_H + BAR_GAP)
                  const barW = Math.max(2, trackW * value)
                  const weak = value < reference
                  return (
                    <g key={`${row.sampleType}-${tier}`}>
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
                        style={{ animationDelay: `${120 + index * 28 + tierIndex * 18}ms` }}
                        onPointerEnter={() => setHover({ row: index, tier: tierIndex })}
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
                        {samples === 0 ? '—' : formatPercent(value, 1)}
                      </text>
                    </g>
                  )
                })}
              </g>
            ))}
          </g>
        ))}
      </svg>

      {hover && hoverInfo && (
        <div
          className={styles.tooltip}
          style={{
            left: `${((trackX + trackW * hoverInfo.value) / viewWidth) * 100}%`,
            top: `${((hoverInfo.item.top + 4 + hover.tier * (BAR_H + BAR_GAP)) / layout.height) * 100}%`,
            transform: 'translate(-50%, -114%)',
          }}
        >
          <div className={styles.tooltipTitle}>
            {hoverInfo.item.row.sampleType} · {hoverInfo.tier}
          </div>
          <div className={styles.tooltipRow}>
            <span>通过率</span>
            <span
              className={cx(
                styles.tooltipValue,
                hoverInfo.value < reference && styles.tooltipBad,
              )}
            >
              {formatPercent(hoverInfo.value, 2)}
            </span>
          </div>
          <div className={styles.tooltipRow}>
            <span>命中 / 样本</span>
            <span className={styles.tooltipValue}>
              {hoverInfo.cell.hits} / {hoverInfo.cell.samples}
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
