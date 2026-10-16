import { useState } from 'react'
import { CloudUpload, Lock, PersonStanding, Syringe, Wifi } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import { Switch } from '../../components/Switch'
import { cx } from '../../lib/cx'
import type {
  BatchPattern,
  BehaviorProfile,
  ClientSettings,
  InjectorStatus,
  NetworkModel,
  ProbeSnapshot,
} from '../../types'
import styles from './ModMenu.module.css'

type TabKey = 'behavior' | 'inject' | 'network' | 'evidence'

const TABS: Array<{ key: TabKey; label: string; icon: LucideIcon }> = [
  { key: 'behavior', label: '行为', icon: PersonStanding },
  { key: 'inject', label: '作弊注入', icon: Syringe },
  { key: 'network', label: '网络条件', icon: Wifi },
  { key: 'evidence', label: '上报与证据', icon: CloudUpload },
]

const BEHAVIOR_OPTIONS: BehaviorProfile[] = ['WALK', 'RUN', 'SPRINT', 'SNEAK', 'JUMP', 'MINE', 'PLACE', 'CLICKING']
const CLICK_DISTRIBUTIONS = ['uniform-1-12', 'butterfly', 'drag'] as const
const CHEAT_GROUPS = ['合法基线', '作弊注入', '阈值边界'] as const

const BATCH_LABEL: Record<BatchPattern, string> = {
  NONE: '无批处理',
  WIFI: 'Wi-Fi 批处理',
  LTE: 'LTE 批处理',
}

interface ModMenuProps {
  settings: ClientSettings
  injector: InjectorStatus
  models: NetworkModel[]
  probe: ProbeSnapshot
  cheatSampleCount: number
  localFlags: number
  reportedSeq: number
  onSettings: (patch: Partial<ClientSettings>) => void
  onInjector: (patch: Partial<InjectorStatus>) => void
}

export function ModMenu({
  settings,
  injector,
  models,
  probe,
  cheatSampleCount,
  localFlags,
  reportedSeq,
  onSettings,
  onInjector,
}: ModMenuProps) {
  const [tab, setTab] = useState<TabKey>('behavior')
  const network = models.find((model) => model.id === settings.networkModelRef) ?? models[0]

  return (
    <div className={styles.wrap}>
      <div className={styles.tabs} role="tablist" aria-label="模组菜单">
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            className={cx(styles.tab, tab === key && styles.tabOn)}
            onClick={() => setTab(key)}
          >
            <Icon size={13} strokeWidth={1.9} aria-hidden="true" />
            {label}
          </button>
        ))}
      </div>

      <div key={tab} className={styles.panel} role="tabpanel" aria-label={TABS.find((item) => item.key === tab)?.label}>
        {tab === 'behavior' && (
          <>
            <div className={styles.field}>
              <span className={styles.fieldLabel}>行为模型</span>
              <div className={styles.options} role="radiogroup" aria-label="行为模型">
                {BEHAVIOR_OPTIONS.map((option, index) => (
                  <button
                    key={option}
                    type="button"
                    role="radio"
                    aria-checked={settings.behavior === option}
                    className={cx(styles.option, settings.behavior === option && styles.optionOn)}
                    style={{ animationDelay: `${index * 26}ms` }}
                    onClick={() => onSettings({ behavior: option })}
                  >
                    {option}
                  </button>
                ))}
              </div>
            </div>

            <div className={styles.field}>
              <span className={styles.fieldLabel}>点击分布</span>
              <div className={styles.options} role="radiogroup" aria-label="点击分布">
                {CLICK_DISTRIBUTIONS.map((option) => (
                  <button
                    key={option}
                    type="button"
                    role="radio"
                    aria-checked={settings.clickDistribution === option}
                    className={cx(styles.option, settings.clickDistribution === option && styles.optionOn)}
                    onClick={() => onSettings({ clickDistribution: option })}
                  >
                    {option}
                  </button>
                ))}
              </div>
            </div>

            <div className={styles.switchRow}>
              <span className={styles.switchLabel}>目标切换</span>
              <Switch
                checked={settings.targetSwitch}
                onChange={(value) => onSettings({ targetSwitch: value })}
                label="目标切换"
                hideLabel
              />
            </div>
          </>
        )}

        {tab === 'inject' && (
          <>
            <div className={styles.switchRow}>
              <span className={styles.switchLabel}>
                作弊注入开关
                <em>仅作用于本地样本链路</em>
              </span>
              <Switch
                checked={injector.cheatInjectionEnabled}
                onChange={(value) => onInjector({ cheatInjectionEnabled: value })}
                label="作弊注入开关"
                tone="danger"
                hideLabel
              />
            </div>

            <div className={styles.switchRow}>
              <span className={styles.switchLabel}>作弊样本预注入</span>
              <Switch
                checked={settings.preInject}
                onChange={(value) => onSettings({ preInject: value })}
                label="作弊样本预注入"
                tone="warn"
                hideLabel
              />
            </div>

            <dl className={styles.rows}>
              <div>
                <dt>样本库</dt>
                <dd className="tabular">{cheatSampleCount} 项（类型 × 档位）</dd>
              </div>
              <div>
                <dt>注入档位</dt>
                <dd>MIN / DEFAULT / MAX / HIDDEN</dd>
              </div>
            </dl>

            <div className={styles.chips}>
              {CHEAT_GROUPS.map((group, index) => (
                <span key={group} className={styles.chip} style={{ animationDelay: `${index * 40}ms` }}>
                  {group}
                </span>
              ))}
            </div>
          </>
        )}

        {tab === 'network' && (
          <>
            <div className={styles.options} role="radiogroup" aria-label="网络条件">
              {models.map((model) => (
                <button
                  key={model.id}
                  type="button"
                  role="radio"
                  aria-checked={settings.networkModelRef === model.id}
                  className={cx(styles.option, settings.networkModelRef === model.id && styles.optionOn)}
                  onClick={() => onSettings({ networkModelRef: model.id })}
                >
                  {model.id}
                </button>
              ))}
            </div>

            <dl className={styles.rows}>
              <div>
                <dt>延迟段</dt>
                <dd className="tabular">
                  {network.latencyMinMs}–{network.latencyMaxMs} ms
                </dd>
              </div>
              <div>
                <dt>抖动 / 尖峰</dt>
                <dd className="tabular">
                  ±{network.jitterMs} / {network.spikeMs > 0 ? `${network.spikeMs} ms` : '—'}
                </dd>
              </div>
              <div>
                <dt>丢包 / 重排</dt>
                <dd className="tabular">
                  {(network.packetLossRate * 100).toFixed(1)}% / {(network.reorderRate * 100).toFixed(1)}%
                </dd>
              </div>
              <div>
                <dt>批处理</dt>
                <dd>{BATCH_LABEL[network.batchPattern]}</dd>
              </div>
            </dl>
          </>
        )}

        {tab === 'evidence' && (
          <>
            <div className={styles.switchRow}>
              <span className={styles.switchLabel}>ProbeStub</span>
              <Switch
                checked={injector.probeStubActive}
                onChange={(value) => onInjector({ probeStubActive: value })}
                label="ProbeStub 激活"
                hideLabel
              />
            </div>

            <div className={styles.switchRow}>
              <span className={styles.switchLabel}>证据录制</span>
              <Switch
                checked={injector.evidenceRecording}
                onChange={(value) => onInjector({ evidenceRecording: value })}
                label="证据录制"
                hideLabel
              />
            </div>

            <div className={styles.switchRow}>
              <span className={styles.switchLabel}>证据自动录制</span>
              <Switch
                checked={settings.autoRecord}
                onChange={(value) => onSettings({ autoRecord: value })}
                label="证据自动录制"
                hideLabel
              />
            </div>

            <dl className={styles.rows}>
              <div>
                <dt>上报 seq</dt>
                <dd className="tabular">{reportedSeq}</dd>
              </div>
              <div>
                <dt>本地 flag</dt>
                <dd className="tabular">{localFlags}</dd>
              </div>
              <div>
                <dt>探针快照</dt>
                <dd className={styles.mono}>{injector.probeSnapshotRef ?? '—'}</dd>
              </div>
              <div>
                <dt>进程指纹</dt>
                <dd className="tabular">{probe.processes.length} 项</dd>
              </div>
            </dl>

            <p className={styles.boundary}>
              <Lock size={12} strokeWidth={2} aria-hidden="true" />
              客户端仅展示本地检测结果，处置动作由服务端与管理员执行，本端无封禁 / 踢出权限。
            </p>
          </>
        )}
      </div>
    </div>
  )
}
