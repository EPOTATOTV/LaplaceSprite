import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useRoutes } from 'react-router-dom'

import { useConsoleData } from '../data/useConsoleData'
import { useI18n } from '../i18n/useI18n'
import { cx } from '../lib/cx'
import { LeftRail } from './LeftRail'
import { NAV_ENTRIES } from './nav'
import type { NavEntry } from './nav'
import { appRoutes } from './routes'
import { TopBar } from './TopBar'
import { useRouteTransition } from './useRouteTransition'
import styles from './AppShell.module.css'

function resolveEntry(pathname: string): NavEntry {
  const matched = NAV_ENTRIES.find((entry) =>
    entry.path === '/' ? pathname === '/' : pathname.startsWith(entry.path),
  )
  if (matched) return matched
  return {
    ...(NAV_ENTRIES[0] as NavEntry),
    code: '—',
    labelKey: 'nav.fallback.label',
    hintKey: 'nav.fallback.hint',
  }
}

export function AppShell() {
  const live = useLocation()
  const { location: routeLocation, phase } = useRouteTransition(live)
  const element = useRoutes(appRoutes, routeLocation)
  const { state } = useConsoleData()
  const { t } = useI18n()
  const [collapsed, setCollapsed] = useState(false)
  const scroller = useRef<HTMLElement | null>(null)

  const entry = useMemo(() => resolveEntry(routeLocation.pathname), [routeLocation.pathname])

  useEffect(() => {
    scroller.current?.scrollTo({ top: 0, left: 0 })
  }, [routeLocation.key])

  return (
    <div className={styles.shell}>
      <LeftRail collapsed={collapsed} onToggle={() => setCollapsed((v) => !v)} />

      <div className={styles.main}>
        <TopBar entry={entry} />
        {state === 'loading' && <div className={styles.loadingRail} aria-hidden="true" />}

        <main
          className={styles.content}
          ref={scroller}
          aria-label={t('content.region', { label: t(entry.labelKey) })}
        >
          <div
            key={routeLocation.key}
            className={cx(styles.stage, phase === 'exit' ? styles.exit : styles.enter)}
          >
            {element}
          </div>
        </main>
      </div>
    </div>
  )
}
