import { consoleData } from './fixtures'
import type { ConsoleData } from '../types'

export interface ConsoleDataResult {
  data: ConsoleData
  source: 'fixture' | 'api'
}

/**
 * 默认直接吃 fixtures；只有显式配了 VITE_API_BASE 才去打后端。
 * 后端不可用时静默回退 fixtures——控制台宁可展示样例，也不要白屏。
 */
export async function loadConsoleData(): Promise<ConsoleDataResult> {
  const base = import.meta.env.VITE_API_BASE
  if (!base) {
    return { data: consoleData, source: 'fixture' }
  }
  try {
    const response = await fetch(`${base.replace(/\/+$/, '')}/api/console`, {
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const payload = (await response.json()) as ConsoleData
    if (!payload || !payload.cards || !Array.isArray(payload.runs)) {
      throw new Error('响应结构不符合 ConsoleData 契约')
    }
    return { data: payload, source: 'api' }
  } catch (error) {
    console.warn('[laplace-console] /api/console 不可用，回退到 fixtures', error)
    return { data: consoleData, source: 'fixture' }
  }
}
