import { cx } from '../lib/cx'
import type { Tone } from '../lib/tone'
import type { RunStatus } from '../types'
import styles from './StatusPill.module.css'

const STATUS_TONE: Record<RunStatus, Tone> = {
  QUEUED: 'neutral',
  RUNNING: 'accent',
  PASSED: 'ok',
  FAILED: 'danger',
  ABORTED: 'danger',
  SKIPPED: 'neutral',
}

const STATUS_LABEL: Record<RunStatus, string> = {
  QUEUED: '排队',
  RUNNING: '运行中',
  PASSED: '通过',
  FAILED: '失败',
  ABORTED: '中止',
  SKIPPED: '跳过',
}

export function StatusPill({ status, dense = false }: { status: RunStatus; dense?: boolean }) {
  return (
    <span
      className={cx(styles.pill, styles[STATUS_TONE[status]], dense && styles.dense)}
      data-status={status}
    >
      <span className={styles.dot} aria-hidden="true" />
      {STATUS_LABEL[status]}
    </span>
  )
}
