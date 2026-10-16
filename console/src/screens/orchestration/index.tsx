import { useState } from 'react'

import { Glass } from '../../components/glass/Glass'
import { LogStream } from '../../components/LogStream'
import { Section } from '../../components/Section'
import { INSTANCE_LABELS } from '../../data/fixtures'
import { useConsoleData } from '../../data/useConsoleData'
import { InstanceCard } from './InstanceCard'
import { NetworkPanel } from './NetworkPanel'
import { ScenarioProgress } from './ScenarioProgress'
import styles from './OrchestrationScreen.module.css'

function isHealthy(tps: number, mspt: number, used: number, max: number): boolean {
  const memoryPct = max === 0 ? 0 : (used * 100) / max
  return tps >= 19.5 && mspt < 50 && memoryPct <= 70
}

export default function OrchestrationScreen() {
  const { data, state } = useConsoleData()
  const [focusId, setFocusId] = useState<string | null>(null)

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入编排控制台…</p>
      </Glass>
    )
  }

  const { serverHealth, scenarioRuns, networkModels, logs } = data

  // 焦点实例默认落在 TPS 最低的那台：运维先看最不稳的一台
  const worst = serverHealth.reduce(
    (acc, item) => (item.tps < acc.tps ? item : acc),
    serverHealth[0],
  )
  const activeId = focusId ?? worst.instanceId
  const hero = serverHealth.find((item) => item.instanceId === activeId) ?? serverHealth[0]
  const rest = serverHealth.filter((item) => item.instanceId !== hero.instanceId)

  const healthyCount = serverHealth.filter((item) =>
    isHealthy(item.tps, item.mspt, item.memoryUsedMb, item.memoryMaxMb),
  ).length
  const runningScenarios = scenarioRuns.filter((run) => run.status === 'RUNNING').length
  const queuedScenarios = scenarioRuns.filter((run) => run.status === 'QUEUED').length

  return (
    <div className={styles.page}>
      <Section
        title="实例状态"
        note={`${healthyCount}/${serverHealth.length} 台健康 · 阈值 TPS ≥19.5 / MSPT <50ms / 堆 ≤70%`}
        delay={0}
        className={styles.bandSection}
      >
        <div className={styles.band}>
          <InstanceCard
            health={hero}
            label={INSTANCE_LABELS[hero.instanceId] ?? hero.instanceId}
            hero
            delay={0}
          />
          <ul className={styles.compactList}>
            {rest.map((item, index) => (
              <InstanceCard
                key={item.instanceId}
                health={item}
                label={INSTANCE_LABELS[item.instanceId] ?? item.instanceId}
                delay={70 + index * 55}
                focused={item.instanceId === activeId}
                onFocus={() => setFocusId(item.instanceId)}
              />
            ))}
          </ul>
        </div>
      </Section>

      <Section
        title="场景剧本进度"
        note={`${runningScenarios} 个运行中 · ${queuedScenarios} 个排队`}
        delay={90}
        className={styles.scenarioSection}
      >
        <ScenarioProgress runs={scenarioRuns} />
      </Section>

      <Section
        title="网络模型"
        note="延迟段 / 抖动 / 批处理 · 丢包与重排可切换"
        delay={140}
        className={styles.networkSection}
      >
        <NetworkPanel models={networkModels} />
      </Section>

      <Section
        title="TestServer 日志流"
        note="tail -f · 事件流 seq / FLAG / 断言 / 归档"
        delay={190}
        className={styles.logSection}
      >
        <LogStream
          entries={logs}
          ariaLabel="TestServer 结构化日志流，按时间正序，默认自动滚动到底部"
          showSeq
          pausable
          height={520}
        />
      </Section>
    </div>
  )
}
