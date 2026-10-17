import { Activity, Boxes, Gauge, ListChecks, ShieldCheck, Users } from 'lucide-react'

import { GroupedBarChart } from '../../components/charts/GroupedBarChart'
import { NightlyStrip } from '../../components/charts/NightlyStrip'
import { TrendChart } from '../../components/charts/TrendChart'
import type { TrendSeries } from '../../components/charts/TrendChart'
import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { useConsoleData } from '../../data/useConsoleData'
import { DETECTION_THRESHOLD, DETECTION_TIERS, FALSE_POSITIVE_THRESHOLD } from '../../data/fixtures'
import { cx } from '../../lib/cx'
import { formatInt, formatPercent, formatRate } from '../../lib/format'
import { performanceVerdict } from '../../lib/performance'
import { GateBlock } from './GateBlock'
import { MetricCard } from './MetricCard'
import { RunTable } from './RunTable'
import { TargetTypePanel } from './TargetTypePanel'
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

  const { cards, runs, sampleTypeBreakdown, targetTypes, nightly, gate } = data

  const trendSeries: TrendSeries[] = [
    {
      key: 'detection',
      label: '检出率（检测类）',
      tone: 'accent',
      axis: 'left',
      points: data.detectionHistory.points,
      threshold: data.detectionHistory.threshold,
      format: (value) => formatPercent(value, 1),
    },
    {
      key: 'falsePositive',
      label: '误报率（检测类）',
      tone: 'warn',
      axis: 'right',
      points: data.falsePositiveHistory.points,
      threshold: data.falsePositiveHistory.threshold,
      format: (value) => formatRate(value),
    },
  ]

  const passRatio = cards.gateTotal === 0 ? 0 : cards.gatePassed / cards.gateTotal
  const perf = performanceVerdict(cards.performanceBaselineOk, cards.performanceBaselineDelta)
  const functionalTone = cards.functionalPassRate >= 1 ? 'ok' : 'warn'

  const tierTotals = DETECTION_TIERS.map((tier) => {
    let hits = 0
    let samples = 0
    for (const row of sampleTypeBreakdown) {
      const cell = row.tiers.find((stat) => stat.tier === tier)
      if (!cell) continue
      hits += cell.hits
      samples += cell.samples
    }
    return { tier, rate: samples === 0 ? 0 : hits / samples }
  })

  return (
    <div className={styles.page}>
      <div className={styles.ribbon}>
        <MetricCard
          hero
          label="功能通过率"
          value={formatPercent(cards.functionalPassRate, 1)}
          icon={ListChecks}
          tone={functionalTone}
          delay={0}
          caption="功能冒烟通过数 / 总数 · 门禁下限 100%"
          ratio={{
            value: cards.functionalPassRate,
            label: `${formatPercent(cards.functionalPassRate, 1)} 通过`,
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
          caption="行为基线样本累计"
        />
        <MetricCard
          label="样本矩阵数"
          value={formatInt(cards.sampleSpecCount)}
          unit="项"
          icon={Boxes}
          delay={135}
          caption="12 类目标 × 4 档位 × 3 变体"
        />
        <MetricCard
          label="性能基线"
          value={perf.label}
          unit={perf.deltaLabel}
          icon={Gauge}
          tone={perf.tone}
          delay={180}
          caption="相对上一版本 TPS · 门禁硬阈值 0%"
        />
        <MetricCard
          label="门禁通过"
          value={formatInt(cards.gatePassed)}
          unit={`/ ${formatInt(cards.gateTotal)}`}
          icon={ShieldCheck}
          tone={gate.passed ? 'ok' : 'danger'}
          delay={225}
          caption="PASSED 状态运行数"
          ratio={{ value: passRatio, label: formatPercent(passRatio, 0) }}
        />
      </div>

      <div className={styles.grid}>
        <Section
          title="最近测试运行"
          note="按开始时间倒序 · 功能类优先"
          delay={90}
          className={styles.spanFull}
        >
          <RunTable runs={runs} />
        </Section>

        <Section
          title="样本类型 × 档位断言结果"
          note="横向为通过率 · 虚线为 95% 参考线 · 按目标类型分组"
          delay={140}
        >
          <GroupedBarChart
            data={sampleTypeBreakdown}
            tiers={DETECTION_TIERS}
            viewWidth={640}
            ariaLabel="按目标类型分组的样本类型在 MIN、DEFAULT、MAX、HIDDEN 四个档位下的断言通过率横向条形图"
          />
          <div className={styles.tierRow}>
            <span className={styles.tierRowLabel}>各档位合计</span>
            {tierTotals.map(({ tier, rate }) => (
              <span key={tier} className={styles.tierChip} data-tier={tier}>
                <em>{tier}</em>
                <strong className="tabular">{formatPercent(rate, 1)}</strong>
              </span>
            ))}
          </div>
        </Section>

        <Section title="夜间基准" note="近 7 晚 · 柱高为耗时" delay={190}>
          <NightlyStrip
            nightly={nightly}
            viewWidth={480}
            ariaLabel="近 7 晚夜间基准的通过情况与耗时"
          />
        </Section>

        <Section
          title="检测类专项 · 指标趋势"
          note="仅检测类目标 · 左轴检出率 / 右轴误报率 · 红点为越界版本"
          delay={240}
          className={styles.spanFull}
        >
          <div className={styles.detectionStats}>
            <span className={styles.detectionLabel}>检测类专项</span>
            <span className={styles.detectionChip}>
              <em>误报率</em>
              <strong
                className={cx(
                  'tabular',
                  cards.falsePositiveRate > FALSE_POSITIVE_THRESHOLD && styles.chipAlert,
                )}
              >
                {formatRate(cards.falsePositiveRate)}
              </strong>
            </span>
            <span className={styles.detectionChip}>
              <em>检出率</em>
              <strong
                className={cx(
                  'tabular',
                  cards.detectionRate < DETECTION_THRESHOLD && styles.chipAlert,
                )}
              >
                {formatPercent(cards.detectionRate, 1)}
              </strong>
            </span>
            <span className={styles.detectionHint}>仅统计检测类目标，功能类不复用这套口径</span>
          </div>
          <TrendChart
            series={trendSeries}
            viewWidth={980}
            height={244}
            ariaLabel="从 v0.11.0 到 v0.15.1 的检测类检出率与误报率随版本演化曲线，越界点标红"
          />
        </Section>

        <Section
          title="全类型目标矩阵"
          note="12 类目标 · 测试重点 / 框架工具 / 代表项目 / 典型风险"
          delay={290}
          className={styles.spanFull}
        >
          <TargetTypePanel rows={targetTypes} />
        </Section>

        <Section
          title="门禁判定"
          note="GateResult · 未通过项会阻断流水线"
          delay={340}
          className={styles.spanFull}
        >
          <GateBlock gate={gate} />
        </Section>
      </div>
    </div>
  )
}
