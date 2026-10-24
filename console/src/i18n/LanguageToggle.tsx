import { Languages } from 'lucide-react'

import { cx } from '../lib/cx'
import { LANGUAGES } from './messages'
import { useI18n } from './useI18n'
import styles from './LanguageToggle.module.css'

export function LanguageToggle() {
  const { language, setLanguage, t } = useI18n()

  return (
    <div className={styles.group} role="group" aria-label={t('language.aria')}>
      <Languages size={13} strokeWidth={1.8} aria-hidden="true" className={styles.icon} />
      {LANGUAGES.map((option) => {
        const active = language === option.value
        return (
          <button
            key={option.value}
            type="button"
            className={cx(styles.option, active && styles.active)}
            onClick={() => setLanguage(option.value)}
            aria-pressed={active}
            title={option.value === 'zh-CN' ? t('language.zh') : t('language.en')}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
