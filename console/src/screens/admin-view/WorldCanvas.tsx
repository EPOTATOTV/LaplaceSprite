import { useMemo } from 'react'

import { cx } from '../../lib/cx'
import type { SimPlayer } from '../../types'
import { PLAYER_LABEL, PLAYER_ORDER } from './playerVisual'
import styles from './WorldCanvas.module.css'

const COLS = 4
const ROWS = 4

function hashSeed(seed: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

interface WorldCanvasProps {
  players: SimPlayer[]
  selectedSimId: string
  onSelect: (simId: string) => void
}

/** 场景不是位图：地平线分层 + 透视网格 + 渐隐暗角，全是 CSS 渐变。 */
export function WorldCanvas({ players, selectedSimId, onSelect }: WorldCanvasProps) {
  const placed = useMemo(
    () =>
      players.map((player, index) => {
        const h = hashSeed(player.simId)
        const col = index % COLS
        const row = Math.floor(index / COLS) % ROWS
        // 在格心附近抖动，既错落又不会互相压住
        const jx = ((h >>> 3) % 44) / 100 - 0.22
        const jy = ((h >>> 11) % 44) / 100 - 0.22
        return {
          player,
          x: ((col + 0.5 + jx) / COLS) * 100,
          y: ((row + 0.5 + jy) / ROWS) * 100,
        }
      }),
    [players],
  )

  return (
    <div className={styles.canvas}>
      <span className={styles.sky} aria-hidden="true" />
      <span className={styles.ground} aria-hidden="true" />
      <span className={styles.grid} aria-hidden="true" />
      <span className={styles.vignette} aria-hidden="true" />

      <div className={styles.markers}>
        {placed.map(({ player, x, y }, index) => {
          const active = player.simId === selectedSimId
          return (
            <button
              key={player.simId}
              type="button"
              className={cx(styles.marker, active && styles.markerOn)}
              data-type={player.type}
              style={{ left: `${x}%`, top: `${y}%`, animationDelay: `${120 + index * 45}ms` }}
              onClick={() => onSelect(player.simId)}
              aria-pressed={active}
              title={`${player.name} · ${PLAYER_LABEL[player.type]} · ${player.simId}`}
            >
              <span className={styles.pin} aria-hidden="true">
                <span className={styles.core} />
              </span>
              <span className={styles.label}>{player.name}</span>
            </button>
          )
        })}
      </div>

      <div className={styles.legend} role="group" aria-label="玩家类型图例">
        {PLAYER_ORDER.map((type) => (
          <span key={type} className={styles.legendItem} data-type={type}>
            <span className={styles.legendDot} aria-hidden="true" />
            {PLAYER_LABEL[type]}
          </span>
        ))}
      </div>
    </div>
  )
}
