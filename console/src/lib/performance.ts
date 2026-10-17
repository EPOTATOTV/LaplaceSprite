import type { Tone } from './tone'

/**
 * 展示分级容差：±0.5% 以内按测量噪声算「持平」。
 * 门禁本身仍以 0 为硬阈值，这里只决定卡片和表格的措辞与颜色。
 */
export const PERFORMANCE_DELTA_TOLERANCE = 0.005

export interface PerformanceVerdict {
  label: '优于' | '持平' | '劣化' | '未采集'
  tone: Tone
  deltaLabel: string
}

export function performanceVerdict(ok: boolean, delta: number): PerformanceVerdict {
  if (!ok) return { label: '未采集', tone: 'warn', deltaLabel: '—' }
  const sign = delta > 0 ? '+' : delta < 0 ? '−' : ''
  const deltaLabel = `${sign}${Math.abs(delta * 100).toFixed(1)}%`
  if (delta > PERFORMANCE_DELTA_TOLERANCE) return { label: '优于', tone: 'ok', deltaLabel }
  if (delta < -PERFORMANCE_DELTA_TOLERANCE) return { label: '劣化', tone: 'danger', deltaLabel }
  return { label: '持平', tone: 'warn', deltaLabel }
}
