import { useMemo } from 'react'

import { HeatMatrix } from '../../components/charts/HeatMatrix'
import { NightlyStrip } from '../../components/charts/NightlyStrip'
import { TrendChart } from '../../components/charts/TrendChart'
import type { TrendSeries } from '../../components/charts/TrendChart'
import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { useConsoleData } from '../../data/useConsoleData'
import { DETECTION_TIERS, FALSE_POSITIVE_THRESHOLD } from '../../data/fixtures'
import { formatDuration, formatPercent, formatRate } from '../../lib/format'
import { PipelineStrip } from './PipelineStrip'
import type { CiStage } from './PipelineStrip'
import styles from './CiScreen.module.css'

export default function CiScreen() {
  const { data, state } = useConsoleData()

  const stages = useMemo<CiStage[]>(() => {
    if (!data) return []
    const lastNight = data.nightly[data.nightly.length - 1]
    const boundaryRuns = data.runs.filter((run) => run.suite === 'boundary')
    const boundaryFailed = boundaryRuns.some((run) => run.status === 'FAILED')
    const boundaryDuration = boundaryRuns.reduce((sum, run) => sum + run.durationMs, 0)

    // 单测与静态扫描的遥测暂不在 ConsoleData 里，这里只保留真实的短路关系：
    // 阈值边界一旦失败，后续扫描阶段不再执行。
    return [
      {
        id: 'unit',
        name: '全量单测',
        detail: 'core / simulator / network',
        status: 'pass',
        durationMs: 214_600,
      },
      {
        id: 'bench',
        name: '关键模拟基准',
        detail: `${lastNight?.baselineVersion ?? '—'} 同口径抽样`,
        status: lastNight?.passed ? 'pass' : 'fail',
        durationMs: lastNight?.durationMs ?? 0,
      },
      {
        id: 'boundary',
        name: '阈值边界',
        detail: `${boundaryRuns.length} 个边界运行`,
        status: boundaryFailed ? 'fail' : 'pass',
        durationMs: boundaryDuration,
      },
      {
        id: 'security',
        name: 'CodeQL + gitleaks',
        detail: '静态扫描 · 密钥扫描',
        status: boundaryFailed ? 'skipped' : 'pass',
        durationMs: 96_400,
      },
      {
        id: 'gate',
        name: 'GateResult',
        detail: '阈值门禁判定',
        status: data.gate.passed ? 'pass' : 'fail',
        durationMs: 12_400,
      },
    ]
  }, [data])

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入 CI 指标…</p>
      </Glass>
    )
  }

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

  const breaches = [
    ...data.detectionHistory.points
      .filter((point) => point.breached)
      .map((point) => ({
        key: `d-${point.version}`,
        version: point.version,
        text: `检测类检出率 ${formatPercent(point.value, 1)} 低于下限 ${formatPercent(data.detectionHistory.threshold, 0)}`,
      })),
    ...data.falsePositiveHistory.points
      .filter((point) => point.breached)
      .map((point) => ({
        key: `f-${point.version}`,
        version: point.version,
        text: `检测类误报率 ${formatRate(point.value)} 超过上限 ${formatRate(data.falsePositiveHistory.threshold)}`,
      })),
  ]

  const totalBoundaryDuration = data.runs
    .filter((run) => run.suite === 'boundary')
    .reduce((sum, run) => sum + run.durationMs, 0)

  return (
    <div className={styles.page}>
      <Section
        title="PR 门禁流水线"
        note="main 分支最近一次运行 · 阶段失败会短路后续阶段"
        delay={0}
        className={styles.spanFull}
        actions={
          <div className={styles.thresholds}>
            <span className={styles.threshold}>
              检出率（检测类） <strong className="tabular">≥ {formatPercent(0.95, 0)}</strong>
            </span>
            <span className={styles.threshold}>
              误报率（检测类）{' '}
              <strong className="tabular">≤ {formatRate(FALSE_POSITIVE_THRESHOLD)}</strong>
            </span>
          </div>
        }
      >
        <PipelineStrip stages={stages} ariaLabel="PR 门禁流水线的五个阶段及其状态" />
      </Section>

      <Section
        title="检测类专项 · 指标演化"
        note="仅检测类目标 · 左轴检出率 / 右轴误报率 · 红点为越界版本"
        delay={90}
        className={styles.spanFull}
      >
        <TrendChart
          series={trendSeries}
          viewWidth={1180}
          height={252}
          ariaLabel="从 v0.11.0 到 v0.15.1 的检测类检出率与误报率演化曲线，越界版本以红点标注"
        />
        <div className={styles.annotations}>
          <span className={styles.annotationsLabel}>越界记录</span>
          {breaches.length === 0 && <span className={styles.annotationEmpty}>暂无</span>}
          {breaches.map((breach, index) => (
            <span
              key={breach.key}
              className={styles.annotation}
              style={{ animationDelay: `${index * 55}ms` }}
            >
              <em className="tabular">{breach.version}</em>
              {breach.text}
            </span>
          ))}
        </div>
      </Section>

      <Section
        title="检测类检出率矩阵"
        note="检测项 × 档位 · 颜色越深检出率越高"
        delay={140}
      >
        <HeatMatrix
          data={data.detectionByCheckTier}
          tiers={DETECTION_TIERS}
          ariaLabel="检测类检测项与档位交叉的检出率矩阵"
        />
      </Section>

      <Section
        title="夜间基准 · 近 7 晚"
        note={`边界阶段累计耗时 ${formatDuration(totalBoundaryDuration)}`}
        delay={190}
      >
        <NightlyStrip
          nightly={data.nightly}
          viewWidth={520}
          ariaLabel="近 7 晚夜间基准的通过情况与耗时"
        />
      </Section>
    </div>
  )
}
