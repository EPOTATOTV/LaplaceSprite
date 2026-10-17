import { useState } from 'react'

import { Switch } from '../../components/Switch'
import { cx } from '../../lib/cx'
import { formatPercent } from '../../lib/format'
import type { BatchPattern, NetworkModel } from '../../types'
import styles from './NetworkPanel.module.css'

/** 延迟刻度上界：与 default / lte 的 350ms 上界对齐 */
const SPAN_MAX = 350

const BATCH_LABEL: Record<BatchPattern, string> = {
  NONE: '无批处理',
  WIFI: 'Wi-Fi 批处理',
  LTE: 'LTE 批处理',
}

export function NetworkPanel({ models }: { models: NetworkModel[] }) {
  const [activeId, setActiveId] = useState(models[0].id)
  const model = models.find((item) => item.id === activeId) ?? models[0]

  const [lossOn, setLossOn] = useState(model.packetLossRate > 0)
  const [reorderOn, setReorderOn] = useState(model.reorderRate > 0)

  const rangeLeft = (model.latencyMinMs / SPAN_MAX) * 100
  const rangeWidth = ((model.latencyMaxMs - model.latencyMinMs) / SPAN_MAX) * 100

  return (
    <div className={styles.wrap}>
      <div className={styles.models} role="group" aria-label="网络模型预设">
        {models.map((item) => (
          <button
            key={item.id}
            type="button"
            className={cx(styles.model, item.id === activeId && styles.modelOn)}
            aria-pressed={item.id === activeId}
            onClick={() => {
              setActiveId(item.id)
              setLossOn(item.packetLossRate > 0)
              setReorderOn(item.reorderRate > 0)
            }}
          >
            <span className={styles.modelId}>{item.id}</span>
            <span className={cx(styles.modelRange, 'tabular')}>
              {item.latencyMinMs}–{item.latencyMaxMs}ms
            </span>
          </button>
        ))}
      </div>

      <div className={styles.band}>
        <span className={styles.bandTrack} aria-hidden="true">
          <span
            className={styles.bandRange}
            style={{ left: `${rangeLeft}%`, width: `${rangeWidth}%` }}
          />
          {model.spikeMs > 0 && (
            <span
              className={styles.bandSpike}
              style={{ left: `${(model.spikeMs / SPAN_MAX) * 100}%` }}
            />
          )}
        </span>
        <div className={styles.bandScale} aria-hidden="true">
          <span>0</span>
          <span>100</span>
          <span>200</span>
          <span>350 ms</span>
        </div>
      </div>

      <dl className={styles.readouts}>
        <div>
          <dt>延迟段</dt>
          <dd className="tabular">
            {model.latencyMinMs}–{model.latencyMaxMs} ms
          </dd>
        </div>
        <div>
          <dt>抖动</dt>
          <dd className="tabular">{model.jitterMs > 0 ? `±${model.jitterMs} ms` : '无'}</dd>
        </div>
        <div>
          <dt>尖峰</dt>
          <dd className="tabular">{model.spikeMs > 0 ? `${model.spikeMs} ms` : '—'}</dd>
        </div>
        <div>
          <dt>批处理</dt>
          <dd>{BATCH_LABEL[model.batchPattern]}</dd>
        </div>
        <div>
          <dt>客户端</dt>
          <dd className="tabular">{model.clientVersion}</dd>
        </div>
        <div>
          <dt>采样种子</dt>
          <dd className="tabular">{model.seed}</dd>
        </div>
      </dl>

      <div className={styles.toggles}>
        <div className={styles.toggleRow}>
          <Switch checked={lossOn} onChange={setLossOn} label="随机丢包" tone="warn" />
          <span className={cx(styles.toggleValue, 'tabular')}>
            {lossOn ? formatPercent(model.packetLossRate, 1) : '0%'}
          </span>
        </div>
        <div className={styles.toggleRow}>
          <Switch checked={reorderOn} onChange={setReorderOn} label="乱序重排" tone="warn" />
          <span className={cx(styles.toggleValue, 'tabular')}>
            {reorderOn ? formatPercent(model.reorderRate, 1) : '0%'}
          </span>
        </div>
      </div>
    </div>
  )
}
