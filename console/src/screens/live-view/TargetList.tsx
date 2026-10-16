import { MonitorPlay } from 'lucide-react'

import { cx } from '../../lib/cx'
import { INSTANCE_LABELS } from '../../data/fixtures'
import type { LiveViewStatus, LiveViewTarget } from '../../types'
import { targetKey } from './targetKey'
import styles from './TargetList.module.css'

interface TargetListProps {
  targets: LiveViewTarget[]
  statusOf: (target: LiveViewTarget) => LiveViewStatus
  activeKey: string
  onView: (target: LiveViewTarget) => void
  onViewAll: () => void
}

export function TargetList({ targets, statusOf, activeKey, onView, onViewAll }: TargetListProps) {
  return (
    <div className={styles.wrap}>
      <div className={styles.head}>
        <span className={styles.count}>
          <strong className="tabular">{targets.length}</strong> 个目标
        </span>
        <button type="button" className={styles.bulk} onClick={onViewAll}>
          <MonitorPlay size={13} strokeWidth={1.9} aria-hidden="true" />
          全部拉流
        </button>
      </div>

      <ul className={styles.list}>
        {targets.map((target, index) => {
          const status = statusOf(target)
          const active = targetKey(target) === activeKey
          return (
            <li
              key={targetKey(target)}
              className={styles.item}
              style={{ animationDelay: `${index * 40}ms` }}
            >
              <div className={styles.info}>
                <span className={styles.instance}>
                  {INSTANCE_LABELS[target.instanceId] ?? target.instanceId}
                </span>
                <span className={styles.sub}>
                  <span className={styles.dot} data-status={status} aria-hidden="true" />
                  <span className={styles.status}>{status}</span>
                  <em className="tabular">{target.simId}</em>
                </span>
              </div>
              <button
                type="button"
                className={cx(styles.view, active && styles.viewOn)}
                aria-pressed={active}
                onClick={() => onView(target)}
              >
                {active ? '查看中' : '查看'}
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
