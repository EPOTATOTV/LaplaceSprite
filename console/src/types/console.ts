/**
 * 后端 JSON 契约的 TypeScript 镜像。
 * 对应 Java：io.laplace.sprite.core.model（report 模块对外序列化的形状）。
 * 字段名与后端保持一致，不要在此处做重命名。
 */

export type RunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'PASSED'
  | 'FAILED'
  | 'ABORTED'
  | 'SKIPPED'

/** CheatTierName：作弊配置档位（4.2） */
export type CheatTier = 'MIN' | 'DEFAULT' | 'MAX' | 'HIDDEN'

/** CheatType：作弊类型（4.2 / 第五章 CheatSpec.type），取自开源作弊客户端真实模块 */
export type CheatType =
  | 'SPEED'
  | 'FLY'
  | 'REACH'
  | 'AUTO_CLICKER'
  | 'KILL_AURA'
  | 'TIMER'
  | 'BLINK'
  | 'NO_FALL'
  | 'PHASE'
  | 'JESUS'
  | 'SCAFFOLD'
  | 'FAST_PLACE'
  | 'FAST_EAT'
  | 'AUTO_TOTEM'
  | 'XRAY'

/** BehaviorProfile：合法玩家行为模型（4.1 行为模型库） */
export type BehaviorProfile =
  | 'WALK'
  | 'RUN'
  | 'SPRINT'
  | 'JUMP'
  | 'SNEAK'
  | 'SWIM'
  | 'ELYTRA'
  | 'BOAT'
  | 'RIDE'
  | 'GODBRIDGE'
  | 'TELLY'
  | 'NINJA'
  | 'MLG'
  | 'PEARL'
  | 'KNOCKBACK_RESPONSE'
  | 'CLICKING'
  | 'TARGET_SWITCH'
  | 'MULTI_ENTITY'
  | 'INVENTORY_SORT'
  | 'PLACE'
  | 'MINE'

/** PlayerType：模拟玩家类型（11.4 Tab 名单） */
export type PlayerType = 'LEGIT' | 'CHEAT' | 'ADMIN'

/** InspectStatus：查端会话状态（11.4） */
export type InspectStatus = 'PENDING_INSPECT' | 'CONFIRMED' | 'FALSE_POSITIVE'

/** BatchPattern：网络包批处理模式（4.3） */
export type BatchPattern = 'NONE' | 'WIFI' | 'LTE'

/** PlatformKey.id()：family/mcVersion[/loaderVersion] */
export interface PlatformRef {
  id: string
}

/** 11.2 顶部六个指标卡 */
export interface MetricCards {
  todayRuns: number
  simulatedPlayers: number
  cheatSpecCount: number
  falsePositiveRate: number
  detectionRate: number
  gatePassed: number
}

/** 11.2 测试运行表的一行（TestRun 的表格投影） */
export interface TestRunRow {
  id: string
  target: string
  suite: string
  legitSamples: number
  falsePositiveRate: number
  detectionRate: number
  boundarySamples: number
  status: RunStatus
  platform: PlatformRef
  startedAt: string
  durationMs: number
}

/** GateResult.violations 的一项 */
export interface GateViolation {
  metric: string
  operator: string
  actual: number
  threshold: number
  message: string
}

/** 第五章 GateResult */
export interface GateResult {
  passed: boolean
  failedMetrics: string[]
  violations: GateViolation[]
}

/** MetricHistory.Point */
export interface MetricPoint {
  version: string
  at: string
  value: number
  breached: boolean
}

/** 11.2 误报/检出趋势图、12.4 基线 */
export interface MetricHistory {
  metric: string
  threshold: number
  points: MetricPoint[]
}

/** 11.2 夜间基准 / 12.4 基线 */
export interface NightlyBenchmarkResult {
  night: string
  passed: boolean
  durationMs: number
  totalRuns: number
  failedRuns: number
  baselineVersion: string
  regressionCreated: boolean
}

/** 11.6 模块健康（ModuleHealth） */
export interface ModuleHealth {
  module: string
  version: string
  status: RunStatus
  counters: Record<string, number>
}

/** DetectionBreakdown 派生：检测项 × 档位的检出率 */
export type DetectionByCheckTier = Record<string, Record<CheatTier, number>>

/** 各档位的合计检出率 */
export type DetectionByTier = Record<CheatTier, number>

/** 11.6 受测插件配置（ServerPluginConfig，服务端模式默认仅告警） */
export interface ServerPluginConfig {
  detectionEnabled: boolean
  kickEnabled: boolean
  banEnabled: boolean
  alertThreshold: number
  exemptions: string[]
  logLevel: string
}

/** 11.3 / 11.6 结构化日志条目（LogEntry）：logfmt / JSON 可被 EvidenceVault 直接归档 */
export interface LogEntry {
  at: string
  level: string
  module: string
  message: string
  fields: Record<string, unknown>
}

/** 11.3 实例健康（ServerHealth）：把测试服当生产服务器看 */
export interface ServerHealth {
  instanceId: string
  platform: PlatformRef
  tps: number
  mspt: number
  memoryUsedMb: number
  memoryMaxMb: number
  players: number
  simulatedPlayers: number
  pluginVersion: string
  status: RunStatus
}

/** 11.3 场景剧本运行进度（ScenarioRun） */
export interface ScenarioRun {
  scenarioId: string
  name: string
  progressPercent: number
  injectedSamples: number
  status: RunStatus
  platform: PlatformRef
}

/** 4.3 / 第五章 网络条件模型（NetworkModel） */
export interface NetworkModel {
  id: string
  latencyMinMs: number
  latencyMaxMs: number
  jitterMs: number
  spikeMs: number
  packetLossRate: number
  reorderRate: number
  batchPattern: BatchPattern
  clientVersion: string
  seed: number
}

/** 4.2 玩家端样本：USB 设备指纹（MCU VID） */
export interface UsbDevice {
  vid: string
  pid: string
  description: string
  mcu: boolean
}

/** 第五章 探针桩快照（ProbeSnapshot） */
export interface ProbeSnapshot {
  processes: string[]
  modules: string[]
  memoryPatterns: string[]
  usbDevices: UsbDevice[]
  registryHits: string[]
  networkHits: string[]
  signatures: string[]
  drivers: string[]
  injections: Record<string, unknown>
}

/** 11.4 查端会话（InspectSession） */
export interface InspectSession {
  sessionId: string
  playerId: string
  simId: string
  relatedFlags: string[]
  status: InspectStatus
  evidenceRefs: string[]
  openedAt: string
}

/** 11.4 模拟玩家（SimPlayer），来自 SimPlayerRegistry */
export interface SimPlayer {
  name: string
  simId: string
  type: PlayerType
  behavior: BehaviorProfile | null
  cheatSpecId: string | null
}

/** 11.4 红屏公示（RedScreenBroadcast） */
export interface RedScreenBroadcast {
  playerId: string
  playerName: string
  type: CheatType
  riskScore: number
  actionLabel: string
}

/** 11.5 受测客户端设置（ClientSettings） */
export interface ClientSettings {
  behavior: BehaviorProfile
  clickDistribution: string
  targetSwitch: boolean
  preInject: boolean
  autoRecord: boolean
  networkModelRef: string
}

/** 11.5 注入器状态（InjectorStatus） */
export interface InjectorStatus {
  cheatInjectionEnabled: boolean
  probeStubActive: boolean
  probeSnapshotRef: string | null
  reportedSeq: number
  evidenceRecording: boolean
}

/** 11.4 /ls 命令（OrchestratorCommand） */
export interface OrchestratorCommand {
  name: string
  args: string[]
  receipt: string
}

/** LiveViewStatus：11.8 画面监控墙会话状态 */
export type LiveViewStatus = 'IDLE' | 'CONNECTING' | 'LIVE' | 'CLOSED'

/** ScreenShareSession（11.8）：SDP offer / answer + ICE，信令经平台 WSS */
export interface ScreenShareSession {
  sessionId: string
  offerSdp: string
  answerSdp: string
  iceCandidates: string[]
  signalingChannel: string
  status: LiveViewStatus
}

/** LiveViewTarget（11.8 一键查看）：管理员旁观视角或任意受测实例第一视角 */
export interface LiveViewTarget {
  instanceId: string
  simId: string
  networkCondition: string
  platform: PlatformRef
  adminSpectator: boolean
}

/** LiveViewSession（11.8，≤3s 出画） */
export interface LiveViewSession {
  sessionId: string
  target: LiveViewTarget
  share: ScreenShareSession
  startedAt: string
  timeToFirstFrameMs: number
}

/** 11.8 画面监控墙（LiveViewGrid）：多路画面并排，≥8 路稳定 */
export interface LiveViewGrid {
  sessions: LiveViewSession[]
}

/** 11.8 / 12.5 性能悬浮窗（PerfOverlay）：压测拐点自动告警并触发录制归档 */
export interface PerfOverlay {
  instanceId: string
  tps: number
  mspt: number
  latencyMs: number
  kneeAlert: boolean
  recordingTriggered: boolean
}

/** 控制台一次性载荷（GET /api/console） */
export interface ConsoleData {
  cards: MetricCards
  runs: TestRunRow[]
  detectionByCheckTier: DetectionByCheckTier
  detectionByTier: DetectionByTier
  falsePositiveHistory: MetricHistory
  detectionHistory: MetricHistory
  nightly: NightlyBenchmarkResult[]
  gate: GateResult
  modules: ModuleHealth[]
  pluginConfig: ServerPluginConfig
  cheatSpecCount: number
  fingerprintCount: number
  liveView: LiveViewGrid
  logs: LogEntry[]
  /** 11.3 测试服实例健康 */
  serverHealth: ServerHealth[]
  /** 11.3 场景剧本进度 */
  scenarioRuns: ScenarioRun[]
  /** 11.3 网络模型预设 */
  networkModels: NetworkModel[]
  /** 11.8 一键查看目标列表 */
  liveViewTargets: LiveViewTarget[]
  /** 11.8 各实例性能悬浮窗 */
  perfOverlays: PerfOverlay[]
  /** 11.4 查端会话 */
  inspectSessions: InspectSession[]
  /** 11.4 模拟玩家名单（当前在场采样） */
  simPlayers: SimPlayer[]
  /** 11.4 红屏公示 */
  redScreen: RedScreenBroadcast
  /** 11.5 受测客户端设置 */
  clientSettings: ClientSettings
  /** 11.5 注入器状态 */
  injectorStatus: InjectorStatus
  /** 11.5 探针桩快照 */
  probeSnapshot: ProbeSnapshot
  /** 11.6 整合包模块健康（7 个模块） */
  modpackModules: ModuleHealth[]
  /** 11.6 整合包结构化输出日志 */
  modpackLogs: LogEntry[]
  /** 11.4 /ls 子命令定义 */
  orchestratorCommands: OrchestratorCommand[]
}
