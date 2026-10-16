import { createContext } from 'react'
import type { ConsoleData } from '../types'

export type LoadState = 'loading' | 'ready' | 'error'

export interface ConsoleDataContextValue {
  state: LoadState
  data: ConsoleData | null
  source: 'fixture' | 'api'
  reload: () => void
}

export const ConsoleDataContext = createContext<ConsoleDataContextValue | null>(null)
