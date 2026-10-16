import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'

import { StatusPill } from '../../components/StatusPill'
import { cx } from '../../lib/cx'
import { formatInt } from '../../lib/format'
import type { ScenarioRun } from '../../types'
import styles from './ScenarioProgress.module.css'

/**
 * 进度条挂载后再铺开：初始 --p 为 0，下一帧切成真实进度，
 * 交给 transform 过渡吃，配合 transition-delay 做错峰生长。
 */
export function ScenarioProgress({ runs }: { runs: ScenarioRun[] }) {
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    const id = window.requestAnimationFrame(() => setMounted(true))
    return () => window.cancelAnimationFrame(id)
  }, [])

  return (
    <ol className={styles.list}>
      {runs.map((run, index) => (
        <li
          key={run.scenarioId}
          className={cx(styles.row, run.status === 'PASSED' && styles.done)}
          style={{ animationDelay: `${index * 55}ms` }}
        >
          <span className={styles.index}>{String(index + 1).padStart(2, '0')}</span>

          <span className={styles.body}>
            <span className={styles.top}>
              <span className={styles.name}>{run.name}</span>
              <span className={styles.platform}>{run.platform.id}</span>
              <span className={cx(styles.samples, 'tabular')}>
                注入 {formatInt(run.injectedSamples)}
              </span>
              <StatusPill status={run.status} dense />
            </span>

            <span className={styles.track}>
              <span
                className={styles.fill}
                style={
                  {
                    '--p': mounted ? run.progressPercent / 100 : 0,
                    transitionDelay: `${140 + index * 70}ms`,
                  } as CSSProperties
                }
              />
            </span>
          </span>

          <span className={cx(styles.pct, 'tabular')}>{run.progressPercent.toFixed(1)}%</span>
        </li>
      ))}
    </ol>
  )
}
