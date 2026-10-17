import { useMemo, useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'

import { StatusPill } from '../../components/StatusPill'
import { cx } from '../../lib/cx'
import { formatClock, formatInt, formatPercent } from '../../lib/format'
import { performanceVerdict } from '../../lib/performance'
import type { TestRunRow } from '../../types'
import styles from './RunTable.module.css'

const COLLAPSED_COUNT = 16

const SUITE_LABEL: Record<string, string> = {
  legit: '行为基线',
  cheat: '检测注入',
  boundary: '边界',
  concurrency: '并发',
}

export function RunTable({ runs }: { runs: TestRunRow[] }) {
  const [expanded, setExpanded] = useState(false)

  const visible = useMemo(
    () => (expanded ? runs : runs.slice(0, COLLAPSED_COUNT)),
    [runs, expanded],
  )

  return (
    <div>
      <div className={styles.toolbar}>
        <span className={styles.count}>
          今日 <strong className="tabular">{runs.length}</strong> 条运行，显示{' '}
          <strong className="tabular">{visible.length}</strong> 条
        </span>
        <button
          type="button"
          className={styles.toggle}
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
        >
          {expanded ? '收起' : '展开全部'}
          {expanded ? (
            <ChevronUp size={14} strokeWidth={2} aria-hidden="true" />
          ) : (
            <ChevronDown size={14} strokeWidth={2} aria-hidden="true" />
          )}
        </button>
      </div>

      <div className={styles.scroll}>
        <table className={styles.table}>
          <caption className="visually-hidden">
            最近测试运行列表，含目标、套件、行为基线样本、功能通过率、性能基线与边界样本
          </caption>
          <thead>
            <tr>
              <th scope="col">运行 ID</th>
              <th scope="col">目标</th>
              <th scope="col">套件</th>
              <th scope="col" className={styles.num}>
                行为基线样本
              </th>
              <th scope="col" className={styles.num}>
                功能通过率
              </th>
              <th scope="col" className={styles.num}>
                性能基线
              </th>
              <th scope="col" className={styles.num}>
                边界样本
              </th>
              <th scope="col">状态</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((run, index) => {
              const perf = performanceVerdict(
                run.performanceBaselineOk,
                run.performanceBaselineDelta,
              )
              const functionalRate =
                run.functionalSmokeTotal > 0
                  ? run.functionalSmokePassed / run.functionalSmokeTotal
                  : null
              // 运行中 / 排队中的样本还没跑完，低于 100% 不代表失败，只有终态才标红。
              const functionalBreach =
                functionalRate !== null &&
                functionalRate < 1 &&
                (run.status === 'FAILED' || run.status === 'ABORTED')
              return (
                <tr
                  key={run.id}
                  className={styles.row}
                  style={{ animationDelay: `${Math.min(index, 18) * 20}ms` }}
                >
                  <td className={cx(styles.mono, styles.id)}>{run.id}</td>
                  <td>
                    <span className={styles.target}>{run.target}</span>
                    <span className={styles.sub}>
                      {run.platform.id} · {formatClock(run.startedAt)}
                    </span>
                  </td>
                  <td>
                    <span className={styles.suite}>
                      {SUITE_LABEL[run.suite] ?? run.suite}
                      <em>{run.suite}</em>
                    </span>
                  </td>
                  <td className={cx(styles.num, styles.mono)}>{formatInt(run.legitSamples)}</td>
                  <td
                    className={cx(styles.num, styles.mono, functionalBreach && styles.breach)}
                    title={
                      functionalRate === null
                        ? '本轮未执行功能冒烟'
                        : `${run.functionalSmokePassed} / ${run.functionalSmokeTotal} 通过`
                    }
                  >
                    {functionalRate === null ? '—' : formatPercent(functionalRate, 1)}
                  </td>
                  <td className={cx(styles.num, styles.perfCell)}>
                    <span
                      className={cx(styles.perf, styles[perf.tone])}
                      title={`${perf.label} ${perf.deltaLabel}`}
                    >
                      {perf.label}
                      <em>{perf.deltaLabel}</em>
                    </span>
                  </td>
                  <td className={cx(styles.num, styles.mono)}>{formatInt(run.boundarySamples)}</td>
                  <td>
                    <StatusPill status={run.status} dense />
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
