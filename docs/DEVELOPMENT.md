# 开发

## 模块与依赖方向

```
core ← simulator, cheats, network, probes, report
network, probes ← adapters
adapters ← servers
servers ← sandbox, debug, hotreload, profile
protocol ← adapters, servers
crashdiag ← core
全部 ← cli
```

`core` 不依赖任何其它模块；平台核心零外部检测引擎依赖。对具体检测目标的依赖限定在 `adapters/` 内且可插拔。适配器与平台差异全部收在 `adapters`，内核、场景剧本与断言不感知具体平台。

## 关键契约

- **事件流**（`core.event.EventStream`）：唯一归一化出口。所有平台事件经 `EventBridge` 收敛成 `core.event.Event` 的 22 种记录（`PlayerJoin` / `Move` / `Combat` / `Packet` / `RegionTick` / `GameTestSuite` …），事件只带玩家、Tick、时间戳与平台标签，平台特有上下文放进 `context()`。
- **适配器 SPI**（`core.spi`）：`ArtifactResolver` / `InstallLayout` / `LifecycleHook` / `EventBridge` / `AssertionContract`。注册中心 `AdapterRegistry` 按「平台 × Minecraft 版本 × 加载器版本」匹配，不中时走兼容链回退并输出告警。
- **检测目标**（`core.engine.DetectionTarget`）：Runner 只面向这个接口。进程内轨是 `SimAdapter`（内部再面向 `adapters.sim.DetectionEngine`），真实环境轨由平台适配器端到端实现。接入真实检测引擎 = 实现 `DetectionEngine` 后替换，Runner 与场景剧本不用改。
- **判定口径**（`ThresholdGate` / `Thresholds` / `MetricSet`）冻结，不为了跑绿而放宽。
- **证据链**（`report.EvidenceChainLink`）：`mac = HMAC(key, index | timestamp | payloadHash | prevMac)`。改字段顺序会破坏既有 vault 的可校验性。远端同步只写 `remote-index.json`，绝不改写 `index.json` / `chain.json`——链必须能在同步后继续验通。
- **报告分节**（`report.ReportSection`）：任何轨道的结构化结果都可以作为报告一节嵌入，`report` 不需要反向依赖那些模块。
- **误报豁免**（`core.engine.ExemptionPolicy`）：判定发生在 `Runner` 内、flag 分类之前。被豁免的触发以 `FlagEvent.exempted(...)` 记录（`falsePositive=false`、`exemption` 非空），因此既不进 `MetricSet.falsePositives`，也不进 `TestRun.detections()`，但仍留在 `flags()` 里可审计。新增豁免维度时改 `ExemptionRule` + `SessionContext`，不要让检测引擎去判断豁免。
- **样本级口径**（`cheats.report`）：检出率与绕过清单的分子一律按 `playerId` 去重（一条样本可能产生多条 flag），分母只取 `DetectionBreakdown.samplesByTier()` 里实际注入过的档位。改动这两个口径会直接影响报告能不能读。
- **装配流水线**（`adapters.pipeline.InstallPipeline`）：五阶段严格顺序，任一阶段失败即停止且不产生后续阶段。`verify` 的端口探测走 `PortProbe` 接缝——进程内轨没有监听端口，会如实失败，不要为了跑绿而返回 true。
- **GameTest 双向桥**（`adapters.gametest.GameTestBridge`）：原生结果构造时强校验「退出码 = 失败用例数」。往返保真字段只有「用例名 / 期望 / 通过与否」，栈帧与耗时是有损的，别声称完美互转。
- **受影响族**（`adapters.matrix.AffectedFamilySelector`）：路径 → 家族的映射必须保守；拿不准就返回全家族并置 `fullMatrixRequired`，不要猜窄集合。

## 新增平台接入（六步，14.8）

1. 在 `adapters/` 下新增适配器类，继承 `AbstractLaplaceAdapter`，声明 `family()` / `supportRange()`，实现 `artifactResolver()` / `installLayout()` / `lifecycleHook()` / `eventBridge()`。
2. 在 `AdapterRegistryFactory.allAdapters()` 注册。
3. 补三个冒烟剧本（装配成功 / 核心事件归一 / 生命周期全链路）。
4. 本机用 `laplace test --platform <平台>` 快速迭代。
5. 在真实环境轨（`docker/docker-compose.testservers.yml`）完成装配与端到端验证。
6. 把矩阵项加进 `.github/workflows/adapter-matrix.yml`。

版式描述文件解析走 `descriptor.DescriptorParserRegistry`，已有 10 个解析器（`plugin.yml`、`paper-plugin.yml`、`bungee.yml`、`velocity-plugin.json`、`plugin.json`、`fabric.mod.json`、`quilt.mod.json`、`mods.toml`、`neoforge.mods.toml`、`sponge_plugins.json`）。新平台的描述文件不一样就加一个解析器。

家族级约定：

- **Sponge 系**：装配在 `mods/`，识别顺序必须是「先查 `sponge_plugins.json`，再回退 Forge / NeoForge 元数据」；事件钩子独立实现，**不能复用 Bukkit 或 Forge 的扫描逻辑**。
- **混合端**：组合适配层只做三件事——双目录合并（`plugins/` + `mods/`）、双事件总线监听并去重、跨生态 ID 映射校验。事件双发、类阴影、命令树冲突属于已知坑，用矩阵覆盖，不假装解决。
- **遗留加载器**：Rift 与 LiteLoader 不支持，只在 `LegacyLoaderAppendix` 里记录，不进注册表。

## 加一个行为模型

实现 `simulator.BehaviorModel`，或继承 `MovementModel` 只覆盖意图钩子（`speed()` / `sprinting()` / `sneaking()` / `motionMode()` / `jumpImpulse()` / `decorate()`）。**位移不再来自手调常量**：`MovementModel.generate` 把意图交给 `PhysicsPredictor` 按原版运动规则逐 tick 预测，再叠加 `SubToleranceNoise`。新增模型必须落在物理包络内，且噪声预算不得超预算常量——超了就会把误报率打上去。

`BehaviorModelRegistryTest#registryCoversEveryBehaviorProfile` 会强制覆盖完整。

## 加一个检测项

在 `adapters/sim/HeuristicDetectionEngine` 加 check 常量与判定分支，阈值用命名常量并写清物理依据（vanilla 关系或推导）。只读事件流内容判定，禁止读取 `DetectionContext.cheatSpec()` / `tier()` 来做决定——那两个字段只用于 flag 标注。

加完检测项要同步做两件事：

1. `cheats` 侧 `DetectionThresholdAnchor` 补锚点，`adapters` 的 `DetectionThresholdAnchorCrossCheckTest` 会校验锚点与引擎常量一致（这是防漂移的闸门）。
2. `adapters` 的 `DetectionAcceptanceTest` 会同时卡两头：全部合法行为模型零 flag、全部内置样本在 DEFAULT 档 ≥95% 检出、边界样本全通过。

## 工具链功能轨的加法定式

第十五章的每条轨都遵守同一形状：**可配置的外部依赖 + 可注入的接缝 + 离线降级路径**。

- 外部依赖（docker、`genSources`、spark、ViaProxy、ProtocolLib、真实客户端）一律参数化，路径 / 通道可注入。
- 离线时给出明确的降级说明，写进 `notes` / `logs`，**不伪造外部结果**（不编造 spark 链接、不伪造抓包、不声称拉起过容器）。
- 单元测试只依赖内存替身：`RconTransport`、`CommandChannel`、`ContainerExecutor`、`ClientHarness`、`ProtocolProbe` 都是可替换接缝，测试不得需要网络、docker 或真实 Minecraft。

新增一条轨时按这个形状走，`report.ReportSection` 让它自动能进报告。

## 测试

```bash
./gradlew test                       # 全部（83 个测试类、423 个用例）
./gradlew :adapters:test             # 单模块
./gradlew test --tests "*DetectionAcceptanceTest*"
./gradlew test --rerun-tasks         # 强制重跑
```

## 前端

`console/` 是 React + Vite + TypeScript。数据契约在 `console/src/types/console.ts`，跟 Java 侧 DTO 字段一一对应；样本数据在 `console/src/data/fixtures.ts`，接口模式在 `console/src/data/api.ts`。

```bash
cd console
npm run dev
npm run build      # tsc --noEmit && vite build
npm run lint       # 零告警
```

样式走 CSS Modules + `src/styles/tokens.css` 的设计令牌，不用组件库模板。动效统一用令牌里的缓动与时长。**注意**：CSS Modules 会本地化 `animation` 简写里的名字，引用关键帧必须在同一模块文件里就地声明 `@keyframes`，否则动画静默失效（趋势图折线曾经因此整条不可见）。

## 改动约定

- Java 不用通配符 import；不留没用的 import、字段、局部变量。
- 注释只在「为什么」不显然的地方写，所有注释与 Javadoc 用中文。物理常量与阈值必须写清依据。
- 不确定的行为标注为待验证，不要靠猜。
