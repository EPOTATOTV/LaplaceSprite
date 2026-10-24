# 变更日志

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本。

## [Unreleased] - L3 P0（L3-01 ~ L3-05）

### 新增

- **五层插件化内核**：`KernelEventBus` 提供数据包 / 实体 / 渲染 / 测试生命周期钩子；`BusinessPlugin` + `PluginRegistry` 按依赖激活，`PluginRuntime` 支持 `laplace.plugins.exclude` 故障注入；第三方适配器经 `AdapterProvider` ServiceLoader 注入；新增 `laplace plugin list`。
- **全版本游戏管理器**：新增 `game` 模块，解析 Mojang 版本清单与版本 JSON，记录 PackFormat / 渲染 API / 映射 / 协议；SHA 校验下载带重试与原子替换；版本缓存与多实例隔离；新增 `laplace game list/download/metadata/cache-clean`。
- **多语言内核**：`I18n` 支持中英文资源、插件语言包与英文回退；CLI 增加 `--lang` 与 `laplace i18n list`；游戏、插件、PACC 与 Webhook 告警接入；控制台增加语言切换。
- **沙箱与证据链增强**：`SandboxPathGuard` 限制实例读写与敏感路径；`ArtifactScanner` 只读扫描 jar 逃逸、体积、可执行头与可疑特征；`EvidenceAuditTrail` 把下载 / 实例 / 测试 / 资产 / 沙箱事件写入同一条 HMAC 链；`AssetTrustRegistry` 提供资产可信度；新增 `laplace audit export/verify` 与 `sandbox --scan-artifact`。
- **PACC 插件化门控**：`TestSuiteProvider` 契约让 PACC 插件声明 `cheat/mixed/boundary` 套件；排除 PACC 后这些套件被拒绝，`legit` 与其余命令不受影响。

## [1.5.0] - 2026-10-23

下一步升级实施 v2（L2-01 ~ L2-10）。

### 新增

- **26.3 / 26.4 版本矩阵**：Fabric / Quilt / NeoForge / Forge 快照与离线生态快照加入 26.3 稳定线与 26.4 快照；`PackFormatTable` 支持 87 / 88；渲染矩阵默认版本扩到 26.4 / 26.3 / 26.2 / 26.1 / 1.21.1；CI 适配器矩阵同步。
- **Vulkan 管线分档**：`RenderPipeline` 区分 OpenGL 与 Vulkan，VulkanMod 在 26.4 起进入像素 diff，容差 0.8%，OpenGL 保持 0.5%；金标准库新增 26.4 OIT 深度 bin 与地形用例、26.3 基线。
- **分布式压测韧性**：下发前做节点健康检查，掉线节点的并发重新分摊给存活节点；节点执行异常按次数重试，仍失败则降级跳过并标记 degraded，聚合结论区分性能破线与基础设施故障。
- **2026 Q4 检测目标样本**：新增 TruthfulAC、Boar、GrimAC 2.3.74 对应的 8 条绕过样本，样本库达到 62 条；绕过说明标注来源与待复核项。
- **GameTest 统一报告**：`GameTestLoader` 明确 26.x 沿用 1.21.5+ 的 `/test runall` 入口，`laplace gametest --mc` 校验版本；`gametest` 与 `datapack` 通过 `TestTrackReport` 输出同一份 JSON 报告。
- **PACC 回归联动**：`PaccRegressionSync` 把 PACC 误报按轨迹去重写入回归库，`laplace pacc --sync-regression` 与 nightly 缓存一起把误报用例纳入回归健康度。
- **样本市场治理**：样本包新增签名的来源可信度与来源引用；市场支持吊销、搜索排序与安装前沙箱校验，被吊销版本不可安装或回滚。
- **评测门户趋势**：`/benchmark` 增加历史综合分折线与基线漂移标注，四项目各有四个历史点。
- **矩阵健康度告警**：`MatrixAlertNotifier` 覆盖健康度下降、版本过期、样本过期，`laplace test --alert-webhook` 发送 Webhook；适配器矩阵 CI 持久化每格健康度。
- **混合端扩充**：新增 Mohist 适配器（Bukkit × Forge，1.21.1–26.4），适配器总数 25。

### 变更

- 版本号升至 1.5.0。
- `laplace datapack --versions` 默认清单加入 26.3 / 26.4。

## [1.4.0] - 2026-10-18

下一步升级实施（L-01 ~ L-10）。

### 新增

- **26.x 版本矩阵**：四个加载器快照加入 26.1 / 26.2，`PackFormatTable` 支持 26.2 的 86 与 26.1 的 85，渲染矩阵默认版本切到 26.2 / 26.1 / 1.21.1，CI 冒烟清单同步。
- **GameTest 生态全覆盖**：`GameTestLoader` 登记 Fabric / Quilt / Forge / NeoForge 四个入口；`GameTestRunner` 真正调用项目 Gradle 任务并按日志判定，`laplace gametest` 可直接执行；`PackTestConvention` 扫描并静态校验 `data/<ns>/test/*.mcfunction`；样本类别新增 `DATAPACK` 与对应注入器。
- **回归库健康度**：`RegressionHealth` 计算用例规模、绕过报告转化率、误报复现覆盖率与超期报告；`laplace run` 写入回归台账（用例 / 误报 flag / 绕过报告 / health.md），`gate` 自动读取台账并支持 `--require-regression-health`。
- **26.x 压测剧本**：新增 `ENTITY_LOAD` / `CHUNK_LOAD` 动作，Carpet 轨下发真实的 `/summon` 与 `/forceload` 命令；实体、区块负载剧本随模块内置。
- **矩阵健康度写回**：`laplace test --health` 把每格冒烟结果写入 `MatrixHealthRegistry`，每周门禁归档健康度文件。
- **26.2 渲染金标准库**：`RenderGoldenLibrary` 覆盖硫磺洞穴、新维度、新效果；`RenderSuiteRunner` 批量比对 golden / actual 目录并输出 Markdown，26.x 容差 0.5%。
- **分布式压测**：`DistributedLoadPlan` 把峰值并发拆到 SSH / Docker 节点，`DistributedLoadOrchestrator` 下发命令并聚合判定；`laplace loadtest --nodes` 输出计划，`--execute` 实际执行。
- **样本市场**：`SamplePackage` + HMAC 签名、`SampleMarket` 安装 / 回滚 / 历史；`laplace market` 支持安装、回滚、校验并写入证据链；`laplace run --market` 把已安装样本并入 `CheatCatalog`。
- **评测报告门户**：控制台新增多项目基准排行与指标横评页（`/benchmark`），数据走 `ConsoleData.benchmarkProjects` 契约而不是页面内常量。
- **PACC 夜间基准**：`DetectionTrend` 产出误报 / 检出趋势 SVG 与 Markdown；`TrendHistory` 持久化历史并判定回归，`laplace pacc --history --fail-on-regression` 支撑 nightly 累积与回归拦截。

### 变更

- 版本号升至 1.4.0；`daily` / `weekly` 冒烟版本清单加入 26.2。
- 生态版本快照加入 26.2 的 Fabric / NeoForge / Forge / Paper 与 Sodium / Lithium / Iris / JEI 等构建。

## [1.3.0] - 2026-10-17

全类型模组测试通用化修订。

### 新增

- **五条工具链轨**：数据包 / 资源包（`datapack`）、假玩家压测（`loadtest`）、世界快照回放（`replay`）、Mixin 生态适配（`mixin`）、客户端渲染级测试（`render`）。
- **全类型目标矩阵**：`TargetTypeMatrix` 覆盖 12 类被测目标，每类含测试重点、框架工具、典型风险、代表项目与来源。
- **社区生态兼容矩阵**：基线四件套（JEI × Sodium × Lithium × Iris）、5 档性能渲染矩阵、内容型共存、热门插件四件套、重型插件三件套、OptiFine 闭源探针、矩阵生成器与矩阵健康度。
- **三级门禁收敛**：PR 子集 / 每日中间集 / 每周全量，`ci/generate-matrix.py` 输出矩阵片段。
- **功能类指标**：功能冒烟通过率、性能基线（相对 delta）、资源与装配。
- **性能指标**：连接数 / 带宽 / 区块加载 / 实体数 / 延迟 P50·P95 与 `LatencyGate`。
- **事件流回放**：按 flag 绑定完整事件流，支持区间重放、断点切段、单步与导出。
- **崩溃分类**：新增「资源包 / 数据包加载失败」类，按 MC-123460 / MC-123459 / MC-310240 路由并提取 JSON 路径。

### 变更

- **通用化重命名**：`LegitSimulator` → `BehaviorSimulator`、`CheatMatrix` → `AnomalyMatrix`、`CheatSpec` → `TestSpec`、`CheatTier` → `SampleTier`、`LegitTrajectory` → `BehaviorTrajectory`、`SimAdapter` → `SimServerAdapter`。
- **样本矩阵泛化**：新增 `SampleCategory`（content / tech / perf / network / anomaly / cheat），样本库扩到 52 条。
- 检出率三视图改为按目标 / 按样本类型 / 按档位，并新增未覆盖项清单。
- 控制台六张指标卡改为功能类优先，条形图改为样本类型 × 档位。

## [1.1.0] - 2026-10-16

独立化修订。

### 新增

- 误报豁免规则（创造 / 旁观 / 允许飞行 / 开局缓冲 / 插件传送与击退），可配置且进证据链。
- 绕过清单与未覆盖项清单。
- 证据链 HMAC 链式哈希与 `--verify-chain`。
- 证据可上云（本地镜像 + S3 兼容）。
- 五步装配流水线（resolve / layout / install / launch / verify）。
- GameTest 双向桥。
- 「第一梯队 + 受影响族」受影响族选择与矩阵计划。
- Sponge 系与混合端适配器族。
- 第七至十二条工具链轨中的前七条（调试 / 热重载 / 剖析 / 沙箱 / 崩溃诊断 / 协议兼容 / CI 客户端）。
- 远端编排：SSH 与 Docker Engine API 驱动。

### 变更

- 与任何反作弊项目解耦：`PTEID` → `SimID`、`pacc-server-core` → `detection-engine`、`PRL` → `规则运行时`。
- 探针快照增加能力位，区分「没检出」与「查不了」。

## [1.0.0] - 2026-10-16

首个可用版本。

### 新增

- 多模块 Java 21 平台：core / simulator / cheats / network / adapters / probes / servers / report / cli。
- 物理预测器驱动的行为基线、异常样本矩阵、网络模型。
- 17 个平台与加载器适配器（Bukkit 系 / 代理端 / 基岩系 / 模组加载器）。
- TestServer 编排、Live View 信令、性能采集。
- 报告三格式、证据库、回归用例库、控制台数据契约。
- React 控制台七块屏。
- CI：PR 门禁、适配器矩阵、夜间基准。
