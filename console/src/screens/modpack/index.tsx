import { useState } from 'react'

import { Glass } from '../../components/glass/Glass'
import { LogStream } from '../../components/LogStream'
import { Section } from '../../components/Section'
import { useConsoleData } from '../../data/useConsoleData'
import type { ServerPluginConfig } from '../../types'
import { ModuleRail } from './ModuleRail'
import { PluginConfigPanel } from './PluginConfigPanel'
import styles from './ModpackScreen.module.css'

export default function ModpackScreen() {
  const { data, state } = useConsoleData()
  const [configPatch, setConfigPatch] = useState<Partial<ServerPluginConfig>>({})

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入整合包面板…</p>
      </Glass>
    )
  }

  const config: ServerPluginConfig = { ...data.pluginConfig, ...configPatch }

  return (
    <div className={styles.page}>
      <Section
        title="受测插件配置"
        note="服务端模式默认仅告警 · 踢出与封禁默认关闭"
        delay={0}
        className={styles.configSection}
      >
        <PluginConfigPanel
          config={config}
          onChange={(patch) => setConfigPatch((current) => ({ ...current, ...patch }))}
        />
      </Section>

      <Section
        title="结构化输出日志"
        note="规则运行时加载 / FLAG / 断言 / 归档 · logfmt ↔ JSON"
        delay={90}
        className={styles.logsSection}
      >
        <LogStream
          entries={data.modpackLogs}
          ariaLabel="整合包结构化输出日志，可按级别过滤并切换 logfmt 与 JSON"
          formats={['logfmt', 'json']}
          pausable
          height={512}
        />
      </Section>

      <Section
        title="整合包模块状态"
        note={`${data.modpackModules.length} 个模块 · 规则 / 事件 / 证据计数`}
        delay={140}
        className={styles.modulesSection}
      >
        <ModuleRail modules={data.modpackModules} />
      </Section>
    </div>
  )
}
