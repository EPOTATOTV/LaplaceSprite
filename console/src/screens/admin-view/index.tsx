import { useState } from 'react'
import { Siren } from 'lucide-react'

import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { useConsoleData } from '../../data/useConsoleData'
import { cx } from '../../lib/cx'
import type { InspectSession, InspectStatus } from '../../types'
import { CommandPanel } from './CommandPanel'
import type { CommandReceipt } from './CommandPanel'
import { InspectPanel } from './InspectPanel'
import { InspectStatusChip } from './InspectStatusChip'
import { PlayerRoster } from './PlayerRoster'
import { WorldCanvas } from './WorldCanvas'
import styles from './AdminViewScreen.module.css'

export default function AdminViewScreen() {
  const { data, state } = useConsoleData()
  const [selectedSimId, setSelectedSimId] = useState<string | null>(null)
  const [resolved, setResolved] = useState<Record<string, InspectStatus>>({})
  const [receipts, setReceipts] = useState<CommandReceipt[]>([])

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入管理员视图…</p>
      </Glass>
    )
  }

  const { simPlayers, inspectSessions, redScreen, orchestratorCommands } = data
  const activeSimId = selectedSimId ?? redScreen.playerId

  const statusOf = (session: InspectSession): InspectStatus =>
    resolved[session.simId] ?? session.status
  const redSession = inspectSessions.find((session) => session.simId === redScreen.playerId)
  const redStatus: InspectStatus = resolved[redScreen.playerId] ?? redSession?.status ?? 'PENDING_INSPECT'

  const pushReceipt = (command: string, text: string): void => {
    setReceipts((list) => [
      ...list,
      { id: `${command}-${list.length}`, command, text, at: new Date().toISOString() },
    ])
  }

  const handleCommand = (raw: string): void => {
    const [name, ...args] = raw.split(/\s+/)
    const definition = orchestratorCommands.find((item) => item.name === name)
    if (!definition) {
      pushReceipt(
        raw,
        `未知子命令 ${name}，可用：${orchestratorCommands.map((item) => item.name).join(' / ')}`,
      )
      return
    }
    pushReceipt(raw, args.length > 0 ? `${definition.receipt}（参数 ${args.join(' ')}）` : definition.receipt)
  }

  const handleConfirm = (): void => {
    setResolved((map) => ({ ...map, [redScreen.playerId]: 'CONFIRMED' }))
    setSelectedSimId(redScreen.playerId)
    pushReceipt(
      `inspect ${redScreen.playerId}`,
      `红屏确认：${redScreen.playerName} 判定为作弊，证据已归档进 EvidenceVault`,
    )
  }

  const confirmed = redStatus === 'CONFIRMED'

  return (
    <div className={styles.page}>
      <Glass
        level="panel"
        tone={confirmed ? 'ok' : 'danger'}
        className={cx(styles.banner, confirmed && styles.bannerResolved)}
      >
        <span className={styles.bannerIcon} aria-hidden="true">
          <Siren size={18} strokeWidth={1.9} />
        </span>
        <div className={styles.bannerBody}>
          <strong className={styles.bannerTitle}>红屏公示 · {redScreen.playerName}</strong>
          <span className={styles.bannerMeta}>
            疑似 {redScreen.type} · 风险分{' '}
            <b className="tabular">{redScreen.riskScore.toFixed(1)}</b> · {redScreen.playerId}
          </span>
        </div>
        <InspectStatusChip status={redStatus} />
        <button
          type="button"
          className={styles.confirm}
          onClick={handleConfirm}
          disabled={confirmed}
        >
          {confirmed ? '已确认查端' : redScreen.actionLabel}
        </button>
      </Glass>

      <Section
        title="世界视图"
        note={`${simPlayers.length} 名在场 · 圈选颜色对应用户类型`}
        delay={60}
        className={styles.worldSection}
      >
        <WorldCanvas players={simPlayers} selectedSimId={activeSimId} onSelect={setSelectedSimId} />
      </Section>

      <Section
        title="Tab 名单"
        note="合法绿 / 作弊红 / 管理员紫 · 点击行与世界标记联动"
        delay={110}
        className={styles.rosterSection}
      >
        <PlayerRoster
          players={simPlayers}
          selectedSimId={activeSimId}
          onSelect={setSelectedSimId}
        />
      </Section>

      <Section
        title="查端会话"
        note="沿用 平台红屏管理交互"
        delay={160}
        className={styles.sessionSection}
      >
        <InspectPanel
          sessions={inspectSessions}
          players={simPlayers}
          selectedSimId={activeSimId}
          statusOf={statusOf}
          onSelect={setSelectedSimId}
        />
      </Section>

      <Section
        title="/ls 命令面板"
        note="inspect / teleport / scenario / result · 回执写入会话"
        delay={210}
        className={styles.commandSection}
      >
        <CommandPanel
          commands={orchestratorCommands}
          receipts={receipts}
          onSubmit={handleCommand}
        />
      </Section>
    </div>
  )
}
