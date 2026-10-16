import { useContext } from 'react'
import { ConsoleDataContext } from './consoleDataContext'
import type { ConsoleDataContextValue } from './consoleDataContext'

export function useConsoleData(): ConsoleDataContextValue {
  const value = useContext(ConsoleDataContext)
  if (!value) throw new Error('useConsoleData 必须在 ConsoleDataProvider 内使用')
  return value
}
