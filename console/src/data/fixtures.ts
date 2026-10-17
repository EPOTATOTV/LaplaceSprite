import type {
  SampleTier,
  ClientSettings,
  ConsoleData,
  DetectionByCheckTier,
  DetectionByTier,
  InjectorStatus,
  InspectSession,
  LiveViewSession,
  LiveViewTarget,
  LogEntry,
  MetricHistory,
  ModuleHealth,
  NetworkModel,
  NightlyBenchmarkResult,
  OrchestratorCommand,
  PerfOverlay,
  ProbeSnapshot,
  RedScreenBroadcast,
  RunStatus,
  SampleCategory,
  SampleTypeRow,
  ScenarioRun,
  ServerHealth,
  ServerPluginConfig,
  SimPlayer,
  TargetTypeRow,
  TestRunRow,
} from '../types'

/* ---------------------------------------------------------------
   自洽的固定数据。

   口径（自下而上算出来，不是写死的展示数字）：
     今日运行      126 条
     模拟人数      32,480（= Σ 各行 legitSamples，行为基线样本）
     功能通过率    Σ functionalSmokePassed / Σ functionalSmokeTotal
     性能基线      Σ 逐行取最小 delta（一次劣化即整体劣化）
     误报率        2 / 32480 ≈ 0.00616%（检测类专项，阈值 0.01%）
     检出率        Σ 命中 / Σ 注入样本（检测类专项）
   --------------------------------------------------------------- */

const RUN_COUNT = 126
const SIMULATED_PLAYERS = 32_480
const CHEAT_SPEC_COUNT = 144
/** 12 类目标 × 4 档位 × 3 样本变体 */
const SAMPLE_SPEC_COUNT = 144
const FINGERPRINT_COUNT = 512
const FP_PER_ROW = 1
const FP_ROW_COUNT = 2

/** 误报率上限 0.01%，检出率下限 95% */
export const FALSE_POSITIVE_THRESHOLD = 0.0001
export const DETECTION_THRESHOLD = 0.95

/** 固定种子，保证每次刷新数据一致 */
function mulberry32(seed: number): () => number {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const rand = mulberry32(0x1a91ac7)

const pick = <T,>(list: readonly T[]): T => list[Math.floor(rand() * list.length)] as T

const PLATFORMS = [
  'fabric/1.20.1/0.15.7',
  'fabric/1.21.1/0.16.5',
  'forge/1.19.2/43.2.0',
  'neoforge/1.21.1/21.1.9',
  'paper/1.20.4',
  'vanilla/1.21.1',
] as const

const SUITE_BY_INDEX = ['legit', 'cheat', 'boundary', 'concurrency', 'cheat', 'legit'] as const

const now = new Date()

function startedAt(minutesAgo: number): string {
  return new Date(now.getTime() - minutesAgo * 60_000).toISOString()
}

function atDaysAgo(days: number, hour: number): string {
  const d = new Date(now.getTime() - days * 86_400_000)
  d.setHours(hour, 0, 0, 0)
  return d.toISOString()
}

function nightLabel(daysAgo: number): string {
  const d = new Date(now.getTime() - daysAgo * 86_400_000)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/* ---------------- 测试运行 ---------------- */

interface RawRun extends TestRunRow {
  cheatSamples: number
  detectedSamples: number
  falsePositives: number
}

function buildRuns(): RawRun[] {
  const legit: number[] = []
  for (let i = 0; i < RUN_COUNT; i += 1) legit.push(232 + Math.floor(rand() * 64))

  let diff = legit.reduce((a, b) => a + b, 0) - SIMULATED_PLAYERS
  let cursor = 0
  while (diff !== 0 && cursor < legit.length) {
    const value = legit[cursor] as number
    if (diff > 0) {
      const take = Math.min(diff, value - 200)
      legit[cursor] = value - take
      diff -= take
    } else {
      const take = Math.min(-diff, 420 - value)
      legit[cursor] = value + take
      diff += take
    }
    cursor += 1
  }
  if (diff !== 0) legit[RUN_COUNT - 1] = (legit[RUN_COUNT - 1] as number) - diff

  const statuses: RunStatus[] = new Array<RunStatus>(RUN_COUNT).fill('PASSED')
  const slots: Array<[RunStatus, number]> = [
    ['RUNNING', 2],
    ['QUEUED', 2],
    ['ABORTED', 1],
    ['SKIPPED', 1],
    ['FAILED', 4],
  ]
  let place = 0
  for (const [status, count] of slots) {
    for (let i = 0; i < count; i += 1) {
      statuses[place] = status
      place += 1
    }
  }

  const failedIndexes: number[] = []
  statuses.forEach((status, i) => {
    if (status === 'FAILED') failedIndexes.push(i)
  })

  const fpIndexes = failedIndexes.slice(0, FP_ROW_COUNT)
  const detectionFailIndexes = failedIndexes.slice(FP_ROW_COUNT)

  const rows: RawRun[] = []
  for (let i = 0; i < RUN_COUNT; i += 1) {
    // 误报发生在行为基线样本上，所以这两行固定走 legit 套件；
    // 其余失败行走正常套件轮转，用来制造检出率的下探。
    const suite = fpIndexes.includes(i) ? 'legit' : SUITE_BY_INDEX[i % SUITE_BY_INDEX.length]
    const legitSamples = legit[i] as number

    let cheatSamples = 0
    if (suite === 'cheat') cheatSamples = 72 + Math.floor(rand() * 96)
    else if (suite === 'concurrency') cheatSamples = 48 + Math.floor(rand() * 64)
    else if (suite === 'boundary') cheatSamples = 24 + Math.floor(rand() * 40)

    let rate = 0.958 + rand() * 0.042
    if (detectionFailIndexes.includes(i)) rate = 0.845 + rand() * 0.06
    const detectedSamples = cheatSamples === 0 ? 0 : Math.round(cheatSamples * rate)

    const falsePositives = fpIndexes.includes(i) ? FP_PER_ROW : 0

    const boundarySamples =
      suite === 'boundary' ? 96 + Math.floor(rand() * 128) : 24 + Math.floor(rand() * 40)

    const status = statuses[i] as RunStatus

    // 排队 / 跳过尚未执行功能冒烟，total 记 0 表示「不适用」，不能按 0% 计入通过率。
    let functionalSmokePassed = 0
    let functionalSmokeTotal = 0
    if (status !== 'QUEUED' && status !== 'SKIPPED') {
      functionalSmokeTotal = 48 + Math.floor(rand() * 48)
      if (status === 'PASSED') functionalSmokePassed = functionalSmokeTotal
      else if (status === 'FAILED') {
        functionalSmokePassed = functionalSmokeTotal - (2 + Math.floor(rand() * 4))
      } else if (status === 'RUNNING') {
        functionalSmokePassed = functionalSmokeTotal - Math.floor(rand() * 2)
      } else {
        functionalSmokePassed = functionalSmokeTotal - (8 + Math.floor(rand() * 5))
      }
    }

    // 一条明确的劣化样本，让性能基线卡能看到负向状态；其余在 ±1% 内波动。
    let performanceBaselineDelta = -0.004 + rand() * 0.012
    if (i === 7) performanceBaselineDelta = -0.021
    else if (i === 19 || i === 44) performanceBaselineDelta = -0.009 - rand() * 0.004

    rows.push({
      id: `run-${(0x2f00 + i).toString(16)}`,
      target: `tgt-${(0x9000 + i * 7).toString(16)}`,
      suite,
      legitSamples,
      falsePositiveRate: legitSamples === 0 ? 0 : falsePositives / legitSamples,
      detectionRate: cheatSamples === 0 ? 1 : detectedSamples / cheatSamples,
      boundarySamples,
      functionalSmokePassed,
      functionalSmokeTotal,
      performanceBaselineOk: true,
      performanceBaselineDelta,
      status,
      platform: { id: pick(PLATFORMS) },
      startedAt: startedAt(i * 6 + Math.floor(rand() * 5)),
      durationMs: 18_000 + Math.floor(rand() * 96_000),
      cheatSamples,
      detectedSamples,
      falsePositives,
    })
  }
  return rows
}

const rawRuns = buildRuns()

const totalLegit = rawRuns.reduce((a, r) => a + r.legitSamples, 0)
const totalFalsePositives = rawRuns.reduce((a, r) => a + r.falsePositives, 0)
const totalCheat = rawRuns.reduce((a, r) => a + r.cheatSamples, 0)
const totalDetected = rawRuns.reduce((a, r) => a + r.detectedSamples, 0)

const aggregateFalsePositiveRate = totalFalsePositives / totalLegit
const aggregateDetectionRate = totalDetected / totalCheat
const passedRuns = rawRuns.filter((r) => r.status === 'PASSED').length

const totalFunctionalSmoke = rawRuns.reduce((a, r) => a + r.functionalSmokeTotal, 0)
const totalFunctionalPassed = rawRuns.reduce((a, r) => a + r.functionalSmokePassed, 0)
const functionalPassRate =
  totalFunctionalSmoke === 0 ? 1 : totalFunctionalPassed / totalFunctionalSmoke

// 与 MetricAggregator.sum 同口径：布尔项取与，delta 取最小，一次劣化即整体劣化。
const performanceBaselineOk = rawRuns.every((r) => r.performanceBaselineOk)
const performanceBaselineDelta = rawRuns.reduce(
  (min, r) => Math.min(min, r.performanceBaselineDelta),
  rawRuns[0]?.performanceBaselineDelta ?? 0,
)

/** 去掉内部统计字段后的表格投影 */
const runs: TestRunRow[] = rawRuns.map((r) => ({
  id: r.id,
  target: r.target,
  suite: r.suite,
  legitSamples: r.legitSamples,
  falsePositiveRate: r.falsePositiveRate,
  detectionRate: r.detectionRate,
  boundarySamples: r.boundarySamples,
  functionalSmokePassed: r.functionalSmokePassed,
  functionalSmokeTotal: r.functionalSmokeTotal,
  performanceBaselineOk: r.performanceBaselineOk,
  performanceBaselineDelta: r.performanceBaselineDelta,
  status: r.status,
  platform: r.platform,
  startedAt: r.startedAt,
  durationMs: r.durationMs,
}))

/* ---------------- 检出率：检测项 × 档位 ---------------- */

const CHECK_TIER_RATE: DetectionByCheckTier = {
  移动速度: { MIN: 0.934, DEFAULT: 0.981, MAX: 0.996, HIDDEN: 0.878 },
  攻击距离: { MIN: 0.918, DEFAULT: 0.972, MAX: 0.992, HIDDEN: 0.841 },
  自动点击: { MIN: 0.952, DEFAULT: 0.988, MAX: 0.999, HIDDEN: 0.924 },
  无坠落伤害: { MIN: 0.842, DEFAULT: 0.936, MAX: 0.968, HIDDEN: 0.792 },
  瞬移相位: { MIN: 0.896, DEFAULT: 0.957, MAX: 0.981, HIDDEN: 0.828 },
  自动放置: { MIN: 0.928, DEFAULT: 0.969, MAX: 0.989, HIDDEN: 0.867 },
  挖矿异常: { MIN: 0.788, DEFAULT: 0.903, MAX: 0.941, HIDDEN: 0.734 },
  'Xray 透视': { MIN: 0.874, DEFAULT: 0.934, MAX: 0.962, HIDDEN: 0.803 },
}

const TIERS: readonly SampleTier[] = ['MIN', 'DEFAULT', 'MAX', 'HIDDEN']

function tierMeans(byCheck: DetectionByCheckTier): DetectionByTier {
  const checks = Object.values(byCheck)
  const mean = (tier: SampleTier): number =>
    checks.reduce((a, c) => a + c[tier], 0) / (checks.length || 1)
  return {
    MIN: mean('MIN'),
    DEFAULT: mean('DEFAULT'),
    MAX: mean('MAX'),
    HIDDEN: mean('HIDDEN'),
  }
}

const detectionByTier = tierMeans(CHECK_TIER_RATE)

/* ---------------- 样本类型 × 档位断言结果 ---------------- */

interface SampleSeed {
  sampleType: string
  category: SampleCategory
  weight: number
  rates: Record<SampleTier, number>
}

const TIER_WEIGHTS: Record<SampleTier, number> = {
  MIN: 0.24,
  DEFAULT: 0.34,
  MAX: 0.22,
  HIDDEN: 0.2,
}

// 功能类样本：五个大类各两个样本类型，权重合计 1。
const FUNCTIONAL_SAMPLE_SEEDS: readonly SampleSeed[] = [
  {
    sampleType: '注册与配方',
    category: 'content',
    weight: 0.12,
    rates: { MIN: 0.997, DEFAULT: 0.999, MAX: 1, HIDDEN: 0.995 },
  },
  {
    sampleType: '生物与维度',
    category: 'content',
    weight: 0.1,
    rates: { MIN: 0.994, DEFAULT: 0.998, MAX: 0.999, HIDDEN: 0.992 },
  },
  {
    sampleType: '机器 tick',
    category: 'tech',
    weight: 0.12,
    rates: { MIN: 0.991, DEFAULT: 0.997, MAX: 0.999, HIDDEN: 0.988 },
  },
  {
    sampleType: '物流与配方同步',
    category: 'tech',
    weight: 0.1,
    rates: { MIN: 0.996, DEFAULT: 0.999, MAX: 1, HIDDEN: 0.994 },
  },
  {
    sampleType: '帧时间与渲染',
    category: 'perf',
    weight: 0.11,
    rates: { MIN: 0.993, DEFAULT: 0.998, MAX: 0.999, HIDDEN: 0.99 },
  },
  {
    sampleType: 'TPS 与内存',
    category: 'perf',
    weight: 0.09,
    rates: { MIN: 0.988, DEFAULT: 0.996, MAX: 0.999, HIDDEN: 0.984 },
  },
  {
    sampleType: '协议握手',
    category: 'network',
    weight: 0.1,
    rates: { MIN: 0.997, DEFAULT: 1, MAX: 1, HIDDEN: 0.996 },
  },
  {
    sampleType: '延迟与丢包',
    category: 'network',
    weight: 0.09,
    rates: { MIN: 0.992, DEFAULT: 0.997, MAX: 0.999, HIDDEN: 0.989 },
  },
  {
    sampleType: '异常输入',
    category: 'anomaly',
    weight: 0.09,
    rates: { MIN: 0.978, DEFAULT: 0.991, MAX: 0.997, HIDDEN: 0.972 },
  },
  {
    sampleType: '依赖与资源回退',
    category: 'anomaly',
    weight: 0.08,
    rates: { MIN: 0.994, DEFAULT: 0.998, MAX: 1, HIDDEN: 0.991 },
  },
]

// 检测类样本：按检出项归类，样本总和与运行表的注入样本严格对齐。
const CHEAT_SAMPLE_SEEDS: readonly SampleSeed[] = [
  {
    sampleType: '移动类检测',
    category: 'cheat',
    weight: 0.32,
    rates: { MIN: 0.962, DEFAULT: 0.989, MAX: 0.998, HIDDEN: 0.912 },
  },
  {
    sampleType: '战斗类检测',
    category: 'cheat',
    weight: 0.3,
    rates: { MIN: 0.97, DEFAULT: 0.992, MAX: 0.999, HIDDEN: 0.928 },
  },
  {
    sampleType: '交互类检测',
    category: 'cheat',
    weight: 0.23,
    rates: { MIN: 0.976, DEFAULT: 0.994, MAX: 1, HIDDEN: 0.941 },
  },
  {
    sampleType: '透视类检测',
    category: 'cheat',
    weight: 0.15,
    rates: { MIN: 0.95, DEFAULT: 0.984, MAX: 0.996, HIDDEN: 0.888 },
  },
]

/** 最大余数法分配整数，保证总和恰好等于 total。 */
function allocate(total: number, weights: readonly number[]): number[] {
  const out = weights.map(() => 0)
  if (total <= 0 || weights.length === 0) return out
  const sum = weights.reduce((a, w) => a + w, 0)
  if (sum <= 0) return out

  const raw = weights.map((w) => (w / sum) * total)
  raw.forEach((value, index) => {
    out[index] = Math.floor(value)
  })
  let remainder = total - out.reduce((a, v) => a + v, 0)
  const order = raw
    .map((value, index) => ({ index, frac: value - Math.floor(value) }))
    .sort((a, b) => b.frac - a.frac)
  let cursor = 0
  while (remainder > 0) {
    const slot = order[cursor % order.length]
    if (slot) out[slot.index] = (out[slot.index] ?? 0) + 1
    remainder -= 1
    cursor += 1
  }
  return out
}

/**
 * 先生成各档位样本数，再把命中数微调到给定的总数。
 * 命中数不允许超过样本数，因此逐格 +1 / -1，而不是直接按比例缩放。
 */
function buildSampleGroup(
  seeds: readonly SampleSeed[],
  totalSamples: number,
  totalHits: number,
): SampleTypeRow[] {
  const samplesByType = allocate(
    totalSamples,
    seeds.map((seed) => seed.weight),
  )
  const cells: Array<{ seed: SampleSeed; tier: SampleTier; samples: number }> = []

  seeds.forEach((seed, seedIndex) => {
    const tierSamples = allocate(
      samplesByType[seedIndex] ?? 0,
      TIERS.map((tier) => TIER_WEIGHTS[tier]),
    )
    TIERS.forEach((tier, tierIndex) => {
      cells.push({ seed, tier, samples: tierSamples[tierIndex] ?? 0 })
    })
  })

  const hits = cells.map((cell) =>
    Math.min(cell.samples, Math.round(cell.samples * cell.seed.rates[cell.tier])),
  )
  let diff = totalHits - hits.reduce((a, h) => a + h, 0)
  let cursor = 0
  let guard = 0
  while (diff !== 0 && guard < cells.length * 2000) {
    const index = cursor % cells.length
    const cell = cells[index]
    const current = hits[index] ?? 0
    if (cell && diff > 0 && current < cell.samples) {
      hits[index] = current + 1
      diff -= 1
    } else if (cell && diff < 0 && current > 0) {
      hits[index] = current - 1
      diff += 1
    }
    cursor += 1
    guard += 1
  }

  return seeds.map((seed, seedIndex) => ({
    sampleType: seed.sampleType,
    category: seed.category,
    tiers: TIERS.map((tier, tierIndex) => {
      const cellIndex = seedIndex * TIERS.length + tierIndex
      return {
        tier,
        hits: hits[cellIndex] ?? 0,
        samples: cells[cellIndex]?.samples ?? 0,
      }
    }),
  }))
}

const sampleTypeBreakdown: SampleTypeRow[] = [
  ...buildSampleGroup(FUNCTIONAL_SAMPLE_SEEDS, totalFunctionalSmoke, totalFunctionalPassed),
  ...buildSampleGroup(CHEAT_SAMPLE_SEEDS, totalCheat, totalDetected),
]

/* ---------------- 指标趋势 ---------------- */

const VERSIONS = [
  'v0.11.0',
  'v0.11.4',
  'v0.12.0',
  'v0.12.3',
  'v0.13.0',
  'v0.13.2',
  'v0.14.0',
  'v0.14.2',
  'v0.15.0',
  'v0.15.1',
] as const

function history(
  metric: string,
  threshold: number,
  breachedWhenAbove: boolean,
  values: readonly number[],
): MetricHistory {
  return {
    metric,
    threshold,
    points: values.map((value, i) => ({
      version: VERSIONS[i] as string,
      at: atDaysAgo((values.length - i) * 4, 3),
      value,
      breached: breachedWhenAbove ? value > threshold : value < threshold,
    })),
  }
}

/** 最末点对齐今日实测值，避免趋势图和指标卡互相打脸 */
const fpTrend = [
  0.000092, 0.000118, 0.000074, 0.000061, 0.000048, 0.000106, 0.000057, 0.000043, 0.000038,
  aggregateFalsePositiveRate,
]

const detectionTrend = [
  0.921, 0.936, 0.948, 0.957, 0.962, 0.949, 0.958, 0.964, 0.961, aggregateDetectionRate,
]

const falsePositiveHistory = history('falsePositiveRate', FALSE_POSITIVE_THRESHOLD, true, fpTrend)
const detectionHistory = history('detectionRate', DETECTION_THRESHOLD, false, detectionTrend)

/* ---------------- 夜间基准 ---------------- */

const nightly: NightlyBenchmarkResult[] = [
  {
    night: nightLabel(6),
    passed: true,
    durationMs: 486_400,
    totalRuns: 118,
    failedRuns: 0,
    baselineVersion: 'v0.14.2',
    regressionCreated: false,
  },
  {
    night: nightLabel(5),
    passed: true,
    durationMs: 512_900,
    totalRuns: 121,
    failedRuns: 0,
    baselineVersion: 'v0.14.2',
    regressionCreated: false,
  },
  {
    night: nightLabel(4),
    passed: false,
    durationMs: 603_200,
    totalRuns: 124,
    failedRuns: 3,
    baselineVersion: 'v0.15.0',
    regressionCreated: true,
  },
  {
    night: nightLabel(3),
    passed: true,
    durationMs: 498_700,
    totalRuns: 119,
    failedRuns: 0,
    baselineVersion: 'v0.15.0',
    regressionCreated: false,
  },
  {
    night: nightLabel(2),
    passed: true,
    durationMs: 521_300,
    totalRuns: 126,
    failedRuns: 0,
    baselineVersion: 'v0.15.1',
    regressionCreated: false,
  },
  {
    night: nightLabel(1),
    passed: true,
    durationMs: 505_100,
    totalRuns: 122,
    failedRuns: 0,
    baselineVersion: 'v0.15.1',
    regressionCreated: false,
  },
  {
    night: nightLabel(0),
    passed: true,
    durationMs: 476_800,
    totalRuns: RUN_COUNT,
    failedRuns: 0,
    baselineVersion: 'v0.15.1',
    regressionCreated: false,
  },
]

/* ---------------- 模块健康 ---------------- */

const modules: ModuleHealth[] = [
  {
    module: 'core-engine',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { runs: RUN_COUNT, assertions: 8412, violations: 0 },
  },
  {
    module: 'simulator',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { players: SIMULATED_PLAYERS, sessions: 1268, ticks: 486_000 },
  },
  {
    module: 'cheats',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { specs: CHEAT_SPEC_COUNT, injected: 9120, detected: 8794 },
  },
  {
    module: 'network',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { profiles: 18, batches: 96, drops: 412 },
  },
  {
    module: 'report',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { artifacts: FINGERPRINT_COUNT, baselines: 7, archived: 1184 },
  },
  {
    module: 'adapters',
    version: 'v0.15.0',
    status: 'FAILED',
    counters: { platforms: 6, contracts: 42, broken: 1 },
  },
  {
    module: 'probes',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { probes: 24, samples: 96_400, gaps: 3 },
  },
]

/* ---------------- 门禁 ---------------- */

// 与 ThresholdGate 同口径：门禁判的是合计指标，不是单条运行。
const gateViolations: ConsoleData['gate']['violations'] = []

if (aggregateFalsePositiveRate > FALSE_POSITIVE_THRESHOLD) {
  gateViolations.push({
    metric: 'false_positive_rate',
    operator: '<=',
    actual: aggregateFalsePositiveRate,
    threshold: FALSE_POSITIVE_THRESHOLD,
    message: `检测类误报率 ${(aggregateFalsePositiveRate * 100).toFixed(4)}% 超过上限 ${(
      FALSE_POSITIVE_THRESHOLD * 100
    ).toFixed(4)}%`,
  })
}

if (totalCheat > 0 && aggregateDetectionRate < DETECTION_THRESHOLD) {
  gateViolations.push({
    metric: 'detection_rate',
    operator: '>=',
    actual: aggregateDetectionRate,
    threshold: DETECTION_THRESHOLD,
    message: `检测类检出率 ${(aggregateDetectionRate * 100).toFixed(2)}% 低于下限 ${(
      DETECTION_THRESHOLD * 100
    ).toFixed(2)}%`,
  })
}

if (totalFunctionalSmoke > 0 && functionalPassRate < 1) {
  gateViolations.push({
    metric: 'functional_smoke_pass_rate',
    operator: '>=',
    actual: functionalPassRate,
    threshold: 1,
    message: `功能通过率 ${(functionalPassRate * 100).toFixed(2)}% 低于门禁 100.00%（${totalFunctionalPassed}/${totalFunctionalSmoke} 通过）`,
  })
}

if (performanceBaselineDelta < 0) {
  gateViolations.push({
    metric: 'performance_baseline_delta',
    operator: '>=',
    actual: performanceBaselineDelta,
    threshold: 0,
    message: `性能相对上一版本基线劣化 ${Math.abs(performanceBaselineDelta * 100).toFixed(2)}%（TPS 相对变化）`,
  })
}

const gate: ConsoleData['gate'] = {
  passed: gateViolations.length === 0,
  failedMetrics: [...new Set(gateViolations.map((v) => v.metric))],
  violations: gateViolations,
}

/* ---------------- 插件配置 / 日志 / 直播 ---------------- */

const pluginConfig: ServerPluginConfig = {
  detectionEnabled: true,
  kickEnabled: false,
  banEnabled: false,
  alertThreshold: 60,
  exemptions: ['CREATIVE', 'SPECTATOR', 'ALLOW_FLIGHT', 'JOIN_GRACE'],
  logLevel: 'DEBUG',
}

const logs: LogEntry[] = [
  {
    at: startedAt(2),
    level: 'INFO',
    module: 'report',
    message: '规则运行时加载完成，覆盖 6 个平台矩阵',
    fields: { platforms: 6, source: 'prl/core.prl' },
  },
  {
    at: startedAt(4),
    level: 'DEBUG',
    module: 'simulator',
    message: '生成合法轨迹 1,268 条，平均 258 tick',
    fields: { sessions: 1268, ticks: 486_000 },
  },
  {
    at: startedAt(7),
    level: 'FLAG',
    module: 'detection',
    message: '命中攻击距离异常 simId=PTE-4471 reach=4.82',
    fields: { simId: 'PTE-4471', reach: 4.82, check: '攻击距离' },
  },
  {
    at: startedAt(9),
    level: 'INFO',
    module: 'cheats',
    message: '注入 KillAura 档位 DEFAULT，样本 128',
    fields: { type: 'KILL_AURA', tier: 'DEFAULT', samples: 128 },
  },
  {
    at: startedAt(13),
    level: 'WARN',
    module: 'gate',
    message: '误报率 0.0106% 逼近阈值 0.0100%',
    fields: { actual: 0.000106, threshold: 0.0001 },
  },
  {
    at: startedAt(16),
    level: 'ERROR',
    module: 'adapters',
    message: 'forge/1.19.2 契约校验失败：断言 tick 缺失',
    fields: { platform: 'forge/1.19.2', contract: 'PlatformAgnosticAssertionContract' },
  },
  {
    at: startedAt(21),
    level: 'INFO',
    module: 'report',
    message: '证据归档写入 EvidenceVault，artifacts=512',
    fields: { artifacts: 512, vault: 'evidence-2026-10' },
  },
  {
    at: startedAt(26),
    level: 'FLAG',
    module: 'detection',
    message: '命中瞬移相位 simId=PTE-2098 delta=6.4b',
    fields: { simId: 'PTE-2098', delta: 6.4, check: '瞬移相位' },
  },
  {
    at: startedAt(31),
    level: 'DEBUG',
    module: 'network',
    message: '网络条件 WIFI_200MS 批次 96 完成',
    fields: { condition: 'WIFI_200MS', batches: 96 },
  },
  {
    at: startedAt(38),
    level: 'INFO',
    module: 'core-engine',
    message: '断言引擎执行 8,412 条断言，违规 0',
    fields: { assertions: 8412, violations: 0 },
  },
  {
    at: startedAt(44),
    level: 'WARN',
    module: 'probes',
    message: '探针采样存在 3 处缺口，已标记待复核',
    fields: { gaps: 3, probes: 24 },
  },
  {
    at: startedAt(52),
    level: 'FLAG',
    module: 'detection',
    message: '命中自动点击 cps=19.4 超出上界 16',
    fields: { cps: 19.4, check: '自动点击' },
  },
  {
    at: startedAt(63),
    level: 'INFO',
    module: 'orchestrator',
    message: '编排批次 boundary-1 启动，目标 tgt-9f2c',
    fields: { batch: 'boundary-1', target: 'tgt-9f2c' },
  },
  {
    at: startedAt(71),
    level: 'DEBUG',
    module: 'core-engine',
    message: '阈值门禁预检：检出率 96.8% 通过',
    fields: { detectionRate: 0.968 },
  },
  {
    at: startedAt(84),
    level: 'WARN',
    module: 'adapters',
    message: 'PlatformFamily.NEOFORGE 加载器版本缺省，回退 21.1.9',
    fields: { family: 'NEOFORGE', fallback: '21.1.9' },
  },
  {
    at: startedAt(96),
    level: 'INFO',
    module: 'report',
    message: '规则运行时断言包重载完成，版本 v0.15.1',
    fields: { version: 'v0.15.1', packages: 12 },
  },
  {
    at: startedAt(112),
    level: 'FLAG',
    module: 'detection',
    message: '误报复核 simId=PTE-7710，标记为 falsePositive',
    fields: { simId: 'PTE-7710', falsePositive: true },
  },
  {
    at: startedAt(126),
    level: 'INFO',
    module: 'gate',
    message: 'GateResult 汇总：failedMetrics=2',
    fields: { failedMetrics: 2 },
  },
  {
    at: startedAt(140),
    level: 'ERROR',
    module: 'live-view',
    message: '会话 lv-3 信令中断，状态回落 CONNECTING',
    fields: { session: 'lv-3', signaling: 'ws://127.0.0.1:8788/rtc' },
  },
  {
    at: startedAt(158),
    level: 'DEBUG',
    module: 'simulator',
    message: '合法轨迹生成器预热完成，已缓存 24 个行为模型',
    fields: { models: 24 },
  },
  {
    at: startedAt(176),
    level: 'INFO',
    module: 'report',
    message: '归档产物 1,184 件，指纹 512 项入库',
    fields: { archived: 1184, fingerprints: FINGERPRINT_COUNT },
  },
  {
    at: startedAt(195),
    level: 'INFO',
    module: 'orchestrator',
    message: '夜间基准队列排空，等待下一次触发',
    fields: { queued: 0 },
  },
]

/* ---------------- 11.3 测试服实例健康 ---------------- */

/** 实例展示名对照：设计稿口径为 Paper 1.21 / Sprite 1.20 / Nukkit-MOT / Geyser 1.21 */
export const INSTANCE_LABELS: Record<string, string> = {
  'ins-1c07': 'Paper 1.21',
  'ins-4a91': 'Sprite 1.20',
  'ins-77bd': 'Nukkit-MOT',
  'ins-9f2c': 'Geyser 1.21',
}

// 四个实例对应四张状态卡；Geyser 的 TPS 已跌破 19.5，用于演示健康度差异。
const serverHealth: ServerHealth[] = [
  {
    instanceId: 'ins-1c07',
    platform: { id: 'paper/1.21.1' },
    tps: 20.0,
    mspt: 14.6,
    memoryUsedMb: 5210,
    memoryMaxMb: 8192,
    players: 0,
    simulatedPlayers: 24,
    pluginVersion: 'v0.15.1',
    status: 'RUNNING',
  },
  {
    instanceId: 'ins-4a91',
    platform: { id: 'fabric/1.20.1/0.15.7' },
    tps: 19.86,
    mspt: 31.2,
    memoryUsedMb: 6144,
    memoryMaxMb: 8192,
    players: 1,
    simulatedPlayers: 18,
    pluginVersion: 'v0.15.1',
    status: 'RUNNING',
  },
  {
    instanceId: 'ins-77bd',
    platform: { id: 'nukkit/1.21.0' },
    tps: 19.54,
    mspt: 42.8,
    memoryUsedMb: 3402,
    memoryMaxMb: 4096,
    players: 0,
    simulatedPlayers: 16,
    pluginVersion: 'v0.15.0',
    status: 'RUNNING',
  },
  {
    instanceId: 'ins-9f2c',
    platform: { id: 'geyser/1.21.1' },
    tps: 18.42,
    mspt: 58.4,
    memoryUsedMb: 3180,
    memoryMaxMb: 4096,
    players: 2,
    simulatedPlayers: 20,
    pluginVersion: 'v0.14.2',
    status: 'RUNNING',
  },
]

/* ---------------- 11.3 场景剧本进度 ---------------- */

const scenarioRuns: ScenarioRun[] = [
  {
    scenarioId: 'pvp',
    name: '竞技对枪 PvP',
    progressPercent: 100,
    injectedSamples: 128,
    status: 'PASSED',
    platform: { id: 'paper/1.21.1' },
  },
  {
    scenarioId: 'bridge-sprint',
    name: '搭桥冲刺',
    progressPercent: 76.4,
    injectedSamples: 96,
    status: 'RUNNING',
    platform: { id: 'fabric/1.20.1/0.15.7' },
  },
  {
    scenarioId: 'mining',
    name: '深层挖矿',
    progressPercent: 58.2,
    injectedSamples: 64,
    status: 'RUNNING',
    platform: { id: 'fabric/1.20.1/0.15.7' },
  },
  {
    scenarioId: 'reconnect',
    name: '断线重连',
    progressPercent: 32.8,
    injectedSamples: 48,
    status: 'RUNNING',
    platform: { id: 'nukkit/1.21.0' },
  },
  {
    scenarioId: 'respawn',
    name: '死亡重生',
    progressPercent: 0,
    injectedSamples: 0,
    status: 'QUEUED',
    platform: { id: 'geyser/1.21.1' },
  },
  {
    scenarioId: 'low-tps',
    name: '低 TPS 压测',
    progressPercent: 0,
    injectedSamples: 0,
    status: 'QUEUED',
    platform: { id: 'geyser/1.21.1' },
  },
]

/* ---------------- 4.3 网络模型预设 ---------------- */

const networkModels: NetworkModel[] = [
  {
    id: 'default',
    latencyMinMs: 10,
    latencyMaxMs: 350,
    jitterMs: 0,
    spikeMs: 0,
    packetLossRate: 0,
    reorderRate: 0,
    batchPattern: 'NONE',
    clientVersion: '1.21.11',
    seed: 0,
  },
  {
    id: 'wifi',
    latencyMinMs: 20,
    latencyMaxMs: 180,
    jitterMs: 15,
    spikeMs: 0,
    packetLossRate: 0.01,
    reorderRate: 0.005,
    batchPattern: 'WIFI',
    clientVersion: '1.21.11',
    seed: 20_818,
  },
  {
    id: 'lte',
    latencyMinMs: 40,
    latencyMaxMs: 350,
    jitterMs: 35,
    spikeMs: 120,
    packetLossRate: 0.03,
    reorderRate: 0.02,
    batchPattern: 'LTE',
    clientVersion: '1.21.11',
    seed: 32_420,
  },
]

/* ---------------- 11.8 一键查看：目标与画面墙 ---------------- */

// 目标覆盖四个实例：管理员旁观视角 + 玩家第一视角。
const liveViewTargets: LiveViewTarget[] = [
  {
    instanceId: 'ins-1c07',
    simId: 'ADMIN',
    networkCondition: 'local',
    platform: { id: 'paper/1.21.1' },
    adminSpectator: true,
  },
  {
    instanceId: 'ins-1c07',
    simId: 'PTE-4471',
    networkCondition: 'LAN',
    platform: { id: 'paper/1.21.1' },
    adminSpectator: false,
  },
  {
    instanceId: 'ins-4a91',
    simId: 'PTE-9014',
    networkCondition: 'WIFI_200MS',
    platform: { id: 'fabric/1.20.1/0.15.7' },
    adminSpectator: false,
  },
  {
    instanceId: 'ins-77bd',
    simId: 'PTE-2098',
    networkCondition: 'LTE_350MS',
    platform: { id: 'nukkit/1.21.0' },
    adminSpectator: false,
  },
  {
    instanceId: 'ins-9f2c',
    simId: 'PTE-6620',
    networkCondition: 'WIFI_200MS',
    platform: { id: 'geyser/1.21.1' },
    adminSpectator: false,
  },
  {
    instanceId: 'ins-9f2c',
    simId: 'PTE-7781',
    networkCondition: 'LAN',
    platform: { id: 'geyser/1.21.1' },
    adminSpectator: false,
  },
  {
    instanceId: 'ins-4a91',
    simId: 'PTE-3310',
    networkCondition: 'WIFI_200MS',
    platform: { id: 'fabric/1.20.1/0.15.7' },
    adminSpectator: false,
  },
  {
    instanceId: 'ins-77bd',
    simId: 'ADMIN',
    networkCondition: 'local',
    platform: { id: 'nukkit/1.21.0' },
    adminSpectator: true,
  },
]

const SIGNALING_CHANNEL = 'wss://127.0.0.1:8788/rtc'

// 9 路会话，保证画面墙 ≥8 路；LIVE 的首帧耗时多数落在 3s 预算内，lv-6 刻意越界。
const liveSessions: LiveViewSession[] = [
  {
    sessionId: 'lv-1',
    target: liveViewTargets[0],
    share: {
      sessionId: 'share-1c07-a',
      offerSdp: 'v=0\\r\\no=- 3918 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: 'v=0\\r\\no=- 7742 2 IN IP4 127.0.0.1\\r\\n',
      iceCandidates: ['host 192.168.0.24 51820 udp', 'srflx 203.0.113.7 43012 udp'],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'LIVE',
    },
    startedAt: startedAt(6),
    timeToFirstFrameMs: 1840,
  },
  {
    sessionId: 'lv-2',
    target: liveViewTargets[1],
    share: {
      sessionId: 'share-1c07-b',
      offerSdp: 'v=0\\r\\no=- 5521 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: 'v=0\\r\\no=- 9033 2 IN IP4 127.0.0.1\\r\\n',
      iceCandidates: ['host 192.168.0.24 51821 udp'],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'LIVE',
    },
    startedAt: startedAt(12),
    timeToFirstFrameMs: 2260,
  },
  {
    sessionId: 'lv-3',
    target: liveViewTargets[2],
    share: {
      sessionId: 'share-4a91-a',
      offerSdp: 'v=0\\r\\no=- 1180 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: 'v=0\\r\\no=- 4410 2 IN IP4 127.0.0.1\\r\\n',
      iceCandidates: ['host 192.168.0.24 51822 udp'],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'LIVE',
    },
    startedAt: startedAt(4),
    timeToFirstFrameMs: 2680,
  },
  {
    sessionId: 'lv-4',
    target: liveViewTargets[3],
    share: {
      sessionId: 'share-77bd-a',
      offerSdp: 'v=0\\r\\no=- 2277 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: '',
      iceCandidates: [],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'CONNECTING',
    },
    startedAt: startedAt(1),
    timeToFirstFrameMs: 0,
  },
  {
    sessionId: 'lv-5',
    target: liveViewTargets[4],
    share: {
      sessionId: 'share-9f2c-a',
      offerSdp: 'v=0\\r\\no=- 6630 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: 'v=0\\r\\no=- 8120 2 IN IP4 127.0.0.1\\r\\n',
      iceCandidates: ['host 192.168.0.24 51823 udp', 'relay 203.0.113.9 51200 udp'],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'LIVE',
    },
    startedAt: startedAt(9),
    timeToFirstFrameMs: 2940,
  },
  {
    sessionId: 'lv-6',
    target: liveViewTargets[5],
    share: {
      sessionId: 'share-9f2c-b',
      offerSdp: 'v=0\\r\\no=- 7711 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: 'v=0\\r\\no=- 9310 2 IN IP4 127.0.0.1\\r\\n',
      iceCandidates: ['host 192.168.0.24 51824 udp'],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'LIVE',
    },
    startedAt: startedAt(15),
    timeToFirstFrameMs: 3120,
  },
  {
    sessionId: 'lv-7',
    target: liveViewTargets[6],
    share: {
      sessionId: 'share-4a91-b',
      offerSdp: '',
      answerSdp: '',
      iceCandidates: [],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'IDLE',
    },
    startedAt: startedAt(21),
    timeToFirstFrameMs: 0,
  },
  {
    sessionId: 'lv-8',
    target: liveViewTargets[7],
    share: {
      sessionId: 'share-77bd-b',
      offerSdp: 'v=0\\r\\no=- 3320 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: 'v=0\\r\\no=- 6890 2 IN IP4 127.0.0.1\\r\\n',
      iceCandidates: ['host 192.168.0.24 51825 udp'],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'LIVE',
    },
    startedAt: startedAt(8),
    timeToFirstFrameMs: 1980,
  },
  {
    sessionId: 'lv-9',
    target: liveViewTargets[2],
    share: {
      sessionId: 'share-4a91-c',
      offerSdp: 'v=0\\r\\no=- 4477 2 IN IP4 127.0.0.1\\r\\n',
      answerSdp: 'v=0\\r\\no=- 5590 2 IN IP4 127.0.0.1\\r\\n',
      iceCandidates: ['host 192.168.0.24 51826 udp'],
      signalingChannel: SIGNALING_CHANNEL,
      status: 'CLOSED',
    },
    startedAt: startedAt(34),
    timeToFirstFrameMs: 2600,
  },
]

/* ---------------- 11.8 / 12.5 性能悬浮窗 ---------------- */

// 与 serverHealth 同口径：TPS / MSPT 直接对齐实例卡，避免两处读数互相打脸。
const perfOverlays: PerfOverlay[] = [
  { instanceId: 'ins-1c07', tps: 20.0, mspt: 14.6, latencyMs: 22, kneeAlert: false, recordingTriggered: false },
  { instanceId: 'ins-4a91', tps: 19.86, mspt: 31.2, latencyMs: 68, kneeAlert: false, recordingTriggered: false },
  { instanceId: 'ins-77bd', tps: 19.54, mspt: 42.8, latencyMs: 96, kneeAlert: false, recordingTriggered: false },
  { instanceId: 'ins-9f2c', tps: 18.42, mspt: 58.4, latencyMs: 142, kneeAlert: true, recordingTriggered: true },
]

/* ---------------- 11.4 查端 / 名单 / 红屏 ---------------- */

const inspectSessions: InspectSession[] = [
  {
    sessionId: 'ins-sess-01',
    playerId: 'PTE-9014',
    simId: 'PTE-9014',
    relatedFlags: ['攻击距离', '自动点击'],
    status: 'PENDING_INSPECT',
    evidenceRefs: ['ev-9014-reach', 'ev-9014-clicks'],
    openedAt: startedAt(3),
  },
  {
    sessionId: 'ins-sess-02',
    playerId: 'PTE-2098',
    simId: 'PTE-2098',
    relatedFlags: ['瞬移相位'],
    status: 'CONFIRMED',
    evidenceRefs: ['ev-2098-phase'],
    openedAt: startedAt(14),
  },
  {
    sessionId: 'ins-sess-03',
    playerId: 'PTE-7710',
    simId: 'PTE-7710',
    relatedFlags: ['移动速度'],
    status: 'FALSE_POSITIVE',
    evidenceRefs: ['ev-7710-speed'],
    openedAt: startedAt(96),
  },
]

// 当前在场采样：合法 7 / 作弊 4 / 管理员 2，是 32,480 模拟池的一个样本。
const simPlayers: SimPlayer[] = [
  { name: 'Sable_Quill', simId: 'PTE-4471', type: 'LEGIT', behavior: 'WALK', cheatSpecId: null },
  { name: 'Mirlo', simId: 'PTE-7710', type: 'LEGIT', behavior: 'RUN', cheatSpecId: null },
  { name: 'AnvilCrest', simId: 'PTE-6620', type: 'LEGIT', behavior: 'SPRINT', cheatSpecId: null },
  { name: 'NettlePike', simId: 'PTE-9012', type: 'LEGIT', behavior: 'JUMP', cheatSpecId: null },
  { name: 'Bramble', simId: 'PTE-7781', type: 'LEGIT', behavior: 'SNEAK', cheatSpecId: null },
  { name: 'Kestrel', simId: 'PTE-3310', type: 'LEGIT', behavior: 'MINE', cheatSpecId: null },
  { name: 'Dovetail', simId: 'PTE-5540', type: 'LEGIT', behavior: 'PLACE', cheatSpecId: null },
  { name: 'Vexon', simId: 'PTE-9014', type: 'CHEAT', behavior: null, cheatSpecId: 'cs-reach-default' },
  { name: 'Hexbolt', simId: 'PTE-2098', type: 'CHEAT', behavior: null, cheatSpecId: 'cs-phase-default' },
  { name: 'Zenith', simId: 'PTE-6102', type: 'CHEAT', behavior: null, cheatSpecId: 'cs-killaura-max' },
  { name: 'Nullpoint', simId: 'PTE-8820', type: 'CHEAT', behavior: null, cheatSpecId: 'cs-speed-min' },
  { name: 'Overseer', simId: 'ADMIN', type: 'ADMIN', behavior: null, cheatSpecId: null },
  { name: 'Warden', simId: 'ADMIN-2', type: 'ADMIN', behavior: null, cheatSpecId: null },
]

const redScreen: RedScreenBroadcast = {
  playerId: 'PTE-9014',
  playerName: 'Vexon',
  type: 'REACH',
  riskScore: 88.5,
  actionLabel: '确认查端',
}

/* ---------------- 11.4 /ls 子命令 ---------------- */

const orchestratorCommands: OrchestratorCommand[] = [
  { name: 'inspect', args: ['<SimID>'], receipt: '查端会话已开启，证据链已挂载' },
  { name: 'teleport', args: ['<SimID>', '<scene>'], receipt: '已传送到目标场景坐标' },
  { name: 'scenario', args: ['<start|stop>', '<id>'], receipt: '场景剧本状态已更新' },
  { name: 'result', args: ['<sessionId>'], receipt: '返回该会话的判定与证据引用' },
]

/* ---------------- 11.5 受测客户端 ---------------- */

const clientSettings: ClientSettings = {
  behavior: 'SPRINT',
  clickDistribution: 'uniform-1-12',
  targetSwitch: false,
  preInject: false,
  autoRecord: true,
  networkModelRef: 'default',
}

const injectorStatus: InjectorStatus = {
  cheatInjectionEnabled: false,
  probeStubActive: true,
  probeSnapshotRef: 'probe-snap-0091',
  reportedSeq: 18_422,
  evidenceRecording: true,
}

// 干净基线：只列出常驻进程，其余维度为空。
const probeSnapshot: ProbeSnapshot = {
  processes: ['javaw.exe', 'minecraft.exe', 'lsass.exe'],
  modules: ['laplace-probe.dll'],
  memoryPatterns: [],
  usbDevices: [],
  registryHits: [],
  networkHits: [],
  signatures: ['laplace-probe.dll:microsoft'],
  drivers: [],
  injections: {},
}

/* ---------------- 11.6 整合包模块与输出日志 ---------------- */

// 7 个整合包模块；计数沿用全局口径（规则 / 事件 / 证据）。
const modpackModules: ModuleHealth[] = [
  {
    module: '检测引擎核心',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { 规则数: 8, 事件数: totalCheat },
  },
  {
    module: '规则运行时',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { 规则数: CHEAT_SPEC_COUNT, 事件数: 8412 },
  },
  {
    module: 'Addon Scanner',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { 规则数: 42, 事件数: totalLegit },
  },
  {
    module: 'ProbeStub',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { 事件数: 96_400, 证据数: 3 },
  },
  {
    module: 'EvidenceVault',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { 证据数: 1184 },
  },
  {
    module: '跨平台更新器',
    version: 'v0.15.0',
    status: 'RUNNING',
    counters: { 事件数: 6 },
  },
  {
    module: '启动器',
    version: 'v0.15.1',
    status: 'PASSED',
    counters: { 事件数: 1268, 证据数: FINGERPRINT_COUNT },
  },
]

const modpackLogs: LogEntry[] = [
  {
    at: startedAt(1),
    level: 'INFO',
    module: '规则运行时',
    message: '规则运行时加载完成，规则包重载 12 个',
    fields: { packages: 12, version: 'v0.15.1' },
  },
  {
    at: startedAt(3),
    level: 'DEBUG',
    module: '检测引擎核心',
    message: '断言引擎执行 8,412 条断言，违规 0',
    fields: { assertions: 8412, violations: 0 },
  },
  {
    at: startedAt(5),
    level: 'FLAG',
    module: '检测引擎核心',
    message: '命中攻击距离异常 simId=PTE-9014 reach=4.82',
    fields: { simId: 'PTE-9014', reach: 4.82, check: '攻击距离' },
  },
  {
    at: startedAt(8),
    level: 'INFO',
    module: 'EvidenceVault',
    message: '归档证据 1,184 件，指纹 512 项入库',
    fields: { archived: 1184, fingerprints: FINGERPRINT_COUNT },
  },
  {
    at: startedAt(11),
    level: 'FLAG',
    module: '检测引擎核心',
    message: '命中瞬移相位 simId=PTE-2098 delta=6.4b',
    fields: { simId: 'PTE-2098', delta: 6.4, check: '瞬移相位' },
  },
  {
    at: startedAt(14),
    level: 'WARN',
    module: 'Addon Scanner',
    message: '扫描到未登记模组 jei-15.2.0.jar，已降级为观察项',
    fields: { addon: 'jei-15.2.0.jar', action: 'observe' },
  },
  {
    at: startedAt(18),
    level: 'INFO',
    module: '启动器',
    message: '整合包实例启动 1,268 次，平均冷启 4.2s',
    fields: { launches: 1268, coldStartMs: 4200 },
  },
  {
    at: startedAt(23),
    level: 'FLAG',
    module: '检测引擎核心',
    message: '误报复核 simId=PTE-7710，标记为 falsePositive',
    fields: { simId: 'PTE-7710', falsePositive: true },
  },
  {
    at: startedAt(29),
    level: 'DEBUG',
    module: 'ProbeStub',
    message: '探针采样 96,400 次，缺口 3 处已标记',
    fields: { samples: 96_400, gaps: 3 },
  },
  {
    at: startedAt(36),
    level: 'ERROR',
    module: '跨平台更新器',
    message: 'forge/1.19.2 通道校验失败，回退镜像源',
    fields: { platform: 'forge/1.19.2', action: 'fallback' },
  },
  {
    at: startedAt(44),
    level: 'INFO',
    module: 'Addon Scanner',
    message: '扫描模组清单完毕，契约 42 项全部匹配',
    fields: { contracts: 42, broken: 0 },
  },
  {
    at: startedAt(52),
    level: 'DEBUG',
    module: 'EvidenceVault',
    message: '证据索引压缩完成，占用 218 MB',
    fields: { sizeMb: 218, retention: '30d' },
  },
]

/* ---------------- 13.6 全类型目标矩阵 ---------------- */

const targetTypes: TargetTypeRow[] = [
  {
    type: 'CONTENT',
    label: '内容型模组',
    testFocus: ['注册表条目可加载且无 ID 冲突', '物品 / 方块 / 生物属性与交互', '维度传送与存档兼容'],
    frameworks: ['Fabric GameTest', '原版 /test 命令', 'spark'],
    risks: ['注册表 ID 冲突或跨版本漂移', 'NBT 结构变更导致旧存档读不出', '生物 AI 造成 MSPT 尖峰'],
    projects: ['Cobblemon', 'Create'],
  },
  {
    type: 'TECH_AUTOMATION',
    label: '科技·自动化',
    testFocus: ['方块实体 tick 与能量网络', '大规模机器阵列的 TPS / MSPT', '配方同步与区块卸载回收'],
    frameworks: ['spark', 'JFR', 'GameTest'],
    risks: ['方块实体或区块卸载不回收导致泄漏', '配方 ID 冲突覆盖其他模组', '高频 tick 把 MSPT 推过 50ms'],
    projects: ['Create', 'Mekanism'],
  },
  {
    type: 'OPTIMIZATION_RENDER',
    label: '优化·渲染',
    testFocus: ['替换渲染管线后行为等价', '帧率 / 帧时间相对基线不劣化', '光影与资源包组合可加载'],
    frameworks: ['spark', 'JFR', 'Sodium 调试 HUD'],
    risks: ['mixin 注入点与其他优化模组冲突', '渲染线程与逻辑线程竞争', '驱动差异导致画面错误但无异常'],
    projects: ['Sodium', 'Lithium'],
  },
  {
    type: 'CLIENT_QOL',
    label: '客户端增强·QoL',
    testFocus: ['UI、按键绑定与配置持久化', '服务端模组与原版协议兼容', '分辨率与无障碍适配'],
    frameworks: ['客户端 GameTest', 'Loom runClient', 'Mod Menu / Cloth Config'],
    risks: ['客户端崩溃或黑屏', '客户端与服务端版本不匹配', '配置迁移丢失'],
    projects: ['JEI', 'Jade', "Xaero's Minimap"],
  },
  {
    type: 'SERVER_PLUGIN',
    label: '服务端专用',
    testFocus: ['命令、权限与事件监听语义', '插件启停、重载与多实例部署', '并发玩家下状态不丢失'],
    frameworks: ['MockBukkit', 'WatchWolf', 'Plugwright', 'spark'],
    risks: ['主线程阻塞或同步 IO', '事件优先级与取消语义错误', '监听器未注销造成内存泄漏'],
    projects: ['MockBukkit', 'WatchWolf', 'Plugwright'],
  },
  {
    type: 'CROSS_LOADER_LIBRARY',
    label: '跨加载器库',
    testFocus: ['同一 API 在多加载器上行为一致', '版本范围与传递依赖解析', '下游模组装配后可启动'],
    frameworks: ['Architectury API', '多加载器构建矩阵', '各加载器 GameTest'],
    risks: ['映射差异导致平台分支漂移', '加载器 API 变更', '依赖被降级或重复打包'],
    projects: ['Architectury API', 'Cloth Config', 'TerraBlender'],
  },
  {
    type: 'RESOURCE_PACK_SHADER',
    label: '资源包与光影',
    testFocus: ['pack_format 与版本匹配', '纹理、模型、动画路径可解析', '加载失败回退原版资源'],
    frameworks: ['Blockbench', '原版资源包日志', 'Iris + Sodium 矩阵'],
    risks: ['pack_format 不匹配被静默忽略', '纹理图集溢出或内存暴涨', '光影与渲染模组版本冲突'],
    projects: ['Fresh Animations', 'Complementary Shaders', 'Blockbench'],
  },
  {
    type: 'DATAPACK',
    label: '数据包',
    testFocus: ['pack_format 与 data 命名空间正确', '函数、进度、战利品表可加载', '/reload 幂等且不重复注册'],
    frameworks: ['PackTest', '原版 /test', 'pack format 对照表'],
    risks: ['pack_format 跨版本失效', '函数每 tick 执行造成卡顿', '标签覆盖导致其他数据包失效'],
    projects: ['Dungeons and Taverns', 'Terralith', 'PackTest'],
  },
  {
    type: 'MODPACK',
    label: '整合包',
    testFocus: ['依赖解析与版本锁定可复现', '启动时间、内存与包体大小', '客户端 / 服务端清单一致'],
    frameworks: ['packwiz', 'Prism / MultiMC', 'spark'],
    risks: ['传递依赖冲突或重复模组', '客户端专用模组混进服务端', '版本漂移使问题无法复现'],
    projects: ['Fabulously Optimized', 'packwiz'],
  },
  {
    type: 'BEDROCK_ADDON',
    label: '基岩版 Addon',
    testFocus: ['manifest UUID、版本与 min_engine_version', '行为包 / 资源包配对', '实体、方块、物品组件 schema'],
    frameworks: ['bridge. v2', 'Blockbench', 'Content Log'],
    risks: ['manifest 版本不匹配导致包不加载', '组件 schema 跨版本变更', '行为包与资源包 UUID 冲突'],
    projects: ['bridge.', 'Blockbench', 'Geyser'],
  },
  {
    type: 'NETWORK_MULTIPLAYER',
    label: '网络·多人',
    testFocus: ['协议版本、握手与登录流程', '代理转发（Velocity / BungeeCord）', '延迟、丢包与重排下状态一致'],
    frameworks: ['NetworkModel 网络条件注入', 'Geyser / Floodgate', 'Simple Voice Chat'],
    risks: ['协议版本不兼容', '代理转发丢失玩家信息', 'UDP 端口或防火墙未放行'],
    projects: ['Simple Voice Chat', 'Geyser', 'Floodgate'],
  },
  {
    type: 'WORLDGEN',
    label: '世界生成类',
    testFocus: ['生物群系 / 结构注册与 seed 可复现', '区块生成耗时分位', '多模组 biome 与结构冲突'],
    frameworks: ['spark', 'TerraBlender', 'Chunky'],
    risks: ['世界生成死循环或卡区块', 'biome / 结构覆盖冲突', '预生成时内存暴涨'],
    projects: ['Terralith', "Biomes O' Plenty", 'TerraBlender'],
  },
]

/* ---------------- 汇总 ---------------- */

export const consoleData: ConsoleData = {
  cards: {
    todayRuns: RUN_COUNT,
    simulatedPlayers: totalLegit,
    sampleSpecCount: SAMPLE_SPEC_COUNT,
    functionalPassRate,
    performanceBaselineOk,
    performanceBaselineDelta,
    gatePassed: passedRuns,
    gateTotal: RUN_COUNT,
    cheatSpecCount: CHEAT_SPEC_COUNT,
    falsePositiveRate: aggregateFalsePositiveRate,
    detectionRate: aggregateDetectionRate,
  },
  runs,
  sampleTypeBreakdown,
  targetTypes,
  detectionByCheckTier: CHECK_TIER_RATE,
  detectionByTier,
  falsePositiveHistory,
  detectionHistory,
  nightly,
  gate,
  modules,
  pluginConfig,
  cheatSpecCount: CHEAT_SPEC_COUNT,
  fingerprintCount: FINGERPRINT_COUNT,
  liveView: { sessions: liveSessions },
  logs,
  serverHealth,
  scenarioRuns,
  networkModels,
  liveViewTargets,
  perfOverlays,
  inspectSessions,
  simPlayers,
  redScreen,
  clientSettings,
  injectorStatus,
  probeSnapshot,
  modpackModules,
  modpackLogs,
  orchestratorCommands,
}

export const DETECTION_TIERS = TIERS
