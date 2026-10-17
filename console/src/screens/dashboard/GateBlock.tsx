import { ShieldAlert, ShieldCheck } from 'lucide-react'

import { cx } from '../../lib/cx'
import { formatPercent, formatRate } from '../../lib/format'
import type { GateResult } from '../../types'
import styles from './GateBlock.module.css'

const METRIC_LABEL: Record<string, string> = {
  false_positive_rate: '误报率（检测类）',
  falsePositiveRate: '误报率（检测类）',
  detection_rate: '检出率（检测类）',
  detectionRate: '检出率（检测类）',
  functional_smoke_pass_rate: '功能通过率',
  performance_baseline_delta: '性能基线',
  resource_assembly: '资源与装配',
  boundary: '阈值边界',
  concurrency: '并发安全',
  coverage: '覆盖率',
}

function metricLabel(metric: string): string {
  return METRIC_LABEL[metric] ?? metric
}

function isUpperBoundViolation(operator: string): boolean {
  return operator === '<='
}

function isFalsePositiveMetric(metric: string): boolean {
  return metric === 'false_positive_rate' || metric === 'falsePositiveRate'
}

function formatMetric(metric: string, value: number, digits: number): string {
  return isFalsePositiveMetric(metric) ? formatRate(value) : formatPercent(value, digits)
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
                {metricLabel(metric)}
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
                  <span className={styles.vMetric}>{metricLabel(violation.metric)}</span>
                  <span className={styles.vExpr}>
                    {isUpperBoundViolation(violation.operator) ? '上限' : '下限'}{' '}
                    {formatMetric(violation.metric, violation.threshold, 0)}
                  </span>
                </div>
                <div className={styles.compare}>
                  <span className={styles.actual}>
                    实测 {formatMetric(violation.metric, violation.actual, 2)}
                  </span>
                  <span className={styles.delta}>
                    {isUpperBoundViolation(violation.operator) ? '超出' : '低于'}阈值
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
