import type { PlayerType } from '../../types'

/**
 * 11.1 语义色：合法绿 / 作弊红 / 管理员紫。
 * 绿与红复用状态令牌，紫是语义色，定义在 AdminViewScreen.module.css 的 --player-* 上。
 */
export const PLAYER_RING: Record<PlayerType, string> = {
  LEGIT: 'var(--player-legit)',
  CHEAT: 'var(--player-cheat)',
  ADMIN: 'var(--player-admin)',
}

export const PLAYER_LABEL: Record<PlayerType, string> = {
  LEGIT: '合法',
  CHEAT: '作弊',
  ADMIN: '管理员',
}

export const PLAYER_ORDER: readonly PlayerType[] = ['LEGIT', 'CHEAT', 'ADMIN']
