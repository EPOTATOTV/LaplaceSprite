import { ShieldAlert } from 'lucide-react'

import { Switch } from '../../components/Switch'
import { cx } from '../../lib/cx'
import type { ServerPluginConfig } from '../../types'
import styles from './PluginConfigPanel.module.css'

const LOG_LEVELS = ['DEBUG', 'INFO', 'WARN', 'ERROR'] as const

interface PluginConfigPanelProps {
  config: ServerPluginConfig
  onChange: (patch: Partial<ServerPluginConfig>) => void
}

export function PluginConfigPanel({ config, onChange }: PluginConfigPanelProps) {
  const enforcing = config.kickEnabled || config.banEnabled

  return (
    <div className={styles.wrap}>
      <div className={styles.controls}>
        <div className={styles.switchRow}>
          <span className={styles.label}>
            检测开关
            <em>命中后先出告警</em>
          </span>
          <Switch
            checked={config.detectionEnabled}
            onChange={(value) => onChange({ detectionEnabled: value })}
            label="检测开关"
            tone="ok"
            hideLabel
          />
        </div>
        <div className={styles.switchRow}>
          <span className={styles.label}>
            踢出
            <em>默认关闭</em>
          </span>
          <Switch
            checked={config.kickEnabled}
            onChange={(value) => onChange({ kickEnabled: value })}
            label="踢出"
            tone="danger"
            hideLabel
          />
        </div>
        <div className={styles.switchRow}>
          <span className={styles.label}>
            封禁
            <em>默认关闭</em>
          </span>
          <Switch
            checked={config.banEnabled}
            onChange={(value) => onChange({ banEnabled: value })}
            label="封禁"
            tone="danger"
            hideLabel
          />
        </div>
      </div>

      <div className={styles.field}>
        <span className={styles.label}>告警阈值</span>
        <div className={styles.sliderRow}>
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={config.alertThreshold}
            onChange={(event) => onChange({ alertThreshold: Number(event.target.value) })}
            className={styles.range}
            aria-label="告警阈值，0 到 100"
          />
          <span className={cx(styles.thresholdValue, 'tabular')}>{config.alertThreshold}</span>
        </div>
      </div>

      <div className={styles.field}>
        <span className={styles.label}>豁免规则</span>
        <div className={styles.chips}>
          {config.exemptions.map((item, index) => (
            <span key={item} className={styles.chip} style={{ animationDelay: `${index * 36}ms` }}>
              {item}
            </span>
          ))}
        </div>
      </div>

      <div className={styles.field}>
        <span className={styles.label}>日志级别</span>
        <div className={styles.levels} role="radiogroup" aria-label="日志级别">
          {LOG_LEVELS.map((level) => (
            <button
              key={level}
              type="button"
              role="radio"
              aria-checked={config.logLevel === level}
              className={cx(styles.level, config.logLevel === level && styles.levelOn)}
              onClick={() => onChange({ logLevel: level })}
            >
              {level}
            </button>
          ))}
        </div>
      </div>

      <p className={cx(styles.note, enforcing && styles.noteDanger)}>
        <ShieldAlert size={12} strokeWidth={2} aria-hidden="true" />
        {enforcing
          ? '写操作已开启：服务端会直接踢出 / 封禁命中玩家，请确认已过复核流程。'
          : '服务端模式默认仅告警：踢出与封禁保持关闭，处置动作留给人工复核。'}
      </p>
    </div>
  )
}
