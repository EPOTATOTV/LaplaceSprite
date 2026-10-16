import { useMemo } from 'react'
import type { CSSProperties } from 'react'

import { cx } from '../lib/cx'
import styles from './PixelAvatar.module.css'

/**
 * 10 × 12 像素模板：h 头发 / s 皮肤 / e 眼睛 / c 上衣 / p 裤子。
 * 只用 SVG 方块拼，不引任何位图；同一 simId 每次渲染结果一致。
 */
const BODY_TEMPLATE = [
  '   hhhh   ',
  '  hhhhhh  ',
  '  hssssh  ',
  '  sesses  ',
  '  ssssss  ',
  ' sccccccs ',
  ' sccccccs ',
  ' sccccccs ',
  '  cccccc  ',
  '  pp  pp  ',
  '  pp  pp  ',
  '  pp  pp  ',
]

/** 前两行由发型变体决定，其余沿用模板 */
const HAIR_VARIANTS = [
  ['   hhhh   ', '  hhhhhh  '],
  ['  hhhhhh  ', ' hhhhhhhh '],
  ['  h h h h ', '  hhhhhh  '],
]

const SKIN = ['#f4d6bb', '#ecc09b', '#dca97e', '#c69067', '#a8784f', '#875e3d']
const HAIR = ['#2b2b33', '#4a3324', '#7a4b28', '#b5793a', '#c9c4bd', '#8a8f99', '#39496b']
const CLOTH = ['#2b4d86', '#1c6d51', '#8f201a', '#a97c1d', '#5b4a8a', '#3d68ac', '#2f6f7a']
const PANTS = ['#2f3542', '#3b3a4a', '#4a4034', '#2c3548']

/** FNV-1a：把 simId 摊成一个稳定的 32 位种子 */
function hashSeed(seed: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

export interface PixelAvatarProps {
  seed: string
  /** 像素图宽度（px），高度按 10:12 比例 */
  size?: number
  /** 覆盖上衣色，用于类型语义色 */
  accent?: string
  /** 外框描边色，用于类型语义圈选 */
  ring?: string
  className?: string
  label: string
}

export function PixelAvatar({
  seed,
  size = 26,
  accent,
  ring,
  className,
  label,
}: PixelAvatarProps) {
  const cells = useMemo(() => {
    const h = hashSeed(seed)
    const palette: Record<string, string> = {
      h: HAIR[(h >>> 5) % HAIR.length],
      s: SKIN[h % SKIN.length],
      e: '#20242e',
      c: accent ?? CLOTH[(h >>> 11) % CLOTH.length],
      p: PANTS[(h >>> 17) % PANTS.length],
    }
    const variant = HAIR_VARIANTS[(h >>> 23) % HAIR_VARIANTS.length]
    const rows = [variant[0], variant[1], ...BODY_TEMPLATE.slice(2)]

    const out: Array<{ x: number; y: number; color: string }> = []
    rows.forEach((row, y) => {
      for (let x = 0; x < row.length; x += 1) {
        const token = row[x]
        if (token === ' ' || !token) continue
        out.push({ x, y, color: palette[token] })
      }
    })
    return out
  }, [seed, accent])

  return (
    <span className={cx(styles.frame, className)} data-ring={ring ? 'on' : 'off'} style={{ '--ring': ring ?? 'transparent' } as CSSProperties}>
      <svg
        className={styles.svg}
        width={size}
        height={size * 1.2}
        viewBox="0 0 10 12"
        role="img"
        aria-label={label}
      >
        {cells.map((cell) => (
          <rect
            key={`${cell.x}-${cell.y}`}
            x={cell.x}
            y={cell.y}
            width={1.04}
            height={1.04}
            fill={cell.color}
          />
        ))}
      </svg>
    </span>
  )
}
