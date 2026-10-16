import { useMemo } from 'react'

import { PixelAvatar } from '../../components/PixelAvatar'
import { cx } from '../../lib/cx'
import type { PlayerType, SimPlayer } from '../../types'
import { PLAYER_LABEL, PLAYER_ORDER, PLAYER_RING } from './playerVisual'
import styles from './PlayerRoster.module.css'

interface PlayerRosterProps {
  players: SimPlayer[]
  selectedSimId: string
  onSelect: (simId: string) => void
}

export function PlayerRoster({ players, selectedSimId, onSelect }: PlayerRosterProps) {
  const counts = useMemo(() => {
    const acc: Record<PlayerType, number> = { LEGIT: 0, CHEAT: 0, ADMIN: 0 }
    players.forEach((player) => {
      acc[player.type] += 1
    })
    return acc
  }, [players])

  return (
    <div className={styles.wrap}>
      <div className={styles.summary} role="list" aria-label="名单类型小计">
        {PLAYER_ORDER.map((type) => (
          <span key={type} role="listitem" className={styles.summaryItem} data-type={type}>
            <strong className="tabular">{counts[type]}</strong>
            {PLAYER_LABEL[type]}
          </span>
        ))}
        <span className={styles.summaryNote}>共 {players.length} 人在场</span>
      </div>

      <ul className={styles.list}>
        {players.map((player, index) => {
          const active = player.simId === selectedSimId
          return (
            <li key={player.simId}>
              <button
                type="button"
                className={cx(styles.row, active && styles.rowOn)}
                data-type={player.type}
                style={{ animationDelay: `${index * 30}ms` }}
                aria-pressed={active}
                onClick={() => onSelect(player.simId)}
              >
                <PixelAvatar
                  seed={player.simId}
                  size={20}
                  ring={PLAYER_RING[player.type]}
                  label={`${player.name} 的像素头像`}
                />
                <span className={styles.who}>
                  <span className={styles.name}>{player.name}</span>
                  <span className={cx(styles.simId, 'tabular')}>{player.simId}</span>
                </span>
                <span className={styles.behavior}>
                  {player.behavior ?? player.cheatSpecId ?? '管理员旁观'}
                </span>
                <span className={styles.type}>{PLAYER_LABEL[player.type]}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
