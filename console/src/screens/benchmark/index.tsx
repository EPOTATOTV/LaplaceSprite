import { useMemo } from 'react'
import type { CSSProperties } from 'react'
import { Boxes, FlaskConical, Trophy } from 'lucide-react'

import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { MetricCard } from '../dashboard/MetricCard'
import { useConsoleData } from '../../data/useConsoleData'
import { cx } from '../../lib/cx'
import { formatInt, formatPercent } from '../../lib/format'
import type { BenchmarkProjectRow, BenchmarkTrendPoint } from '../../types'
import styles from './BenchmarkScreen.module.css'

const METRICS = [
  { key: 'functional', label: '功能通过率', better: 'higher' },
  { key: 'detection', label: '检出率', better: 'higher' },
  { key: 'msptP95', label: 'P95 MSPT', better: 'lower' },
  { key: 'regressionCases', label: '回归用例', better: 'higher' },
  { key: 'evidenceLinks', label: '证据链', better: 'higher' },
] as const

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value))
}

type ScoreInput = Pick<
  BenchmarkProjectRow,
  'functional' | 'detection' | 'msptP95' | 'regressionCases'
>

/** 综合分：功能 40% + 检出 30% + 性能 20% + 回归规模 10%。 */
function scoreOf(project: ScoreInput): number {
  const perf = clamp01((60 - project.msptP95) / 35)
  const regression = clamp01(project.regressionCases / 60)
  return project.functional * 0.4 + project.detection * 0.3 + perf * 0.2 + regression * 0.1
}

interface Drift {
  regressed: boolean
  scoreDelta: number
  msptDelta: number
  from: string
}

/** 最近两点综合分下滑 ≥1pt 或 P95 MSPT 上涨 ≥5% 即标记基线漂移。 */
function driftOf(project: BenchmarkProjectRow): Drift | null {
  const history = project.history
  if (!history || history.length < 2) return null
  const previous = history[history.length - 2]
  const latest = history[history.length - 1]
  const scoreDelta = scoreOf(latest) - scoreOf(previous)
  const msptDelta = previous.msptP95 === 0 ? 0 : (latest.msptP95 - previous.msptP95) / previous.msptP95
  return {
    regressed: scoreDelta <= -0.01 || msptDelta >= 0.05,
    scoreDelta,
    msptDelta,
    from: previous.label,
  }
}

/** 综合分历史折线；历史点不足 3 个时不画。 */
function Sparkline({ history }: { history: BenchmarkTrendPoint[] }) {
  const values = history.map(scoreOf)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const width = 88
  const height = 24
  const pad = 3
  const points = values.map((value, index) => {
    const x = pad + (index * (width - pad * 2)) / (values.length - 1)
    const y = height - pad - ((value - min) / range) * (height - pad * 2)
    return [Number(x.toFixed(2)), Number(y.toFixed(2))] as const
  })
  const last = points[points.length - 1]

  return (
    <svg
      className={styles.spark}
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label={`综合分走势 ${history.map((point) => point.label).join(' → ')}`}
    >
      <polyline
        className={styles.sparkLine}
        points={points.map((point) => point.join(',')).join(' ')}
      />
      <circle className={styles.sparkDot} cx={last[0]} cy={last[1]} r="2.4" />
    </svg>
  )
}

function averageMspt(projects: BenchmarkProjectRow[]): number {
  if (projects.length === 0) return 0
  return projects.reduce((sum, project) => sum + project.msptP95, 0) / projects.length
}

function metricText(key: (typeof METRICS)[number]['key'], project: BenchmarkProjectRow): string {
  if (key === 'msptP95') return `${project.msptP95.toFixed(1)} ms`
  if (key === 'functional' || key === 'detection') return formatPercent(project[key], 1)
  return formatInt(project[key])
}

function isBest(
  key: (typeof METRICS)[number]['key'],
  project: BenchmarkProjectRow,
  projects: BenchmarkProjectRow[],
): boolean {
  if (key === 'msptP95') {
    return project.msptP95 === Math.min(...projects.map((item) => item.msptP95))
  }
  if (key === 'functional' || key === 'detection') {
    return project[key] === Math.max(...projects.map((item) => item[key]))
  }
  return project[key] === Math.max(...projects.map((item) => item[key]))
}

export default function BenchmarkScreen() {
  const { data, state } = useConsoleData()
  const projects = useMemo(() => data?.benchmarkProjects ?? [], [data])
  const ranked = useMemo(
    () => [...projects].sort((left, right) => scoreOf(right) - scoreOf(left)),
    [projects],
  )
  const best = ranked[0]
  const average = averageMspt(projects)

  if (!data || state === 'loading') {
    return (
      <Glass level="panel" className={styles.loading}>
        <span className={styles.loadingBar} aria-hidden="true" />
        <p>正在载入多项目基准…</p>
      </Glass>
    )
  }

  if (!best) {
    return (
      <Glass level="panel" className={styles.loading}>
        <p>暂无多项目基准数据</p>
      </Glass>
    )
  }

  return (
    <div className={styles.page}>
      <div className={styles.summary}>
        <MetricCard
          hero
          label="最高综合分"
          value={formatPercent(scoreOf(best), 1)}
          caption={`${best.name} · ${best.kind} ${best.version}`}
          icon={Trophy}
          tone="accent"
          delay={0}
          ratio={{ value: scoreOf(best), label: '功能 40 · 检出 30 · 性能 20 · 回归 10' }}
        />
        <MetricCard
          label="参与项目"
          value={String(projects.length)}
          unit="个"
          caption="模组 / 插件 / 整合包同一口径"
          icon={Boxes}
          delay={70}
        />
        <MetricCard
          label="平均 P95 MSPT"
          value={average.toFixed(1)}
          unit="ms"
          caption="越低越好 · 门禁上限 50ms"
          icon={FlaskConical}
          tone={average > 50 ? 'danger' : 'warn'}
          delay={140}
        />
      </div>

      <Section
        title="多项目基准排行"
        note="功能 / 检出 / 性能 / 回归四条线加权 · 折线为历史综合分，最近两点下滑自动标注漂移"
        delay={0}
        className={styles.spanFull}
      >
        <ol className={styles.rankList}>
          {ranked.map((project, index) => {
            const score = scoreOf(project)
            const drift = driftOf(project)
            return (
              <Glass
                key={project.id}
                as="li"
                level="inset"
                interactive
                className={cx(styles.rankRow, index === 0 && styles.lead)}
                style={{ animationDelay: `${index * 70}ms` } as CSSProperties}
              >
                <span className={styles.rankNo}>{String(index + 1).padStart(2, '0')}</span>

                <div className={styles.identity}>
                  <span className={styles.projectName}>{project.name}</span>
                  <span className={styles.projectMeta}>
                    <em>{project.kind}</em>
                    {project.version}
                  </span>
                </div>

                <div className={styles.score}>
                  <span className={cx(styles.scoreValue, 'tabular')}>{formatPercent(score, 1)}</span>
                  <span className={styles.scoreTrack} aria-hidden="true">
                    <span
                      className={styles.scoreFill}
                      style={{ transform: `scaleX(${score})` }}
                    />
                  </span>
                </div>

                <div className={styles.bars}>
                  <span className={styles.barItem}>
                    <span className={styles.barLabel}>功能</span>
                    <span className={styles.barTrack}>
                      <span
                        className={styles.barFill}
                        style={{ transform: `scaleX(${project.functional})` }}
                      />
                    </span>
                  </span>
                  <span className={styles.barItem}>
                    <span className={styles.barLabel}>检出</span>
                    <span className={styles.barTrack}>
                      <span
                        className={cx(styles.barFill, styles.barAccent)}
                        style={{ transform: `scaleX(${project.detection})` }}
                      />
                    </span>
                  </span>
                  <span className={styles.barItem}>
                    <span className={styles.barLabel}>性能</span>
                    <span className={styles.barTrack}>
                      <span
                        className={cx(styles.barFill, styles.barWarn)}
                        style={{ transform: `scaleX(${clamp01((60 - project.msptP95) / 35)})` }}
                      />
                    </span>
                  </span>
                </div>

                <div className={styles.trend}>
                  {project.history && project.history.length >= 3 ? (
                    <>
                      <Sparkline history={project.history} />
                      {drift?.regressed ? (
                        <span
                          className={styles.drift}
                          title={`${drift.from} 起综合分 ${formatPercent(drift.scoreDelta, 1)}，P95 MSPT ${drift.msptDelta >= 0 ? '+' : ''}${(drift.msptDelta * 100).toFixed(1)}%`}
                        >
                          漂移 {formatPercent(drift.scoreDelta, 1)}
                        </span>
                      ) : (
                        <span className={styles.steady}>基线稳定</span>
                      )}
                    </>
                  ) : (
                    <span className={styles.trendEmpty}>历史不足</span>
                  )}
                </div>

                <span className={styles.trail}>
                  {project.regressionCases} 条回归 · {project.evidenceLinks} 环证据
                </span>
              </Glass>
            )
          })}
        </ol>
      </Section>

      <Section
        title="指标横评"
        note="按项目类型分组 · 粗体为该项最优"
        delay={140}
        className={styles.spanFull}
      >
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th scope="col">项目</th>
                {METRICS.map((metric) => (
                  <th key={metric.key} scope="col">
                    {metric.label}
                    <span className={styles.better}>
                      {metric.better === 'higher' ? '越高越好' : '越低越好'}
                    </span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ranked.map((project, index) => (
                <tr
                  key={project.id}
                  className={cx(index === 0 && styles.bestRow)}
                  style={{ animationDelay: `${180 + index * 55}ms` } as CSSProperties}
                >
                  <th scope="row">
                    {project.name}
                    <span className={styles.rowKind}>{project.kind}</span>
                  </th>
                  {METRICS.map((metric) => (
                    <td
                      key={metric.key}
                      className={cx('tabular', isBest(metric.key, project, projects) && styles.bestCell)}
                    >
                      {metricText(metric.key, project)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>
    </div>
  )
}
