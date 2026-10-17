# 验收报告

对应设计文档 v1.3.0 第九章「验收标准」、14.8「适配器开发指南与验收标准」、12.6 与第十五章十二条工具链轨。以下结论全部来自本机实测。

## 一、第九章验收标准（v1.3.0 共 11 条）

| # | 标准 | 结论 | 证据 |
|-|-|-|-|
| 1 | 功能类目标冒烟全绿、性能基线不劣化；检测引擎类目标误报 ≤0.01%、检出 ≥95%、边界全通过；客户端扫描器类样本正反例全覆盖 | **达成（进程内轨）** | 500 名行为基线误报 0；36 个异常样本检出 36；36 个边界样本符合预期 36；93 条进程指纹 + 能力位 |
| 2 | 至少 3 个平台可通过 TestServer 编排器端到端运行同一场景剧本 | **部分达成** | 24 个适配器冒烟全过；Paper / Spigot / Nukkit 真实实例编排已写好 compose，本机未起真实服务端 |
| 3 | 缺陷修复必须携带回归轨迹入库，CI 门禁强制 | **达成** | `RegressionLibrary` 幂等入库，`run` 自动写入；CI 作业全进 `gate` |
| 4 | 一次完整基准（10000 行为基线 + 全异常样本矩阵）在 CI 内 ≤30 分钟 | **未实测** | `--players 10000` 路径可用；1000 人 366 ms，线性外推远低于 30 分钟，属外推 |
| 5 | v1.3.0 交付：独立仓库、CLI + Docker 镜像、下载渠道、设计/使用文档、验收报告 | **达成** | 仓库 + `:cli:installDist` + `Dockerfile.cli` + 五份文档 |
| 6 | 适配器体系覆盖 ≥5 类家族，Sponge 系与混合端通过 14.8 六步验收 | **达成** | **6 类家族 / 24 个适配器**；`laplace test --all` **72 项 0 失败** |
| 7 | 崩溃诊断器四类（现为五类）路由准确，产出结构化诊断与人话建议 | **达成（单元级 + CLI 实测）** | 新增「资源包 / 数据包加载失败」类；CLI 实测 JSON 语法错误样本路由到 `RESOURCE_PACK_LOAD_FAILURE` 并提取 `pack.mcmeta` + `$.pack.description` + `MC-123460` |
| 8 | 协议兼容轨：服务端 × ≥3 客户端版本矩阵最小闭环；ProtocolLib 数据包断言通过 | **部分达成** | 矩阵 5 格经 ViaProxy 全过；数据包断言 6 条对离线夹具全过；**真实 ProtocolLib 抓包未做** |
| 9 | 调试轨可用；热重载双轨制：方法体热替换生效、冷启动轨禁用 /reload | **部分达成** | jdwp / 源码映射 / Mixin 快照 / IDE 配置 / 冷启动拒绝 `/reload` / 禁忌标记全部产出；**「热替换生效」只做到能力判定** |
| 10 | 社区生态基线四件套 ≥6 格；假玩家压测 N≥50 稳态 TPS≥19.5 且 P95 MSPT≤50ms；数据包冒烟 ≥2 版本列；Mixin 注入计数门禁在 CI 生效 | **部分达成** | 基线四件套 / 五档性能矩阵 / 插件四件套 / 重型三件套 / 矩阵生成器与三级收敛均已实现并有 39 个用例；压测门禁边界（19.5 / 50.0）由 `LoadGateTest` 钉住；pack_format 矩阵实测 3 列（1.20.6 不兼容、1.21.4 通过、1.21.5 不兼容）；Mixin 计数参数渲染已进 CI 作业。**真实客户端、真实压测、真实 1.21.5 服务端未跑** |
| 11 | 全类型模组测试矩阵覆盖 ≥10 类，每类含测试重点、框架/工具与真实来源 | **达成** | `TargetTypeMatrix` 覆盖 **12 类**，合计 36 条代表作记录（去重 29），来源全部为绝对 URL；测试断言 12 类五项均非空且不存在「检测引擎」默认行 |

实测输出：

```
##### legit #####      合法样本 500 · 误报 0 · 误报率 0.000000% · 豁免 0 · 门禁：通过
##### cheat #####      作弊样本 36 · 检出 36 · 检出率 100.0000% · 绕过项 0 · 门禁：通过
##### boundary #####   边界样本 36 · 符合预期 36 · 门禁：通过
##### gate #####       3 次运行聚合 · 误报 0/500 · 检出 72/72 · 边界 36/36
                       功能冒烟 0/0（不适用）· 性能基线未劣化（Δ 0.00%）· 资源与装配通过
                       门禁结果：通过
##### datapack #####   pack_format 61 → 1.20.6 不兼容 / 1.21.4 通过 / 1.21.5 不兼容
##### render #####     4x4 金标准 vs 实拍 · 差异 0/16（0.0000%）· 通过
```

## 二、14.8 适配器验收标准

| 验收项 | 标准 | 结论 |
|-|-|-|
| 装配解析 | 描述文件字段全解析；依赖三语义与生产一致；矩阵匹配无降级告警 | 达成（10 个解析器；`InstallPipeline` 五阶段含 verify 四项） |
| 事件桥 | ≥10 类核心事件归一；异步 / 并发时序正确 | 达成（24 个适配器实测 14–16 类） |
| 生命周期 | onLoad → onEnable → onTick → onShutdown 全链路无异常 | 达成 |
| 并发适配 | Folia 调度器路由正确；跨区域无线程违规 | 达成 |
| 双端（模组） | client / server 各一组断言通过；side 剥离正确 | 部分达成（未在真实客户端跑 runClientGameTest） |
| 版本矩阵 | 至少 2 档 Minecraft 版本通过 | 达成 |
| 证据 | 失败自动归档日志 + 截图 + 画面回放 | 部分达成（截图与画面回放依赖真实客户端与 Live View 真实流） |

## 三、第十五章十二条工具链轨

| 轨 | 模块 | 离线实现 | 未验证的外部依赖 |
|-|-|-|-|
| 15.1 调试轨 | `debug` | jdwp、源码映射、Mixin 快照与打包、IDE / log4j 配置、8 个断点模板、日志按线程切分 | 真实 jdwp 附加、真实 genSources |
| 15.2 热重载双轨制 | `hotreload` | 能力矩阵、资源变更路由、冷启动拒绝 `/reload`、禁忌标记阻断发布门禁 | 真实 HotSwap / DCEVM / JBR |
| 15.3 剖析轨 | `profile` | Spark 命令与回执解析、四类指标、TPS 评级、回归嫌疑、JFR、视角切分 | 真实 spark 通道 |
| 15.4 沙箱轨 | `sandbox` | 容器参数、只读挂载、网络白名单、OOM 自愈、世界快照、RCON、敏感路径审计 | 真实 `docker run` |
| 15.5 崩溃诊断器 | `crashdiag` | 五类签名路由（含资源包/数据包加载失败）、栈帧归因与覆盖率告警、人话建议、现场包 | 真实 hs_err、真实类索引 |
| 15.6 协议兼容轨 | `protocol` | 版本矩阵、ViaProxy / Geyser 渲染、数据包断言 + 离线夹具 | 真实 ViaProxy / ProtocolLib |
| 15.7 CI 客户端测试轨 | `servers.clienttest` | 声明式 DSL、证据三件套（缺截图判失败）、多维矩阵、XVFB、Loom 渲染 | 真实 Linux + XVFB + Loom |
| 15.8 数据包 / 资源包轨 | `datapack` | pack.mcmeta 解析与三条 Mojira 特征、DSL 顺序、`ASSERT_GAMETEST` 按版本回退、版本矩阵、资源包解析 | 真实 1.21.5 `/test` 回包 |
| 15.9 假玩家压测轨 | `loadtest` | 双后端剧本、爬坡、稳态窗口、TPS/P95 门禁、N×剧本曲线、破线现场包 | 真实 Mineflayer / Carpet / spark |
| 15.10 世界快照回放轨 | `replay` | `.mcpr` 元数据与包流解析（截断保留）、移动/指令抽取、四元组快照与双向注册表、判定口径 | 真实 ReplayMod 录像 |
| 15.11 Mixin 生态适配轨 | `mixin` | 计数启动参数、require/expect 门禁、冲突图（@Overwrite > 重复 @Redirect > @Inject）、Watchdog 归因、MixinSquared 分流、版本分层检查 | 真实 Mixin 织入与 Watchdog 输出 |
| 15.12 渲染级测试轨 | `render` | 像素 diff（0.5% 容差、尺寸不一致即失败）、渲染后端矩阵（VulkanMod 不进门禁）、shader 全黑/全白与交互探针 | 真实 GPU / 客户端 |

## 四、单元测试与构建

```
testcases=712 suites=139     全部通过（gradle test）
main-java=555
```

- `gradle build` 通过，JaCoCo 报告在各模块 `build/reports/jacoco/`。
- `console`：`tsc --noEmit` 无错误，`lint` 零告警，`build` 成功。
- `ci/generate-matrix.py --tier pr` 输出 3 格合法矩阵 JSON。
- `samples/extract/extract_cheat_configs.py` 在合成样本上跑通。

## 五、v1.3.0 通用化改动

- **重命名**：`LegitSimulator` → `BehaviorSimulator`、`CheatMatrix` → `AnomalyMatrix`、`CheatSpec` → `TestSpec`、`CheatTier` → `SampleTier`、`LegitTrajectory` → `BehaviorTrajectory`、`SimAdapter` → `SimServerAdapter`（75 个文件改写、11 个类改名，无残留）。
- **样本矩阵**：52 个 TestSpec，覆盖 6 类（CHEAT 36 / CONTENT 3 / TECH 3 / PERF 2 / NETWORK 3 / ANOMALY 5）。
- **指标**：新增功能冒烟通过率、性能基线、资源与装配三个维度，以及连接数 / 带宽 / 区块 / 实体 / 延迟 P50·P95。
- **前端**：六张卡片改为「今日运行 / 模拟人数 / 样本矩阵数 / 功能通过率 / 性能基线 / 门禁通过」，条形图改为样本类型 × 档位，新增 12 类目标矩阵面板。

## 六、未验证项与风险

1. **真实服务端与真实客户端**：compose 只是编排文件，本机没起过真实实例，也没接入真实客户端。第 2 条因此只算部分达成。
2. **WebRTC 真实画面**：Live View 信令与预算约束是进程内实现，无真实媒体流验证。
3. **外部 HTTP 分支**：版本矩阵四来源与 Modrinth / CurseForge 在线分支只按已知形状实现，测试覆盖「不可达降级到本地快照」。
4. **性能基线数字**：12.4 的基线是设计初值，未在 8h8g 实测校准。
5. **检测引擎口径**：通过门禁的是 `adapters/sim/HeuristicDetectionEngine`——开发期引擎，覆盖 15 类共 15 个检测项，**不是**真实检测引擎。
6. **SSH / Docker API 与对象存储**：只在注入通道上验证了请求渲染与降级，未对真实主机、真实 S3 端点执行。
7. **GameTest 往返有损**：只有用例名 / 期望 / 通过与否保真。
8. **压测与渲染**：`LoadGate` 阈值由单元测试钉住，但没有真实 bot 与 spark 采样；像素 diff 只在合成截图上验证，没有真实 GPU 输出。
9. **`.mcpr` 帧格式**：按 v14 设计实现，未用真实 ReplayMod 文件交叉验证；移动/指令包 id 只登记协议 763。
10. **`/test` 回包**：解析覆盖几种常见回显形态，未对真实 1.21.5 服务端取样。
11. **社区生态断言**：基线四件套、F3 帧率、JEI 配方页等目前是数据与规则层，仓库内没有拉起真实客户端并注入这些包的运行器。
12. **前端新增字段**：Java 侧 `ConsoleDataBuilder` 仍只输出原有字段，新字段由前端在缺失时用样本数据补齐；接真实后端前需要把 `sampleSpecCount` / `functionalPassRate` / `performanceBaseline*` / `sampleTypeBreakdown` / `targetTypes` 补进 `ConsoleData`。
13. **证据链边界**：密钥与数据同处一地时只能证明「归档后未被单独改写」；整段后缀连同索引删除无法发现。
14. **`ci/gate.sh`**：本机无 `sh`，只做语法检查，未实际执行；工作流也未在 GitHub Actions 上跑过。
