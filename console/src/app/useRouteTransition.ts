import { useEffect, useRef, useState } from 'react'
import type { Location } from 'react-router-dom'

/** 与 AppShell.module.css 里的 lc-page-out 时长对齐 */
export const PAGE_EXIT_MS = 190

export interface RouteTransitionState {
  /** 当前正在渲染的路由（退场期间仍是旧路由） */
  location: Location
  phase: 'enter' | 'exit'
}

/**
 * 让路由切换有退场：先把旧屏淡出，再换上新屏进入。
 * react-router 默认是硬切，这里自己留一段重叠窗口。
 */
export function useRouteTransition(location: Location): RouteTransitionState {
  const [rendered, setRendered] = useState<Location>(location)
  const [phase, setPhase] = useState<'enter' | 'exit'>('enter')
  const latest = useRef(location)

  useEffect(() => {
    latest.current = location
  }, [location])

  useEffect(() => {
    if (location.key === rendered.key) return
    setPhase('exit')
    const timer = window.setTimeout(() => {
      setRendered(latest.current)
      setPhase('enter')
    }, PAGE_EXIT_MS)
    return () => window.clearTimeout(timer)
  }, [location, rendered.key])

  return { location: rendered, phase }
}
