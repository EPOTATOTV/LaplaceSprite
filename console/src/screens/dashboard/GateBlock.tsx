import { ShieldAlert, ShieldCheck } from 'lucide-react'

import { cx } from '../../lib/cx'
import { formatPercent, formatRate } from '../../lib/format'
import type { GateResult } from '../../types'
import styles from './GateBlock.module.css'

function isUpperBoundViolation(operator: string): boolean {
  return operator === '<='
}

export function GateBlock({ gate }: { gate: GateResult }) {
  const Icon = gate.passed ? ShieldCheck : ShieldAlert

  return (
    <div className={styles.wrap}>
      <div className={cx(styles.verdict, gate.passed ? styles.pass : styles.fail)}>
        <span className={styles.seal}>
          <Icon size={22} strokeWidth={1.7} aria-hidden="true" />
        </span>
        <div className={styles.verdictText}>
          <strong className={styles.verdictTitle}>{gate.passed ? '门禁通过' : '门禁未通过'}</strong>
          <span className={styles.verdictNote}>
            {gate.passed
              ? '全部指标落在阈值区间内，可放行'
              : `${gate.failedMetrics.length} 项指标越界，流水线应停在 Gate 阶段`}
          </span>
        </div>
      </div>

      {!gate.passed && (
        <>
          <div className={styles.metricChips}>
            {gate.failedMetrics.map((metric, index) => (
              <span
                key={metric}
                className={styles.chip}
                style={{ animationDelay: `${index * 60}ms` }}
              >
                {metric}
              </span>
            ))}
          </div>

          <ul className={styles.violations}>
            {gate.violations.map((violation, index) => (
              <li
                key={`${violation.metric}-${violation.message}`}
                className={styles.violation}
                style={{ animationDelay: `${80 + index * 55}ms` }}
              >
                <div className={styles.vHead}>
                  <span className={styles.vMetric}>{violation.metric}</span>
                  <span className={styles.vExpr}>
                    {isUpperBoundViolation(violation.operator) ? '上限' : '下限'}{' '}
                    {violation.metric === 'falsePositiveRate'
                      ? formatRate(violation.threshold)
                      : formatPercent(violation.threshold, 0)}
                  </span>
                </div>
                <div className={styles.compare}>
                  <span className={styles.actual}>
                    实测{' '}
                    {violation.metric === 'falsePositiveRate'
                      ? formatRate(violation.actual)
                      : formatPercent(violation.actual, 2)}
                  </span>
                  <span className={styles.delta}>
                    {violation.metric === 'falsePositiveRate' ? '超出' : '低于'}阈值
                  </span>
                </div>
                <p className={styles.vMessage}>{violation.message}</p>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
