import { StatusPill } from '../../components/StatusPill'
import { formatInt } from '../../lib/format'
import type { ModuleHealth } from '../../types'
import styles from './ModuleRail.module.css'

/** 7 个整合包模块：左侧一条状态色带 + 计数，不做成卡片网格。 */
export function ModuleRail({ modules }: { modules: ModuleHealth[] }) {
  return (
    <ol className={styles.list}>
      {modules.map((item, index) => (
        <li
          key={item.module}
          className={styles.row}
          data-status={item.status}
          style={{ animationDelay: `${index * 45}ms` }}
        >
          <span className={styles.rail} aria-hidden="true" />
          <div className={styles.body}>
            <div className={styles.top}>
              <span className={styles.name}>{item.module}</span>
              <span className={styles.version}>{item.version}</span>
              <StatusPill status={item.status} dense />
            </div>
            <div className={styles.counters}>
              {Object.entries(item.counters).map(([key, value]) => (
                <span key={key} className={styles.counter}>
                  <em>{key}</em>
                  <strong className="tabular">{formatInt(value)}</strong>
                </span>
              ))}
            </div>
          </div>
        </li>
      ))}
    </ol>
  )
}
