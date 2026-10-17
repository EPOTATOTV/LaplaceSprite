# 变更日志

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本。

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
