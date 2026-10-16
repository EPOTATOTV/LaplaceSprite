import { useState } from 'react'
import { ChevronDown, ChevronUp, RadioTower } from 'lucide-react'

import { cx } from '../../lib/cx'
import type { LiveViewSession } from '../../types'
import styles from './SignalDrawer.module.css'

const BUDGET_MS = 3000

export function SignalDrawer({ session }: { session: LiveViewSession | undefined }) {
  const [open, setOpen] = useState(true)

  if (!session) {
    return <p className={styles.empty}>未选择会话</p>
  }

  const share = session.share
  const measured = session.timeToFirstFrameMs > 0
  const over = measured && session.timeToFirstFrameMs > BUDGET_MS
  const ratio = measured ? Math.min(1, session.timeToFirstFrameMs / BUDGET_MS) : 0

  return (
    <div className={styles.wrap}>
      <div className={styles.head}>
        <span className={cx(styles.session, 'tabular')}>{session.sessionId}</span>
        <span className={styles.channel} title={share.signalingChannel}>
          {share.signalingChannel}
        </span>
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={open}
          onClick={() => setOpen((value) => !value)}
        >
          {open ? '收起' : '展开'}
          {open ? (
            <ChevronUp size={13} strokeWidth={2.2} aria-hidden="true" />
          ) : (
            <ChevronDown size={13} strokeWidth={2.2} aria-hidden="true" />
          )}
        </button>
      </div>

      <div className={styles.budget}>
        <span className={styles.budgetLabel}>
          <RadioTower size={12} strokeWidth={2} aria-hidden="true" />
          ≤3s 出画
        </span>
        <span className={styles.budgetTrack} aria-hidden="true">
          <span
            className={cx(styles.budgetFill, over && styles.budgetOver)}
            style={{ transform: `scaleX(${ratio})` }}
          />
        </span>
        <span className={styles.budgetValue} data-over={over}>
          {measured ? `${(session.timeToFirstFrameMs / 1000).toFixed(2)}s` : '—'}
        </span>
      </div>

      {open && (
        <div className={styles.body}>
          <div className={styles.row}>
            <span className={styles.key}>SDP offer</span>
            <code className={styles.value}>{share.offerSdp || '—'}</code>
          </div>
          <div className={styles.row}>
            <span className={styles.key}>SDP answer</span>
            <code className={styles.value}>{share.answerSdp || '—'}</code>
          </div>
          <div className={styles.row}>
            <span className={styles.key}>ICE 候选</span>
            <code className={styles.value}>
              {share.iceCandidates.length > 0 ? share.iceCandidates.join('\n') : '—'}
            </code>
          </div>
          <div className={styles.row}>
            <span className={styles.key}>WSS 通道</span>
            <code className={styles.value}>{share.signalingChannel}</code>
          </div>
          <p className={styles.note}>
            协议形态对齐 通用查端协议 inspect_offer / inspect_answer / inspect_ice，由 Laplace 侧独立实现。
          </p>
        </div>
      )}
    </div>
  )
}
