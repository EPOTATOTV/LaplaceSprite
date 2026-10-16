import type { CSSProperties } from 'react'
import { useMemo } from 'react'

import { cx } from '../../lib/cx'
import { formatPercent } from '../../lib/format'
import type { CheatTier, DetectionByCheckTier } from '../../types'
import styles from './charts.module.css'

interface HeatMatrixProps {
  data: DetectionByCheckTier
  tiers: readonly CheatTier[]
  ariaLabel: string
}

/** 把检出率映射成 0..1 的热度；低于下限的值照样留一点底色，避免格子看起来是空的 */
function heatOf(value: number, min: number, max: number): number {
  if (max - min < 1e-9) return 0.6
  const t = (value - min) / (max - min)
  return 0.08 + t * 0.5
}

export function HeatMatrix({ data, tiers, ariaLabel }: HeatMatrixProps) {
  const rows = useMemo(() => Object.entries(data), [data])

  const bounds = useMemo(() => {
    const values = rows.flatMap(([, rates]) => tiers.map((tier) => rates[tier]))
    return { min: Math.min(...values), max: Math.max(...values) }
  }, [rows, tiers])

  const scaleSteps = [0, 1, 2, 3, 4, 5]

  return (
    <div>
      <div className={styles.matrixWrap}>
        <table className={styles.matrix} aria-label={ariaLabel}>
          <thead>
            <tr>
              <th scope="col">检测项</th>
              {tiers.map((tier) => (
                <th key={tier} scope="col">
                  {tier}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map(([check, rates], rowIndex) => (
              <tr key={check}>
                <th scope="row">{check}</th>
                {tiers.map((tier, tierIndex) => {
                  const value = rates[tier]
                  const heat = heatOf(value, bounds.min, bounds.max)
                  return (
                    <td
                      key={`${check}-${tier}`}
                      className={cx(styles.cell, heat > 0.4 && styles.cellStrong)}
                      style={
                        {
                          '--heat': heat,
                          animationDelay: `${(rowIndex * tiers.length + tierIndex) * 16}ms`,
                        } as CSSProperties
                      }
                      title={`${check} · ${tier}：${formatPercent(value, 2)}`}
                    >
                      {formatPercent(value, 1)}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className={styles.scaleLegend}>
        <span>低 {formatPercent(bounds.min, 1)}</span>
        <div className={styles.scaleBar}>
          {scaleSteps.map((step) => (
            <span
              key={step}
              className={styles.scaleStep}
              style={{
                backgroundColor: `rgba(var(--heat-rgb), ${(0.08 + (step / (scaleSteps.length - 1)) * 0.5).toFixed(3)})`,
              }}
            />
          ))}
        </div>
        <span>高 {formatPercent(bounds.max, 1)}</span>
      </div>
    </div>
  )
}
