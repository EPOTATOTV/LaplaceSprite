import { Activity, MemoryStick, Plug, Users } from 'lucide-react'
import type { CSSProperties } from 'react'

import { ArcGauge } from '../../components/ArcGauge'
import { Glass } from '../../components/glass/Glass'
import { StatusPill } from '../../components/StatusPill'
import { cx } from '../../lib/cx'
import type { Tone } from '../../lib/tone'
import type { ServerHealth } from '../../types'
import styles from './InstanceCard.module.css'

const HEALTHY_TPS = 19.5

/** 12.3 健康阈值：TPS ≥19.5、MSPT <50ms、堆 ≤70% */
function healthOf(health: ServerHealth): { healthy: boolean; memoryPct: number } {
  const memoryPct = health.memoryMaxMb === 0 ? 0 : (health.memoryUsedMb * 100) / health.memoryMaxMb
  return {
    healthy: health.tps >= HEALTHY_TPS && health.mspt < 50 && memoryPct <= 70,
    memoryPct,
  }
}

function gb(mb: number): string {
  return (mb / 1024).toFixed(1)
}

interface InstanceCardProps {
  health: ServerHealth
  label: string
  hero?: boolean
  delay?: number
  focused?: boolean
  onFocus?: () => void
}

export function InstanceCard({
  health,
  label,
  hero = false,
  delay = 0,
  focused = false,
  onFocus,
}: InstanceCardProps) {
  const { healthy, memoryPct } = healthOf(health)
  const tone: Tone = healthy ? 'ok' : memoryPct > 82 || health.tps < 19 ? 'danger' : 'warn'

  const gauge = (
    <ArcGauge
      value={health.tps}
      max={20}
      healthyFrom={HEALTHY_TPS}
      size={hero ? 196 : 96}
      readout={hero ? 'lg' : 'sm'}
      caption="TPS"
      delay={delay}
      ariaLabel={`${label} 当前 TPS ${health.tps.toFixed(2)}，健康下限 ${HEALTHY_TPS}`}
    />
  )

  const memory = (
    <div className={styles.memory} title={`堆占用 ${memoryPct.toFixed(1)}%`}>
      <span className={styles.memoryTrack}>
        <span
          className={cx(styles.memoryFill, memoryPct > 70 && styles.memoryHot)}
          style={{ transform: `scaleX(${Math.min(1, memoryPct / 100)})` }}
        />
      </span>
      <em className={cx(styles.memoryLabel, 'tabular')}>{memoryPct.toFixed(0)}%</em>
    </div>
  )

  if (hero) {
    return (
      <Glass
        level="panel"
        tone={tone}
        className={styles.hero}
        style={{ animationDelay: `${delay}ms` } as CSSProperties}
      >
        <div className={styles.head}>
          <span className={styles.name}>{label}</span>
          <span className={styles.platform}>{health.platform.id}</span>
          <StatusPill status={health.status} dense />
        </div>

        <div className={styles.gaugeWrap}>{gauge}</div>

        <ul className={styles.metrics}>
          <li>
            <Activity size={13} strokeWidth={1.9} aria-hidden="true" />
            <span>MSPT</span>
            <strong className="tabular">{health.mspt.toFixed(1)} ms</strong>
          </li>
          <li>
            <MemoryStick size={13} strokeWidth={1.9} aria-hidden="true" />
            <span>内存</span>
            <strong className="tabular">
              {gb(health.memoryUsedMb)} / {gb(health.memoryMaxMb)} GB
            </strong>
          </li>
          <li>
            <Users size={13} strokeWidth={1.9} aria-hidden="true" />
            <span>玩家</span>
            <strong className="tabular">
              {health.players} 真实 · {health.simulatedPlayers} 模拟
            </strong>
          </li>
          <li>
            <Plug size={13} strokeWidth={1.9} aria-hidden="true" />
            <span>插件</span>
            <strong className="tabular">{health.pluginVersion}</strong>
          </li>
        </ul>

        {memory}
      </Glass>
    )
  }

  return (
    <Glass as="li" level="inset" tone={tone} interactive className={styles.compact}>
      <button
        type="button"
        className={styles.compactInner}
        onClick={onFocus}
        aria-pressed={focused}
        aria-label={`把 ${label} 设为焦点实例`}
      >
        <span className={styles.compactGauge}>{gauge}</span>

        <span className={styles.compactHead}>
          <span className={styles.name}>{label}</span>
          <span className={styles.platform}>{health.platform.id}</span>
        </span>

        <span className={styles.compactStats}>
          <span className={styles.stat}>
            <em>MSPT</em>
            <strong className="tabular">{health.mspt.toFixed(1)}</strong>
          </span>
          <span className={styles.stat}>
            <em>模拟</em>
            <strong className="tabular">{health.simulatedPlayers}</strong>
          </span>
          <span className={styles.stat}>
            <em>插件</em>
            <strong className="tabular">{health.pluginVersion}</strong>
          </span>
        </span>

        <span className={styles.compactTail}>
          {memory}
          <StatusPill status={health.status} dense />
        </span>
      </button>
    </Glass>
  )
}
