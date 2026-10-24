import { useContext } from 'react'

import { I18nContext } from './i18nContext'
import type { I18nContextValue } from './i18nContext'

export function useI18n(): I18nContextValue {
  const value = useContext(I18nContext)
  if (!value) throw new Error('useI18n 必须在 LanguageProvider 内使用')
  return value
}
