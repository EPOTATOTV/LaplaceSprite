import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { I18nContext } from './i18nContext'
import type { I18nContextValue } from './i18nContext'
import { translate } from './messages'
import type { LanguageName, MessageKey } from './messages'

const STORAGE_KEY = 'laplace-console-language'

function initialLanguage(): LanguageName {
  if (typeof window === 'undefined') return 'zh-CN'
  const saved = window.localStorage.getItem(STORAGE_KEY)
  return saved === 'en-US' ? 'en-US' : 'zh-CN'
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<LanguageName>(initialLanguage)

  useEffect(() => {
    document.documentElement.lang = language
  }, [language])

  const setLanguage = useCallback((next: LanguageName) => {
    setLanguageState(next)
    window.localStorage.setItem(STORAGE_KEY, next)
  }, [])

  const t = useCallback(
    (key: MessageKey, params?: Record<string, string | number>) => translate(language, key, params),
    [language],
  )

  const value = useMemo<I18nContextValue>(
    () => ({ language, setLanguage, t }),
    [language, setLanguage, t],
  )

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
