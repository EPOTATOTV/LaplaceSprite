import { Archive, Flag, Fingerprint } from 'lucide-react'

import { cx } from '../../lib/cx'
import { formatTimestamp } from '../../lib/format'
import type { InspectSession, InspectStatus, SimPlayer } from '../../types'
import { InspectStatusChip } from './InspectStatusChip'
import styles from './InspectPanel.module.css'

interface InspectPanelProps {
  sessions: InspectSession[]
  players: SimPlayer[]
  selectedSimId: string
  statusOf: (session: InspectSession) => InspectStatus
  onSelect: (simId: string) => void
}

export function InspectPanel({
  sessions,
  players,
  selectedSimId,
  statusOf,
  onSelect,
}: InspectPanelProps) {
  const active = sessions.find((session) => session.simId === selectedSimId) ?? sessions[0]
  const subject = players.find((player) => player.simId === active.simId)

  return (
    <div className={styles.wrap}>
      <div className={styles.tabs} role="group" aria-label="查端会话列表">
        {sessions.map((session) => {
          const on = session.sessionId === active.sessionId
          return (
            <button
              key={session.sessionId}
              type="button"
              className={cx(styles.tab, on && styles.tabOn)}
              aria-pressed={on}
              onClick={() => onSelect(session.simId)}
            >
              <span className={cx(styles.tabId, 'tabular')}>{session.sessionId}</span>
              <InspectStatusChip status={statusOf(session)} />
            </button>
          )
        })}
      </div>

      <dl className={styles.detail}>
        <div>
          <dt>会话</dt>
          <dd className="tabular">{active.sessionId}</dd>
        </div>
        <div>
          <dt>对象</dt>
          <dd>{subject ? `${subject.name} · ${active.simId}` : active.playerId}</dd>
        </div>
        <div>
          <dt>开启</dt>
          <dd className="tabular">{formatTimestamp(active.openedAt)}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>
            <InspectStatusChip status={statusOf(active)} />
          </dd>
        </div>
      </dl>

      <div className={styles.block}>
        <span className={styles.blockLabel}>
          <Flag size={12} strokeWidth={2} aria-hidden="true" />
          关联警告
        </span>
        <div className={styles.chips}>
          {active.relatedFlags.length === 0 && <span className={styles.empty}>无</span>}
          {active.relatedFlags.map((flag, index) => (
            <span key={flag} className={styles.flagChip} style={{ animationDelay: `${index * 45}ms` }}>
              {flag}
            </span>
          ))}
        </div>
      </div>

      <div className={styles.block}>
        <span className={styles.blockLabel}>
          <Archive size={12} strokeWidth={2} aria-hidden="true" />
          证据引用
        </span>
        <ul className={styles.evidence}>
          {active.evidenceRefs.length === 0 && <li className={styles.empty}>无</li>}
          {active.evidenceRefs.map((ref, index) => (
            <li
              key={ref}
              className={styles.evidenceItem}
              style={{ animationDelay: `${index * 45}ms` }}
            >
              <Fingerprint size={12} strokeWidth={2} aria-hidden="true" />
              <span className="tabular">{ref}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
