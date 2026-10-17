import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { ConsoleDataContext } from './consoleDataContext'
import type { ConsoleDataContextValue, LoadState } from './consoleDataContext'
import { loadConsoleData } from './api'
import type { ConsoleData } from '../types'

/** 首次进入时留一点余量，让入场动画有机会跑完而不是闪一下 */
const MIN_DWELL_MS = 260

export function ConsoleDataProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<LoadState>('loading')
  const [data, setData] = useState<ConsoleData | null>(null)
  const [source, setSource] = useState<'fixture' | 'api'>('fixture')
  const [nonce, setNonce] = useState(0)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const started = Date.now()
    const settle = (next: ConsoleData, nextSource: 'fixture' | 'api'): void => {
      const wait = Math.max(0, MIN_DWELL_MS - (Date.now() - started))
      window.setTimeout(() => {
        if (cancelled) return
        setData(next)
        setSource(nextSource)
        setState('ready')
      }, wait)
    }
    loadConsoleData()
      .then((result) => settle(result.data, result.source))
      .catch(() => {
        if (!cancelled) setState('error')
      })
    return () => {
      cancelled = true
    }
  }, [nonce])

  const reload = useCallback(() => {
    setState('loading')
    setNonce((n) => n + 1)
  }, [])

  const value = useMemo<ConsoleDataContextValue>(
    () => ({ state, data, source, reload }),
    [state, data, source, reload],
  )

  return <ConsoleDataContext.Provider value={value}>{children}</ConsoleDataContext.Provider>
}
