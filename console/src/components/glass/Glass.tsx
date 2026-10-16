import { createElement } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { cx } from '../../lib/cx'
import type { Tone } from '../../lib/tone'
import styles from './Glass.module.css'

export type GlassLevel = 'rail' | 'panel' | 'inset' | 'chip'
export type GlassTag = 'div' | 'section' | 'aside' | 'header' | 'article' | 'li'

export interface GlassProps {
  level?: GlassLevel
  tone?: Tone
  /** 可交互表面：抬起 + 位移，不只是变色 */
  interactive?: boolean
  as?: GlassTag
  className?: string
  style?: CSSProperties
  label?: string
  children: ReactNode
}

export function Glass({
  level = 'panel',
  tone = 'neutral',
  interactive = false,
  as = 'div',
  className,
  style,
  label,
  children,
}: GlassProps) {
  return createElement(
    as,
    {
      className: cx(styles.glass, styles[level], styles[tone], interactive && styles.interactive, className),
      style,
      'aria-label': label,
    },
    children,
  )
}
