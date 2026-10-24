import { createContext } from 'react'

import type { LanguageName, MessageKey } from './messages'

export interface I18nContextValue {
  language: LanguageName
  setLanguage: (language: LanguageName) => void
  t: (key: MessageKey, params?: Record<string, string | number>) => string
}

export const I18nContext = createContext<I18nContextValue | null>(null)
