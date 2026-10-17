# 验收报告

对应设计文档 v1.1.0 第九章「验收标准」、14.8「适配器开发指南与验收标准」、12.6 与第十五章各功能轨。以下结论全部来自本机实测，命令与输出一并列出。

## 一、第九章验收标准（v1.1.0 共 10 条）

| # | 标准 | 结论 | 证据 |
|-|-|-|-|
| 1 | 对首期接入的检测引擎全检测项：合法误报率 ≤0.01%、作弊检出率 ≥95%、阈值边界样本全部符合预期 | **达成（进程内轨）** | 1000 名合法玩家误报 0；36 个作弊样本检出 36；36 个边界样本符合预期 36 |
| 2 | 对客户端扫描器类检测目标：探针桩样本正反例全覆盖（进程指纹 80+ 项、USB VID 等） | **达成** | 93 条进程指纹；6 个客户端各 ≥1 条正例 + 1 条硬件外挂正例；5 条反例不触发。新增**能力位**区分「没检出」与「查不了」：`degraded-lab` 样本把 USB / 注册表标为不可查，`looksInjected` 不再把「查不了」当作清白 |
| 3 | 至少 3 个平台（Paper/Spigot/Nukkit）可通过 TestServer 编排器端到端运行同一场景剧本 | **部分达成** | 24 个适配器的装配 / 事件归一 / 生命周期冒烟在进程内轨全通过；Paper / Spigot / Nukkit 的真实实例编排已写好 compose，但本机未起真实服务端 |
| 4 | 误报修复必须携带回归轨迹入库，CI 门禁强制 | **达成** | `RegressionLibrary` 对 `trajectoryRef` 幂等，`run` 自动入库；`ci.yml` 的 baseline / boundary / adapter-smoke / dev-toolchain 作业都进 `gate` 汇总，失败即整条流水线红 |
| 5 | 一次完整基准（10000 合法 + 全作弊矩阵）在 CI 内 ≤30 分钟 | **未实测** | `--players 10000` 路径可用，本机 1000 人 366 ms，线性外推远低于 30 分钟——属外推，非实测 |
| 6 | v1.1.0 交付：独立仓库、CLI + Docker 镜像、下载渠道、设计/使用文档、验收报告 | **达成** | 独立仓库；`:cli:installDist` 与 `docker/Dockerfile.cli`；README + `docs/` 五份文档 + 本报告 |
| 7 | 适配器体系覆盖 ≥5 类家族端到端可跑，其中新增 Sponge 系与混合端通过 14.8 六步验收 | **达成** | **6 类家族**：Bukkit 系 6 / 代理端 3 / 基岩系 4 / 模组加载器 4 / Sponge 系 3 / 混合端 4 = **24 个适配器**；`laplace test --all` **72 项检查 0 失败**；`--platform spongeneo --mc 1.21.10` 与 `--platform arclight --mc 1.21.1` 各 3 项冒烟全过（事件归一分别 14 / 16 类） |
| 8 | 崩溃诊断器对已知崩溃样本（Mixin 冲突 / 缺依赖 / OOM / 原生崩溃）四类路由准确，产出结构化诊断报告与人话建议 | **达成（单元级 + CLI 实测）** | 四类路由各有正例测试，且含「日志提到 OOM 但现场有 hs_err」的优先级用例；CLI 实测依赖冲突样本路由到 `DEPENDENCY_MISMATCH`，产出 `crash-diagnosis.{json,md}` 与 `scene-package.zip` |
| 9 | 协议兼容轨：被测服务端 × ≥3 个客户端版本矩阵格通过最小闭环；ProtocolLib 数据包断言通过 | **部分达成** | 矩阵 5 格（1.20.6 / 1.21 / 1.21.1 / 1.21.4 / 1.21.11）经 ViaProxy 统一入口全部通过最小闭环；数据包断言 6 条对离线夹具全部通过。**真实 ProtocolLib 抓包未做** |
| 10 | 调试轨可用；热重载双轨制：Dev Hot Loop 方法体热替换生效、发布前冷启动轨禁用 /reload 语义 | **部分达成** | jdwp 参数、源码映射状态、Mixin 织入快照、IDE / log4j 配置、8 个断点模板、冷启动轨拒绝 `/reload`、资源变更路由、`⚠ 非真实运行态` 禁忌标记全部产出并实测。**「方法体热替换生效」只做到能力判定，未在真实 JVM 上完成一次热替换** |

实测输出：

```
##### legit #####
  合法样本 1000 · 误报 0 · 误报率 0.000000%     门禁：通过   exit=0
##### cheat #####
  作弊样本 36 · 检出 36 · 检出率 100.0000%      门禁：通过   exit=0
##### boundary #####
  边界样本 36 · 符合预期 36                     门禁：通过   exit=0
##### gate（三次运行聚合） #####
  合法样本 1000 · 误报 0 · 误报率 0.000000%（上限 0.010000%）
  作弊样本 72 · 检出 72 · 检出率 100.0000%（下限 95.0000%）
  边界样本 36/36 · 并发安全 通过                 门禁结果：通过  exit=0
##### adapter smoke #####
  冒烟结果：72 项，失败 0 项                     exit=0
##### evidence chain #####
  证据链校验 通过 · 链接 1 条 · 首断点 无        exit=0
```

## 二、14.8 适配器验收标准

| 验收项 | 标准 | 结论 | 证据 |
|-|-|-|-|
| 装配解析 | 描述文件字段全解析；依赖三语义与生产一致；矩阵匹配无降级告警 | 达成 | 10 个描述符解析器各带真实格式样本测试（含 `sponge_plugins.json`）；`DependencyOrderer` 覆盖硬 / 软 / 反向与成环检测 |
| 事件桥 | ≥10 类核心事件归一；异步 / 并发事件时序正确 | 达成 | 24 个适配器实测 14–16 类；BungeeCord intent、Velocity Continuation、混合端双发去重各有独立测试 |
| 生命周期 | `onLoad → onEnable → onTick → onShutdown` 全链路无异常 | 达成 | 每个适配器一份生命周期日志；`onLoad` 校验失败会拒绝进入下一阶段 |
| 并发适配 | Folia 调度器路由正确；跨区域无线程违规 | 达成 | `FoliaAdapter.routeTask` 四路路由 + 跨区域排队标记测试 |
| 双端（模组） | client / server 各一组断言通过；side 剥离正确 | 部分达成 | 双端流水线与 side 字段已建模并有测试；未在真实客户端上跑 `runClientGameTest` |
| 版本矩阵 | 至少 2 档 Minecraft 版本通过 | 达成 | 四来源 + 离线快照；覆盖 1.8–1.21 与 1.20 / 1.21 两档；`LegacyLoaderAppendix` 记录 Rift / LiteLoader 不支持 |
| 证据 | 失败自动归档日志 + 截图 + 画面回放 | 部分达成 | 运行 / flag / 崩溃现场包 / 客户端证据三件套自动归档；画面回放依赖 Live View 真实流，未接入 |

## 三、12.6 真实环境与性能补充

| 项 | 结论 |
|-|-|
| 一键拉流 ≤3s 出画 | 单元级验证 |
| 多路并排 ≥8 路稳定 | 单元级验证 |
| 长稳 ≥12h 无 OOM、无 TPS 持续掉底 | 未实测（`LongRunWatchdog` 判定逻辑已实现并有单元测试） |
| 性能基线随夜间基准更新入库 | 未实测（`ConsoleCommand` 已按日期聚合 `NightlyBenchmarkResult`） |

## 四、第十五章功能轨状态

| 轨 | 离线实现 | 外部依赖未验证的部分 |
|-|-|-|
| 15.1 调试轨 | jdwp 参数、源码映射状态机、Mixin 快照与打包、IDE / log4j 配置（XML 可解析）、8 个断点模板、日志按线程切分 | 真实 jdwp 附加、真实 `genSources`、真实 `.mixin.out` |
| 15.2 热重载双轨制 | 能力自检矩阵（JVM × 改动类型）、资源变更路由表、冷启动轨拒绝 `/reload`、禁忌标记阻断发布门禁 | 真实 HotSwap / DCEVM / JBR 热替换、真实 Mixin 热交换 |
| 15.3 剖析轨 | Spark 命令构造与回执解析、四类指标、TPS 中文评级、回归嫌疑阈值、JFR 命令行、视角切分 | 真实 spark 通道（无通道时明确降级，不编造链接） |
| 15.4 沙箱轨 | 一次性容器参数渲染、只读挂载、网络隔离白名单、OOM 自愈（OOM 判失败不静默通过）、世界快照、RCON 编解码、敏感路径审计 | 真实 `docker run`（无 docker 时明确「仅渲染」） |
| 15.5 崩溃诊断器 | 现场采集、四类签名路由（含优先级）、栈帧 × 类索引归因与覆盖率告警、人话建议、现场包 | 真实 `hs_err` / 真实 Modrinth 类索引（内置 22 条前缀） |
| 15.6 协议兼容轨 | 版本矩阵与失败格 / 失败阶段、ViaProxy 与 Geyser 命令渲染、数据包断言引擎 + 离线夹具 | 真实 ViaProxy / Geyser 拉起、真实 ProtocolLib 抓包 |
| 15.7 CI 客户端测试轨 | 声明式 DSL、证据三件套（**缺截图判失败**）、多维矩阵、XVFB 自动启用、Loom 命令渲染（含同步器开关） | 真实 Linux + XVFB + Loom 客户端运行 |

## 五、单元测试与构建

```
testcases=498 suites=94     全部通过（gradle test）
main-java=375  all-java=460
```

- `gradle build` 通过，JaCoCo 报告生成于各模块 `build/reports/jacoco/`。
- `console`：`npx tsc --noEmit` 无错误，`npm run lint` 零告警，`npm run build` 成功。
- `samples/extract/extract_cheat_configs.py` 在合成样本上跑通，产出 CheatSpec 草案。
- `core` 之前没有任何单元测试，本轮补上 `ExemptionPolicyTest`（9 个用例，含 Runner 集成）。

## 六、本轮补齐的文档要求（复核 v1.1.0 时发现 7 处「文档有、代码无」）

复核 §4.1 / §4.2 / §4.6 / §12.2 / §14.5 / §14.6 / §14.7 时发现以下能力只有文档描述、代码里并不存在，本轮全部补上：

| 缺口 | 文档依据 | 补齐内容 | 实测 |
|-|-|-|-|
| 误报豁免规则 | §4.1「创造/旁观/允许飞行/开局缓冲期/其它插件传送与击退不计误报（可配置）」 | `ExemptionRule` / `SessionContext` / `ExemptionPolicy`；`Runner` 在计数前判定；`run --exemptions` 可配 | 9 个用例通过；默认合法基准仍 0 误报、`--exemptions none` 下同样 0（默认队列无豁免触发） |
| 检出率三视图 | §4.2「按检测项 / 按客户端 / 按档位三视图」 | `DetectionRateReport` + 三视图 Markdown，落 `report/detection-rate.md` | 实测「总样本 36，命中 36，100.0%」；修正了此前「命中 45 / 125%」的错口径 |
| 绕过清单 | §4.2「已实现但被绕过的检测项 → backlog」 | `BypassAnalyzer` / `BypassList`，分「已实现但被绕过」与「引擎尚未实现」两节，落 `report/bypass-list.md` | 全档检出时 0 条；只跑单档时不再误报未注入档位为绕过 |
| 证据可上云 | §4.6「本地 + 可上云存储」 | `EvidenceRemoteStore` / `LocalMirrorRemoteStore` / `HttpRemoteStore`（S3 兼容）+ `syncRemote` / `verifyRemote` | 实测同步 92/92、校验 92/92 一致；同步后 HMAC 链仍通过 |
| SSH / Docker API 编排 | §12.2「编排控制（SSH / Docker API）」 | `SshCommandDriver` + `DockerApiDriver` + `ServerDriverKind` 工厂 | 请求/参数渲染与降级路径有测试；**未对真实主机执行** |
| 装配流水线五步 | §14.5 resolve / layout / install / launch / verify | `InstallPipeline`，verify 四项健康检查，`test --pipeline` 可跑 | 实测 paper：前四步 PASS，verify 因「端口未监听」如实 FAIL |
| GameTest 双向桥 | §14.6「原生 GameTest 与平台断言脚本可互转」 | `GameTestBridge` + `NativeGameTestResult` + `PlatformAssertionScript` | 7 个用例通过，含「退出码 = 失败用例数」强校验 |
| 第一梯队 + 受影响族 | §14.7「PR 门禁跑第一梯队 + 受影响族子集」 | `FamilyTier` / `AffectedFamilySelector` / `MatrixPlan`，`test --plan --affected` 输出矩阵片段 | 实测只改 `bukkit/` 时收敛到 6 个 Bukkit 家族平台 |

## 七、未验证项与风险

按「先检查错误前提、信息缺失」的要求如实列出，以下都没有真实证据：

1. **真实服务端与真实客户端**。`docker/docker-compose.testservers.yml` 只是编排文件，本机没起过真实实例，也没接入真实客户端。第 3 条因此只算部分达成。
2. **WebRTC 真实画面**。`LiveViewService` 的信令与预算约束是进程内实现，没有真实浏览器媒体流经过验证。
3. **外部 HTTP 分支**。版本矩阵四来源与 Modrinth / CurseForge 的**在线**分支只按已知响应形状实现，测试覆盖的是「不可达时降级到本地快照」。
4. **性能基线数字**。12.4 的 5 行基线是设计初值，未在 8h8g 上实测校准。
5. **前端观感**。上一版做过一次浏览器实测并修掉 5 个真实缺陷（含趋势图折线因 CSS Modules 本地化关键帧而整条不可见）；v1.1.0 前端只做了 `SimID` 重命名，未再逐屏目视复检。
6. **检测引擎的口径位置**。当前通过门禁的是 `adapters/sim/HeuristicDetectionEngine`——进程内、确定性的开发期引擎，覆盖 15 类作弊共 15 个检测项。它**不是**首期接入的真实检测引擎；接入真实引擎需实现 `adapters.sim.DetectionEngine` 后替换。第 1 条应理解为「平台口径与流程已跑通」，而非「真实引擎全检测项已在真实环境验证」。
7. **`--coverage` 未自动回填**。JaCoCo 结果到 `run --coverage` 的连接需要 CI 脚本额外一步解析。
8. **热重载「生效」**。文档要求「Dev Hot Loop 方法体热替换生效」，本平台做到的是「按 JVM × 改动类型判定能否热生效」，没有在真实 JVM 上完成一次热替换。
9. **沙箱网络白名单**。docker 本身不提供按域名放行，白名单以 label 声明、需宿主防火墙或出口代理消费；代码与文档已写明，平台不假装 docker 能按域名放行。
10. **证据链的边界**。密钥与数据同处一地时只能证明「归档后未被单独改写」；整段后缀连同索引一起删除无法发现。真正保证需要把密钥与链尾锚点放在 vault 之外。
11. **SSH / Docker API 控制面**。两个驱动只在注入的通道上验证了请求与参数渲染、以及不可用时的降级；**没有对真实 SSH 主机或真实 docker daemon 执行过**。`DockerApiHttpTransport` 的 unix 分支按 `Content-Length` 读响应，未处理 chunked；`docker logs` 的多路复用帧按纯文本行处理。
12. **对象存储**。`HttpRemoteStore` 的 AWS SigV4 签名与请求形态只在离线传输替身上验证，未对真实 S3 / MinIO 端点跑过；凭据只从环境变量读，代码与仓库里没有密钥。
13. **GameTest 往返是有损的**。只有「用例名 / 期望 / 通过与否」保真；JUnit 栈帧、耗时、结构模板与环境定义不保留，`WARN` 往返后降级为 `PASS`。不要把它当成完整语义转换。
14. **装配流水线的验签**只做「声明与落盘一致」的对齐，没有 PGP / JARSec 真实验签（需要公钥环）；`launch` 在进程内轨只走模拟生命周期钩子，未联调真实进程编排。
