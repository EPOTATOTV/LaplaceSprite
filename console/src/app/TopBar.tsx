import { Activity, Radio, RefreshCw, ShieldCheck } from 'lucide-react'

import { useConsoleData } from '../data/useConsoleData'
import { cx } from '../lib/cx'
import type { NavEntry } from './nav'
import { ThemeToggle } from './theme/ThemeToggle'
import styles from './TopBar.module.css'

function TopStat({
  icon: Icon,
  label,
  value,
  tone = 'neutral',
}: {
  icon: typeof Radio
  label: string
  value: string
  tone?: 'neutral' | 'accent' | 'ok' | 'danger'
}) {
  return (
    <div className={cx(styles.stat, styles[tone])} title={`${label}：${value}`}>
      <Icon size={14} strokeWidth={1.8} aria-hidden="true" />
      <span className={styles.statLabel}>{label}</span>
      <span className={cx(styles.statValue, 'tabular')}>{value}</span>
    </div>
  )
}

export function TopBar({ entry }: { entry: NavEntry }) {
  const { data, state, reload } = useConsoleData()

  const liveCount = data ? data.liveView.sessions.filter((s) => s.share.status === 'LIVE').length : 0
  const brokenModules = data ? data.modules.filter((m) => m.status !== 'PASSED').length : 0
  const gate = data ? data.gate : null

  return (
    <header className={styles.topbar}>
      <div className={styles.leading}>
        <span className={cx(styles.code, 'tabular')}>{entry.code}</span>
        <h1 key={entry.path} className={styles.title}>
          {entry.label}
        </h1>
        <span className={styles.hint}>{entry.hint}</span>
      </div>

      <div className={styles.stats} role="status" aria-live="polite">
        <TopStat
          icon={Radio}
          label="直播"
          value={data ? `${liveCount}/${data.liveView.sessions.length}` : '—'}
          tone={liveCount > 0 ? 'accent' : 'neutral'}
        />
        <TopStat
          icon={Activity}
          label="模块异常"
          value={data ? String(brokenModules) : '—'}
          tone={brokenModules > 0 ? 'danger' : 'ok'}
        />
        <TopStat
          icon={ShieldCheck}
          label="门禁"
          value={gate ? (gate.passed ? '通过' : `${gate.failedMetrics.length} 项超限`) : '—'}
          tone={gate ? (gate.passed ? 'ok' : 'danger') : 'neutral'}
        />
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          className={styles.iconBtn}
          onClick={reload}
          disabled={state === 'loading'}
          aria-label="重新拉取控制台数据"
          title="重新拉取控制台数据"
        >
          <RefreshCw size={15} strokeWidth={1.9} aria-hidden="true" />
        </button>
        <ThemeToggle />
      </div>
    </header>
  )
}
