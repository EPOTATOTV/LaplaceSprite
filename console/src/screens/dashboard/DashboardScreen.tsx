import { Activity, Boxes, Percent, ShieldCheck, Target, Users } from 'lucide-react'

import { GroupedBarChart } from '../../components/charts/GroupedBarChart'
import { NightlyStrip } from '../../components/charts/NightlyStrip'
import { TrendChart } from '../../components/charts/TrendChart'
import type { TrendSeries } from '../../components/charts/TrendChart'
import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { useConsoleData } from '../../data/useConsoleData'
import { DETECTION_THRESHOLD, DETECTION_TIERS, FALSE_POSITIVE_THRESHOLD } from '../../data/fixtures'
import { formatInt, formatPercent, formatRate } from '../../lib/format'
import { GateBlock } from './GateBlock'
import { MetricCard } from './MetricCard'
import { RunTable } from './RunTable'
import styles from './DashboardScreen.module.css'

export default function DashboardScreen() {
  const { data, state } = useConsoleData()

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入控制台数据…</p>
      </Glass>
    )
  }

  const { cards, runs, detectionByCheckTier, detectionByTier, nightly, gate } = data

  const trendSeries: TrendSeries[] = [
    {
      key: 'detection',
      label: '检出率',
      tone: 'accent',
      axis: 'left',
      points: data.detectionHistory.points,
      threshold: data.detectionHistory.threshold,
      format: (value) => formatPercent(value, 1),
    },
    {
      key: 'falsePositive',
      label: '误报率',
      tone: 'warn',
      axis: 'right',
      points: data.falsePositiveHistory.points,
      threshold: data.falsePositiveHistory.threshold,
      format: (value) => formatRate(value),
    },
  ]

  const passRatio = cards.todayRuns === 0 ? 0 : cards.gatePassed / cards.todayRuns

  return (
    <div className={styles.page}>
      <div className={styles.ribbon}>
        <MetricCard
          hero
          label="检出率"
          value={formatPercent(cards.detectionRate, 1)}
          icon={Target}
          tone="ok"
          delay={0}
          caption={`下限 ${formatPercent(DETECTION_THRESHOLD, 0)} · 命中数 / 注入样本数`}
          spark={{
            values: data.detectionHistory.points.map((p) => p.value),
            breached: data.detectionHistory.points.map((p) => p.breached),
          }}
        />
        <MetricCard
          label="今日运行"
          value={formatInt(cards.todayRuns)}
          unit="次"
          icon={Activity}
          tone="accent"
          delay={45}
          caption="全部套件，含排队与运行中"
        />
        <MetricCard
          label="模拟人数"
          value={formatInt(cards.simulatedPlayers)}
          unit="人"
          icon={Users}
          delay={90}
          caption="合法玩家样本累计"
        />
        <MetricCard
          label="CheatSpec 数"
          value={formatInt(cards.cheatSpecCount)}
          unit="项"
          icon={Boxes}
          delay={135}
          caption="作弊类型 × 配置档位"
        />
        <MetricCard
          label="误报率"
          value={formatRate(cards.falsePositiveRate)}
          icon={Percent}
          tone="accent"
          delay={180}
          caption={`上限 ${formatRate(FALSE_POSITIVE_THRESHOLD)}`}
          spark={{
            values: data.falsePositiveHistory.points.map((p) => p.value),
            breached: data.falsePositiveHistory.points.map((p) => p.breached),
          }}
        />
        <MetricCard
          label="门禁通过"
          value={formatInt(cards.gatePassed)}
          unit={`/ ${cards.todayRuns}`}
          icon={ShieldCheck}
          tone={gate.passed ? 'ok' : 'danger'}
          delay={225}
          caption="PASSED 状态运行数"
          ratio={{ value: passRatio, label: formatPercent(passRatio, 0) }}
        />
      </div>

      <div className={styles.grid}>
        <Section
          title="检测项 × 档位检出率"
          note="横向为 0–100%，虚线为 95% 参考线"
          delay={90}
        >
          <GroupedBarChart
            data={detectionByCheckTier}
            tiers={DETECTION_TIERS}
            ariaLabel="各检测项在 MIN、DEFAULT、MAX、HIDDEN 四个档位下的检出率横向条形图"
          />
          <div className={styles.tierRow}>
            <span className={styles.tierRowLabel}>各档位合计</span>
            {DETECTION_TIERS.map((tier) => (
              <span key={tier} className={styles.tierChip} data-tier={tier}>
                <em>{tier}</em>
                <strong className="tabular">{formatPercent(detectionByTier[tier], 1)}</strong>
              </span>
            ))}
          </div>
        </Section>

        <Section title="夜间基准" note="近 7 晚 · 柱高为耗时" delay={140}>
          <NightlyStrip
            nightly={nightly}
            viewWidth={540}
            ariaLabel="近 7 晚夜间基准的通过情况与耗时"
          />
        </Section>

        <Section
          title="误报率与检出率演化"
          note="横轴为版本 · 左轴检出率 / 右轴误报率 · 红点为越界版本"
          delay={190}
          className={styles.spanFull}
        >
          <TrendChart
            series={trendSeries}
            viewWidth={980}
            height={244}
            ariaLabel="从 v0.11.0 到 v0.15.1 的检出率与误报率随版本演化曲线，越界点标红"
          />
        </Section>

        <Section
          title="最近测试运行"
          note="按开始时间倒序"
          delay={240}
          className={styles.spanFull}
        >
          <RunTable runs={runs} />
        </Section>

        <Section
          title="门禁判定"
          note="GateResult · 未通过项会阻断流水线"
          delay={290}
          className={styles.spanFull}
        >
          <GateBlock gate={gate} />
        </Section>
      </div>
    </div>
  )
}
