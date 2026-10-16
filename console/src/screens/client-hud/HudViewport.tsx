import { Lock, Radio, ShieldQuestion, Waves } from 'lucide-react'

import { cx } from '../../lib/cx'
import type { ClientSettings, InjectorStatus, NetworkModel } from '../../types'
import styles from './HudViewport.module.css'

interface HudViewportProps {
  settings: ClientSettings
  injector: InjectorStatus
  network: NetworkModel | undefined
  scene: string
  seed: number
  localFlags: number
  reportedSeq: number
}

/** 第一视角 HUD：底子是全 CSS 的场景抽象，上面浮会话卡与注入器状态。 */
export function HudViewport({
  settings,
  injector,
  network,
  scene,
  seed,
  localFlags,
  reportedSeq,
}: HudViewportProps) {
  return (
    <div className={styles.viewport}>
      <span className={styles.sky} aria-hidden="true" />
      <span className={styles.ground} aria-hidden="true" />
      <span className={styles.grid} aria-hidden="true" />
      <span className={styles.vignette} aria-hidden="true" />

      <svg className={styles.crosshair} viewBox="0 0 20 20" aria-hidden="true">
        <line x1="10" y1="1" x2="10" y2="6.2" />
        <line x1="10" y1="13.8" x2="10" y2="19" />
        <line x1="1" y1="10" x2="6.2" y2="10" />
        <line x1="13.8" y1="10" x2="19" y2="10" />
        <circle cx="10" cy="10" r="0.9" />
      </svg>

      <section className={cx(styles.card, styles.session)} aria-label="会话卡">
        <header className={styles.cardHead}>
          <Waves size={13} strokeWidth={1.9} aria-hidden="true" />
          <span className={styles.cardTitle}>会话</span>
          <span className={cx(styles.cardId, 'tabular')}>sess-4a91-07</span>
        </header>
        <dl className={styles.rows}>
          <div>
            <dt>行为模型</dt>
            <dd className={styles.mono}>{settings.behavior}</dd>
          </div>
          <div>
            <dt>场景</dt>
            <dd>{scene}</dd>
          </div>
          <div>
            <dt>网络模型</dt>
            <dd className={styles.mono}>
              {network ? `${network.id} · ${network.latencyMinMs}–${network.latencyMaxMs}ms` : settings.networkModelRef}
            </dd>
          </div>
          <div>
            <dt>轨迹种子</dt>
            <dd className={cx(styles.mono, 'tabular')}>
              {seed}
              <em className={styles.seedTag}>可复现</em>
            </dd>
          </div>
        </dl>
        <div className={styles.flags}>
          <ShieldQuestion size={13} strokeWidth={1.9} aria-hidden="true" />
          <span>本地 flag</span>
          <strong className={cx(styles.flagCount, 'tabular')} data-on={localFlags > 1}>
            {localFlags}
          </strong>
        </div>
      </section>

      <section className={cx(styles.card, styles.injector)} aria-label="注入器状态">
        <header className={styles.cardHead}>
          <Radio size={13} strokeWidth={1.9} aria-hidden="true" />
          <span className={styles.cardTitle}>注入器状态</span>
          <span className={styles.state} data-on={injector.cheatInjectionEnabled}>
            {injector.cheatInjectionEnabled ? '注入中' : '待命'}
          </span>
        </header>
        <dl className={styles.rows}>
          <div>
            <dt>作弊注入</dt>
            <dd className={styles.state} data-on={injector.cheatInjectionEnabled}>
              {injector.cheatInjectionEnabled ? 'ON' : 'OFF'}
            </dd>
          </div>
          <div>
            <dt>ProbeStub</dt>
            <dd className={styles.state} data-on={injector.probeStubActive}>
              {injector.probeStubActive ? '激活' : '关闭'}
            </dd>
          </div>
          <div>
            <dt>探针快照</dt>
            <dd className={styles.mono}>{injector.probeSnapshotRef ?? '—'}</dd>
          </div>
          <div>
            <dt>上报 seq</dt>
            <dd className={cx(styles.mono, 'tabular')}>{reportedSeq}</dd>
          </div>
          <div>
            <dt>证据录制</dt>
            <dd className={styles.state} data-on={injector.evidenceRecording}>
              {injector.evidenceRecording ? '录制中' : '未录制'}
            </dd>
          </div>
        </dl>
      </section>

      <span className={styles.authority} title="客户端仅展示本地结果">
        <Lock size={12} strokeWidth={2} aria-hidden="true" />
        本地只读 · 无封禁 / 踢出权限
      </span>

      <div className={styles.hotbar} aria-hidden="true">
        {Array.from({ length: 9 }, (_, index) => (
          <span
            key={index}
            className={cx(styles.slot, index === 6 && styles.slotActive)}
            data-slot={index + 1}
          />
        ))}
      </div>

      <div className={styles.brand}>
        <span className={styles.brandDot} aria-hidden="true" />
        Laplace 测试框架 · 受测玩家控制台
      </div>
    </div>
  )
}
