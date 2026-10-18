import type { CSSProperties } from 'react'

import { Glass } from '../../components/glass/Glass'
import { Section } from '../../components/Section'
import { cx } from '../../lib/cx'
import { formatInt, formatPercent } from '../../lib/format'
import styles from './BenchmarkScreen.module.css'

type ProjectKind = '模组' | '插件' | '整合包'

interface BenchmarkProject {
  id: string
  name: string
  kind: ProjectKind
  version: string
  functional: number
  detection: number
  msptP95: number
  regressionCases: number
  evidenceLinks: number
}

/** 离线示例基准：接入真实数据后由 report 模块的 report.json 汇总替换。 */
const PROJECTS: BenchmarkProject[] = [
  {
    id: 'cobblemon',
    name: 'Cobblemon',
    kind: '模组',
    version: '1.7.2',
    functional: 0.982,
    detection: 0.961,
    msptP95: 38.4,
    regressionCases: 42,
    evidenceLinks: 386,
  },
  {
    id: 'create',
    name: 'Create',
    kind: '模组',
    version: '6.0.9',
    functional: 0.974,
    detection: 0.948,
    msptP95: 41.9,
    regressionCases: 37,
    evidenceLinks: 341,
  },
  {
    id: 'essentialsx',
    name: 'EssentialsX Suite',
    kind: '插件',
    version: '2.21.0',
    functional: 0.991,
    detection: 0.972,
    msptP95: 31.2,
    regressionCases: 55,
    evidenceLinks: 512,
  },
  {
    id: 'fabulously-optimized',
    name: 'Fabulously Optimized',
    kind: '整合包',
    version: '6.2.0',
    functional: 0.996,
    detection: 0.955,
    msptP95: 27.8,
    regressionCases: 48,
    evidenceLinks: 468,
  },
]

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

/** 综合分：功能 40% + 检出 30% + 性能 20% + 回归规模 10%。 */
function scoreOf(project: BenchmarkProject): number {
  const perf = clamp01((60 - project.msptP95) / 35)
  const regression = clamp01(project.regressionCases / 60)
  return project.functional * 0.4 + project.detection * 0.3 + perf * 0.2 + regression * 0.1
}

const RANKED = [...PROJECTS].sort((left, right) => scoreOf(right) - scoreOf(left))

function metricText(key: (typeof METRICS)[number]['key'], project: BenchmarkProject): string {
  if (key === 'msptP95') return `${project.msptP95.toFixed(1)} ms`
  if (key === 'functional' || key === 'detection') return formatPercent(project[key], 1)
  return formatInt(project[key])
}

function isBest(key: (typeof METRICS)[number]['key'], project: BenchmarkProject): boolean {
  if (key === 'msptP95') return project.msptP95 === Math.min(...PROJECTS.map((item) => item.msptP95))
  if (key === 'functional' || key === 'detection') {
    return project[key] === Math.max(...PROJECTS.map((item) => item[key]))
  }
  return project[key] === Math.max(...PROJECTS.map((item) => item[key]))
}

export default function BenchmarkScreen() {
  return (
    <div className={styles.page}>
      <Section
        title="多项目基准排行"
        note="功能 / 检出 / 性能 / 回归四条线加权 · 同一套样本与门禁口径"
        delay={0}
        className={styles.spanFull}
      >
        <ol className={styles.rankList}>
          {RANKED.map((project, index) => {
            const score = scoreOf(project)
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
              {RANKED.map((project, index) => (
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
                      className={cx('tabular', isBest(metric.key, project) && styles.bestCell)}
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
