import { useState } from 'react'

import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { INSTANCE_LABELS } from '../../data/fixtures'
import { useConsoleData } from '../../data/useConsoleData'
import type { LiveViewStatus, LiveViewTarget } from '../../types'
import { PerfPanel } from './PerfPanel'
import { SignalDrawer } from './SignalDrawer'
import { StreamTile } from './StreamTile'
import { TargetList } from './TargetList'
import { targetKey } from './targetKey'
import styles from './LiveViewScreen.module.css'

export default function LiveViewScreen() {
  const { data, state } = useConsoleData()
  const [startedKeys, setStartedKeys] = useState<string[] | null>(null)
  const [selectedKey, setSelectedKey] = useState<string | null>(null)

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入画面墙…</p>
      </Glass>
    )
  }

  const { liveViewTargets, liveView, perfOverlays } = data

  const sessionFor = (target: LiveViewTarget) => {
    const same = liveView.sessions.filter(
      (session) =>
        session.target.instanceId === target.instanceId && session.target.simId === target.simId,
    )
    return same.find((session) => session.share.status === 'LIVE') ?? same[0]
  }

  // 设计稿里已有 LIVE 会话的目标当作"已在拉流"，其余靠 查看 / 全部拉流 拉起
  const initialLive = liveViewTargets
    .filter((target) => sessionFor(target)?.share.status === 'LIVE')
    .map(targetKey)
  const started = new Set(startedKeys ?? initialLive)

  const statusOf = (target: LiveViewTarget): LiveViewStatus =>
    started.has(targetKey(target)) ? 'LIVE' : (sessionFor(target)?.share.status ?? 'IDLE')

  const activeKey = selectedKey ?? initialLive[0] ?? targetKey(liveViewTargets[0])
  const activeTarget =
    liveViewTargets.find((target) => targetKey(target) === activeKey) ?? liveViewTargets[0]
  const activeLabel = INSTANCE_LABELS[activeTarget.instanceId] ?? activeTarget.instanceId
  const activeSession = sessionFor(activeTarget)
  const activePerf = perfOverlays.find((item) => item.instanceId === activeTarget.instanceId)

  const liveCount = liveViewTargets.filter((target) => statusOf(target) === 'LIVE').length

  const handleView = (target: LiveViewTarget): void => {
    const key = targetKey(target)
    setStartedKeys((previous) => {
      const base = previous ?? initialLive
      return base.includes(key) ? base : [...base, key]
    })
    setSelectedKey(key)
  }

  const handleViewAll = (): void => {
    setStartedKeys(liveViewTargets.map(targetKey))
  }

  return (
    <div className={styles.page}>
      <Section
        title="受测实例"
        note={`${liveCount}/${liveViewTargets.length} 路拉流中 · 一键查看`}
        delay={0}
        className={styles.targetsSection}
      >
        <TargetList
          targets={liveViewTargets}
          statusOf={statusOf}
          activeKey={activeKey}
          onView={handleView}
          onViewAll={handleViewAll}
        />
      </Section>

      <Section
        title="画面监控墙"
        note="WebRTC 屏幕共享 · 每路标注实例 / SimID / 网络条件"
        delay={80}
        className={styles.wallSection}
      >
        <div className={styles.wall}>
          {liveViewTargets.map((target, index) => (
            <StreamTile
              key={targetKey(target)}
              target={target}
              status={statusOf(target)}
              firstFrameMs={sessionFor(target)?.timeToFirstFrameMs ?? 0}
              perf={perfOverlays.find((item) => item.instanceId === target.instanceId)}
              index={index}
              active={targetKey(target) === activeKey}
              onSelect={() => handleView(target)}
            />
          ))}
        </div>
      </Section>

      <Section
        title="性能悬浮窗"
        note="该实例所在服务器实时读数 · 拐点触发录制"
        delay={130}
        className={styles.perfSection}
      >
        <PerfPanel instanceLabel={activeLabel} simId={activeTarget.simId} perf={activePerf} />
      </Section>

      <Section
        title="信令明细"
        note="offer / answer / ICE · WSS 通道"
        delay={180}
        className={styles.drawerSection}
      >
        <SignalDrawer session={activeSession} />
      </Section>
    </div>
  )
}
