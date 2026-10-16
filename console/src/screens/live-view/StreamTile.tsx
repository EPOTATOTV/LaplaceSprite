import type { CSSProperties } from 'react'

import { INSTANCE_LABELS } from '../../data/fixtures'
import { cx } from '../../lib/cx'
import type { LiveViewStatus, LiveViewTarget, PerfOverlay } from '../../types'
import styles from './StreamTile.module.css'

const BUDGET_MS = 3000

function hashSeed(seed: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

/** 每个实例一套低饱和色相，画面彼此不同但同一实例每次渲染一致 */
function surfaceTones(key: string): { a: string; b: string } {
  const h = hashSeed(key)
  const hueA = h % 360
  const hueB = (hueA + 38 + ((h >>> 7) % 54)) % 360
  return {
    a: `hsl(${hueA} 40% 64% / 0.5)`,
    b: `hsl(${hueB} 36% 52% / 0.42)`,
  }
}

interface StreamTileProps {
  target: LiveViewTarget
  status: LiveViewStatus
  firstFrameMs: number
  perf: PerfOverlay | undefined
  index: number
  active: boolean
  onSelect: () => void
}

/** 没有真实流，就用分层的渐变 + 扫描线做"画面表面"；只有 LIVE 才动。 */
export function StreamTile({
  target,
  status,
  firstFrameMs,
  perf,
  index,
  active,
  onSelect,
}: StreamTileProps) {
  const key = `${target.instanceId}/${target.simId}`
  const tones = surfaceTones(key)
  const measured = firstFrameMs > 0
  const over = measured && firstFrameMs > BUDGET_MS
  const label = INSTANCE_LABELS[target.instanceId] ?? target.instanceId

  return (
    <button
      type="button"
      className={cx(styles.tile, active && styles.tileActive)}
      data-status={status}
      aria-pressed={active}
      aria-label={`${label} ${target.simId} 画面，状态 ${status}`}
      onClick={onSelect}
      style={
        {
          '--tone-a': tones.a,
          '--tone-b': tones.b,
          animationDelay: `${index * 45}ms`,
        } as CSSProperties
      }
    >
      <span className={styles.surface} aria-hidden="true">
        <span className={styles.layerA} />
        <span className={styles.layerB} />
        <span className={styles.scan} />
        <span className={styles.offline} />
      </span>

      <span className={styles.top}>
        <span className={styles.badge} data-status={status}>
          {status}
        </span>
        <span className={styles.budget} data-ok={measured && !over} data-over={over}>
          ≤3s {measured ? `${(firstFrameMs / 1000).toFixed(2)}s` : '—'}
        </span>
      </span>

      <span className={styles.meta}>
        <span className={styles.lineTop}>
          <span className={styles.instance}>{label}</span>
          <span className={styles.simId}>{target.simId}</span>
        </span>
        <span className={styles.lineBottom}>
          <span className={styles.net}>{target.networkCondition}</span>
          {perf && (
            <span className={styles.perfRow}>
              <span className="tabular">TPS {perf.tps.toFixed(1)}</span>
              <span className="tabular">{perf.latencyMs} ms</span>
            </span>
          )}
        </span>
      </span>
    </button>
  )
}
