import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { ChevronsLeft, ChevronsRight } from 'lucide-react'

import { Glass } from '../components/glass/Glass'
import { useConsoleData } from '../data/useConsoleData'
import { cx } from '../lib/cx'
import { NAV_ENTRIES } from './nav'
import styles from './LeftRail.module.css'

function BrandMark() {
  return (
    <svg viewBox="0 0 32 32" className={styles.mark} role="img" aria-label="Laplace Sprite">
      <defs>
        <linearGradient id="lc-mark" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#3d68ac" />
          <stop offset="55%" stopColor="#1f3765" />
          <stop offset="100%" stopColor="#0f1b33" />
        </linearGradient>
      </defs>
      <rect x="1" y="1" width="30" height="30" rx="10" fill="url(#lc-mark)" />
      <path
        d="M8 21.4c0-6.2 3.4-10.4 8-10.4s8 4.2 8 10.4"
        fill="none"
        stroke="rgba(255,255,255,0.5)"
        strokeWidth="1.3"
      />
      <circle cx="16" cy="11" r="2.5" fill="#ffffff" />
      <circle cx="16" cy="21.4" r="1.4" fill="rgba(255,255,255,0.75)" />
    </svg>
  )
}

export function LeftRail({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const location = useLocation()
  const { source, state } = useConsoleData()
  const listRef = useRef<HTMLUListElement | null>(null)
  const itemRefs = useRef<Array<HTMLLIElement | null>>([])
  const [marker, setMarker] = useState({ top: 0, height: 46 })

  const activeIndex = useMemo(() => {
    const found = NAV_ENTRIES.findIndex((entry) =>
      entry.path === '/' ? location.pathname === '/' : location.pathname.startsWith(entry.path),
    )
    return found < 0 ? -1 : found
  }, [location.pathname])

  const measure = useCallback(() => {
    if (activeIndex < 0) return
    const el = itemRefs.current[activeIndex]
    if (!el) return
    setMarker({ top: el.offsetTop, height: el.offsetHeight })
  }, [activeIndex])

  // 列表尺寸一变（折叠、字体加载、窗口缩放）就重量一次，避免指示器飘掉
  useLayoutEffect(() => {
    const list = listRef.current
    measure()
    if (!list) return
    const observer = new ResizeObserver(measure)
    observer.observe(list)
    window.addEventListener('resize', measure)
    return () => {
      observer.disconnect()
      window.removeEventListener('resize', measure)
    }
  }, [measure])

  return (
    <Glass
      as="aside"
      level="rail"
      className={cx(styles.rail, collapsed && styles.collapsed)}
      label="主导航栏"
    >
      <div className={styles.brand}>
        <BrandMark />
        <div className={styles.brandText}>
          <span className={styles.brandName}>Laplace Console</span>
          <span className={styles.brandSub}>拉普拉斯精灵 · 测试平台</span>
        </div>
      </div>

      <nav aria-label="控制台导航">
        <ul className={styles.navList} ref={listRef}>
          {NAV_ENTRIES.map((entry, index) => {
            const Icon = entry.icon
            return (
              <li
                key={entry.path}
                ref={(node) => {
                  itemRefs.current[index] = node
                }}
                className={styles.navSlot}
              >
                <NavLink
                  to={entry.path}
                  end={entry.path === '/'}
                  className={({ isActive }) => cx(styles.navItem, isActive && styles.navActive)}
                  title={collapsed ? `${entry.code} ${entry.label}` : undefined}
                >
                  <span className={styles.navIcon}>
                    <Icon size={17} strokeWidth={1.7} aria-hidden="true" />
                  </span>
                  <span className={styles.navBody}>
                    <span className={styles.navLabel}>{entry.label}</span>
                    <span className={styles.navHint}>{entry.hint}</span>
                  </span>
                  <span className={cx(styles.navCode, 'tabular')}>{entry.code}</span>
                  {!entry.ready && <span className={styles.navFlag} aria-hidden="true" />}
                </NavLink>
              </li>
            )
          })}
          {activeIndex >= 0 && (
            <li
              aria-hidden="true"
              className={styles.marker}
              style={{ transform: `translateY(${marker.top}px)`, height: marker.height }}
            />
          )}
        </ul>
      </nav>

      <div className={styles.footer}>
        <div className={styles.sourceChip} data-source={source}>
          <span className={styles.sourceDot} aria-hidden="true" />
          <span className={styles.sourceText}>
            {state === 'loading' ? '同步中' : source === 'api' ? '实时接口' : '样例数据'}
          </span>
        </div>
        <button
          type="button"
          className={styles.collapseBtn}
          onClick={onToggle}
          aria-label={collapsed ? '展开导航栏' : '收起导航栏'}
          aria-expanded={!collapsed}
          title={collapsed ? '展开导航栏' : '收起导航栏'}
        >
          {collapsed ? (
            <ChevronsRight size={16} strokeWidth={1.8} aria-hidden="true" />
          ) : (
            <ChevronsLeft size={16} strokeWidth={1.8} aria-hidden="true" />
          )}
        </button>
      </div>
    </Glass>
  )
}
