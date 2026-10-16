import { cx } from '../lib/cx'
import type { Tone } from '../lib/tone'
import styles from './Switch.module.css'

export interface SwitchProps {
  checked: boolean
  onChange: (next: boolean) => void
  label: string
  /** 只留轨道，文本转成无障碍名 */
  hideLabel?: boolean
  tone?: Tone
  disabled?: boolean
  className?: string
}

/**
 * 开关。做成单个 button[role=switch]，点击标签文字同样切换，
 * 避免 label 包裹 button 带来的焦点语义问题。
 */
export function Switch({
  checked,
  onChange,
  label,
  hideLabel = false,
  tone = 'accent',
  disabled = false,
  className,
}: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      className={cx(styles.switch, styles[tone], checked && styles.on, className)}
      onClick={() => onChange(!checked)}
    >
      <span className={styles.track} aria-hidden="true">
        <span className={styles.thumb} />
      </span>
      {hideLabel ? (
        <span className="visually-hidden">{label}</span>
      ) : (
        <span className={styles.label}>{label}</span>
      )}
    </button>
  )
}
