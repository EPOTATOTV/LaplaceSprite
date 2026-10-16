import { ConsoleDataProvider } from '../data/ConsoleDataProvider'
import { AppShell } from './AppShell'
import { ThemeProvider } from './theme/ThemeProvider'

export default function App() {
  return (
    <ThemeProvider>
      <ConsoleDataProvider>
        <AppShell />
      </ConsoleDataProvider>
    </ThemeProvider>
  )
}
