import { Sun, Moon } from 'lucide-react'

import { cx } from '../../lib/cx'
import { useTheme } from './useTheme'
import styles from './ThemeToggle.module.css'

export function ThemeToggle({ className }: { className?: string }) {
  const { theme, toggleTheme } = useTheme()
  const next = theme === 'light' ? '暗色' : '亮色'
  return (
    <button
      type="button"
      className={cx(styles.toggle, className)}
      onClick={toggleTheme}
      aria-label={`切换到${next}主题`}
      title={`切换到${next}主题`}
      data-theme-state={theme}
    >
      <span className={styles.track} aria-hidden="true">
        <span className={styles.thumb}>
          {theme === 'light' ? (
            <Sun size={13} strokeWidth={1.9} />
          ) : (
            <Moon size={13} strokeWidth={1.9} />
          )}
        </span>
      </span>
    </button>
  )
}
