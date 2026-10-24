import { ConsoleDataProvider } from '../data/ConsoleDataProvider'
import { LanguageProvider } from '../i18n/LanguageProvider'
import { AppShell } from './AppShell'
import { ThemeProvider } from './theme/ThemeProvider'

export default function App() {
  return (
    <ThemeProvider>
      <LanguageProvider>
        <ConsoleDataProvider>
          <AppShell />
        </ConsoleDataProvider>
      </LanguageProvider>
    </ThemeProvider>
  )
}
