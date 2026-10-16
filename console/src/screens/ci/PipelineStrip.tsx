import { Check, Minus, X } from 'lucide-react'

import { cx } from '../../lib/cx'
import { formatDuration } from '../../lib/format'
import styles from './PipelineStrip.module.css'

export type CiStageStatus = 'pass' | 'fail' | 'running' | 'skipped'

export interface CiStage {
  id: string
  name: string
  detail: string
  status: CiStageStatus
  durationMs: number
}

function StageGlyph({ status }: { status: CiStageStatus }) {
  if (status === 'pass') return <Check size={13} strokeWidth={2.6} aria-hidden="true" />
  if (status === 'fail') return <X size={13} strokeWidth={2.6} aria-hidden="true" />
  if (status === 'skipped') return <Minus size={13} strokeWidth={2.6} aria-hidden="true" />
  return <span className={styles.runningDot} aria-hidden="true" />
}

const STATUS_LABEL: Record<CiStageStatus, string> = {
  pass: '通过',
  fail: '失败',
  running: '运行中',
  skipped: '跳过',
}

export function PipelineStrip({ stages, ariaLabel }: { stages: CiStage[]; ariaLabel: string }) {
  return (
    <ol className={styles.strip} aria-label={ariaLabel}>
      {stages.map((stage, index) => (
        <li
          key={stage.id}
          className={cx(styles.stage, styles[stage.status])}
          style={{ animationDelay: `${index * 64}ms` }}
        >
          <span className={styles.node}>
            <StageGlyph status={stage.status} />
          </span>
          <span className={styles.name}>{stage.name}</span>
          <span className={styles.detail}>{stage.detail}</span>
          <span className={styles.foot}>
            <span className={styles.status}>{STATUS_LABEL[stage.status]}</span>
            <span className={cx(styles.duration, 'tabular')}>
              {stage.status === 'skipped' ? '—' : formatDuration(stage.durationMs)}
            </span>
          </span>
        </li>
      ))}
    </ol>
  )
}
