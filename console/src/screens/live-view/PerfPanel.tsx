import { Archive, Gauge, TrendingDown } from 'lucide-react'

import { cx } from '../../lib/cx'
import type { PerfOverlay } from '../../types'
import styles from './PerfPanel.module.css'

interface PerfPanelProps {
  instanceLabel: string
  simId: string
  perf: PerfOverlay | undefined
}

export function PerfPanel({ instanceLabel, simId, perf }: PerfPanelProps) {
  if (!perf) {
    return <p className={styles.empty}>未选择实例</p>
  }

  const knee = perf.kneeAlert
  const Icon = knee ? TrendingDown : Gauge

  return (
    <div className={styles.wrap}>
      <div className={styles.head}>
        <span className={styles.instance}>{instanceLabel}</span>
        <span className={styles.simId}>{simId}</span>
      </div>

      <dl className={styles.stats}>
        <div>
          <dt>TPS</dt>
          <dd className="tabular" data-tone={perf.tps >= 19.5 ? 'ok' : 'danger'}>
            {perf.tps.toFixed(2)}
          </dd>
        </div>
        <div>
          <dt>MSPT</dt>
          <dd className="tabular" data-tone={perf.mspt < 50 ? 'ok' : 'danger'}>
            {perf.mspt.toFixed(1)}
          </dd>
        </div>
        <div>
          <dt>延迟</dt>
          <dd className="tabular">{perf.latencyMs}</dd>
        </div>
      </dl>

      <div className={cx(styles.knee, knee ? styles.kneeOn : styles.kneeOff)}>
        <span className={styles.kneeIcon} aria-hidden="true">
          <Icon size={16} strokeWidth={1.9} />
        </span>
        <div className={styles.kneeText}>
          <strong>{knee ? '压测拐点' : '运行平稳'}</strong>
          <span>
            {knee
              ? perf.recordingTriggered
                ? 'TPS 或 MSPT 越界，已触发录制并归档'
                : 'TPS 或 MSPT 越界，已触发录制'
              : 'TPS 与 MSPT 均在阈值内'}
          </span>
        </div>
      </div>

      {perf.recordingTriggered && (
        <p className={styles.recording}>
          <Archive size={12} strokeWidth={2} aria-hidden="true" />
          录制任务已写入 EvidenceVault · 12.5 拐点归档
        </p>
      )}
    </div>
  )
}
