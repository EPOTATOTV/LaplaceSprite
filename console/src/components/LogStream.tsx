import { useEffect, useMemo, useRef, useState } from 'react'
import { Pause, Play } from 'lucide-react'

import { cx } from '../lib/cx'
import { formatClock } from '../lib/format'
import type { LogEntry } from '../types'
import styles from './LogStream.module.css'

export type LogFormat = 'logfmt' | 'json'

/** 已知级别按严重度排序，未知级别排在后面 */
const LEVEL_ORDER = ['DEBUG', 'INFO', 'WARN', 'FLAG', 'ERROR']

const LEVEL_CLASS: Record<string, string> = {
  DEBUG: 'debug',
  INFO: 'info',
  WARN: 'warn',
  FLAG: 'flag',
  ERROR: 'error',
}

interface LogStreamProps {
  entries: LogEntry[]
  ariaLabel: string
  /** 展示格式互切（logfmt ↔ JSON）；不传则固定按 logfmt 渲染字段 */
  formats?: LogFormat[]
  /** 事件流序号列 */
  showSeq?: boolean
  /** 暂停 / 继续（同时控制自动滚动） */
  pausable?: boolean
  height?: number
}

function formatValue(value: unknown): string {
  if (typeof value === 'string') return /[\s"]/.test(value) ? JSON.stringify(value) : value
  return String(value)
}

function renderFields(entry: LogEntry, format: LogFormat): string {
  const keys = Object.keys(entry.fields)
  if (keys.length === 0) return ''
  if (format === 'json') return JSON.stringify(entry.fields)
  return Object.entries(entry.fields)
    .map(([key, value]) => `${key}=${formatValue(value)}`)
    .join(' ')
}

export function LogStream({
  entries,
  ariaLabel,
  formats,
  showSeq = false,
  pausable = false,
  height = 320,
}: LogStreamProps) {
  const [level, setLevel] = useState('ALL')
  const [format, setFormat] = useState<LogFormat>(formats?.[0] ?? 'logfmt')
  const [paused, setPaused] = useState(false)
  const bodyRef = useRef<HTMLDivElement | null>(null)

  // 日志流按时间正序铺：最新一条落在底部，贴近 tail -f 的读法
  const ordered = useMemo(
    () => [...entries].sort((a, b) => (a.at < b.at ? -1 : a.at > b.at ? 1 : 0)),
    [entries],
  )

  const seqOf = useMemo(() => {
    const map = new Map<LogEntry, number>()
    ordered.forEach((entry, index) => map.set(entry, index + 1))
    return map
  }, [ordered])

  const levels = useMemo(() => {
    const present = new Set(ordered.map((entry) => entry.level))
    const known = LEVEL_ORDER.filter((item) => present.has(item))
    const extras = [...present].filter((item) => !LEVEL_ORDER.includes(item)).sort()
    return ['ALL', ...known, ...extras]
  }, [ordered])

  const visible = useMemo(
    () => (level === 'ALL' ? ordered : ordered.filter((entry) => entry.level === level)),
    [ordered, level],
  )

  useEffect(() => {
    if (paused) return
    const el = bodyRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [visible, format, level, paused])

  return (
    <div className={styles.wrap}>
      <div className={styles.toolbar}>
        <div className={styles.filters} role="group" aria-label="按级别过滤日志">
          {levels.map((item) => (
            <button
              key={item}
              type="button"
              className={cx(styles.filter, level === item && styles.filterOn)}
              aria-pressed={level === item}
              onClick={() => setLevel(item)}
            >
              {item === 'ALL' ? '全部' : item}
            </button>
          ))}
        </div>

        <div className={styles.tools}>
          {formats && formats.length > 1 && (
            <div className={styles.segment} role="group" aria-label="日志渲染格式">
              {formats.map((item) => (
                <button
                  key={item}
                  type="button"
                  className={cx(styles.segItem, format === item && styles.segOn)}
                  aria-pressed={format === item}
                  onClick={() => setFormat(item)}
                >
                  {item === 'logfmt' ? 'logfmt' : 'JSON'}
                </button>
              ))}
            </div>
          )}

          {pausable && (
            <button
              type="button"
              className={cx(styles.pause, paused && styles.pauseOn)}
              aria-pressed={paused}
              onClick={() => setPaused((value) => !value)}
            >
              {paused ? (
                <Play size={12} strokeWidth={2.3} aria-hidden="true" />
              ) : (
                <Pause size={12} strokeWidth={2.3} aria-hidden="true" />
              )}
              {paused ? '继续' : '暂停'}
            </button>
          )}
        </div>
      </div>

      <div className={styles.stream}>
        <div className={styles.body} ref={bodyRef} style={{ maxHeight: height }} aria-label={ariaLabel}>
          <ol className={styles.list}>
            {visible.map((entry, index) => {
              const fields = renderFields(entry, format)
              return (
                <li
                  key={`${entry.at}-${entry.module}-${index}`}
                  className={cx(styles.row, styles[LEVEL_CLASS[entry.level] ?? 'info'])}
                  style={{ animationDelay: `${Math.min(index, 18) * 14}ms` }}
                >
                  {showSeq && (
                    <span className={cx(styles.seq, 'tabular')}>
                      {String(seqOf.get(entry) ?? 0).padStart(4, '0')}
                    </span>
                  )}
                  <span className={cx(styles.time, 'tabular')}>{formatClock(entry.at)}</span>
                  <span className={styles.level}>{entry.level}</span>
                  <span className={styles.module}>{entry.module}</span>
                  <span className={styles.text}>
                    <span className={styles.message}>{entry.message}</span>
                    {fields && <span className={styles.fields}>{fields}</span>}
                  </span>
                </li>
              )
            })}
            {visible.length === 0 && <li className={styles.empty}>该级别暂无日志</li>}
          </ol>
        </div>
        <span className={styles.veil} aria-hidden="true" />
      </div>
    </div>
  )
}
