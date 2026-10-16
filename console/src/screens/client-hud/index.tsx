import { useState } from 'react'

import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { useConsoleData } from '../../data/useConsoleData'
import type { ClientSettings, InjectorStatus } from '../../types'
import { HudViewport } from './HudViewport'
import { ModMenu } from './ModMenu'
import styles from './ClientHudScreen.module.css'

function hashSeed(seed: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

export default function ClientHudScreen() {
  const { data, state } = useConsoleData()
  const [settingsPatch, setSettingsPatch] = useState<Partial<ClientSettings>>({})
  const [injectorPatch, setInjectorPatch] = useState<Partial<InjectorStatus>>({})

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入客户端 HUD…</p>
      </Glass>
    )
  }

  const settings: ClientSettings = { ...data.clientSettings, ...settingsPatch }
  const injector: InjectorStatus = { ...data.injectorStatus, ...injectorPatch }

  const network = data.networkModels.find((model) => model.id === settings.networkModelRef)
  const scene = data.scenarioRuns.find((run) => run.status === 'RUNNING')?.name ?? data.scenarioRuns[0].name
  const seed = hashSeed(`${settings.behavior}|${settings.networkModelRef}`) % 1_000_000

  // 本地 flag 随参数变化：改行为 / 开注入 / 切网络条件，HUD 上的读数会立刻跟着动
  let localFlags = 1
  if (injector.cheatInjectionEnabled) localFlags += 5
  if (settings.behavior === 'MINE' || settings.behavior === 'PLACE') localFlags += 1
  if (settings.clickDistribution === 'butterfly') localFlags += 1
  if (settings.networkModelRef === 'wifi') localFlags += 1
  if (settings.networkModelRef === 'lte') localFlags += 2

  const networkIndex = Math.max(
    0,
    data.networkModels.findIndex((model) => model.id === settings.networkModelRef),
  )
  const reportedSeq =
    data.injectorStatus.reportedSeq +
    settings.behavior.length * 37 +
    networkIndex * 53 +
    (injector.cheatInjectionEnabled ? 4096 : 0) +
    (injector.probeStubActive ? 512 : 0) +
    (injector.evidenceRecording ? 128 : 0)

  return (
    <div className={styles.page}>
      <Section
        title="受测玩家视角"
        note="第一视角 HUD · 客户端仅展示本地结果"
        delay={0}
        className={styles.viewSection}
      >
        <HudViewport
          settings={settings}
          injector={injector}
          network={network}
          scene={scene}
          seed={seed}
          localFlags={localFlags}
          reportedSeq={reportedSeq}
        />
      </Section>

      <Section
        title="模组菜单"
        note="行为 / 作弊注入 / 网络条件 / 上报与证据"
        delay={90}
        className={styles.menuSection}
      >
        <ModMenu
          settings={settings}
          injector={injector}
          models={data.networkModels}
          probe={data.probeSnapshot}
          cheatSampleCount={data.cheatSpecCount}
          localFlags={localFlags}
          reportedSeq={reportedSeq}
          onSettings={(patch) => setSettingsPatch((current) => ({ ...current, ...patch }))}
          onInjector={(patch) => setInjectorPatch((current) => ({ ...current, ...patch }))}
        />
      </Section>
    </div>
  )
}
