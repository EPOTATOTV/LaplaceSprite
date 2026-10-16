import { Clock, ShieldAlert, ShieldCheck } from 'lucide-react'

import { cx } from '../../lib/cx'
import type { InspectStatus } from '../../types'
import styles from './InspectStatusChip.module.css'

const LABEL: Record<InspectStatus, string> = {
  PENDING_INSPECT: '待查端',
  CONFIRMED: '已确认作弊',
  FALSE_POSITIVE: '误报',
}

const ICON = {
  PENDING_INSPECT: Clock,
  CONFIRMED: ShieldAlert,
  FALSE_POSITIVE: ShieldCheck,
}

export function InspectStatusChip({ status }: { status: InspectStatus }) {
  const Icon = ICON[status]
  return (
    <span className={cx(styles.chip, styles[status])} data-status={status}>
      <Icon size={12} strokeWidth={2} aria-hidden="true" />
      {LABEL[status]}
    </span>
  )
}
