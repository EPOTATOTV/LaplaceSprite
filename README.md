# LaplaceSprite

拉普拉斯精灵：面向**全体我的世界开发者**的通用便捷测试平台。在一个程序里完成「构建装配 → 拉起客户端与服务器 → 自动化实测 → 生成报告」，覆盖模组、插件、数据包、资源包、整合包、基岩 Addon 与检测引擎类目标。

## 环境

- JDK 21
- Node 20（只做前端时需要）
- Docker（可选，真实环境轨与沙箱轨需要）

## 构建与测试

```bash
./gradlew build          # 编译 + 单测 + JaCoCo
./gradlew :cli:installDist
```

Windows 上用 `gradlew.bat`。

## 跑一次基准

```bash
# 行为基线：证明正常玩家不误报
./cli/build/install/laplace/bin/laplace run --suite legit --players 1000 --ticks 200

# 异常样本矩阵（检测类子场景）：证明检测项抓得住真实作弊
./cli/build/install/laplace/bin/laplace run --suite cheat --tier DEFAULT

# 阈值边界样本
./cli/build/install/laplace/bin/laplace run --suite boundary

# 门禁判定（未通过返回退出码 1）
./cli/build/install/laplace/bin/laplace gate
```

结果默认写到 `build/laplace/`：`report/`（Markdown / JSON / HTML、检出率三视图、绕过清单）、`evidence/`（证据链，带 HMAC 链式哈希）、`regression.json`（回归用例库）。

## 命令行

```bash
laplace test --all --mc 1.21.11              # 24 个适配器冒烟（装配 / 事件归一 / 生命周期）
laplace test --plan --affected <改动路径>    # 「第一梯队 + 受影响族」CI 矩阵片段
laplace archive --verify-chain               # 校验证据链；--sync-remote 上云
laplace console                              # 导出前端控制台数据契约
```

工具链轨（第十五章）：

| 命令 | 内容 |
|-|-|
| `laplace debug` | 调试轨 + 热重载双轨制：jdwp、源码映射、Mixin 织入快照、IDE 配置、热重载能力矩阵 |
| `laplace sandbox` | 沙箱轨：一次性容器配额、只读挂载、网络隔离、OOM 自愈、世界快照、敏感路径审计 |
| `laplace profile` | 剖析轨：Spark 指标、TPS 中文评级、回归嫌疑、JFR、视角切分 |
| `laplace crash` | 崩溃诊断器：五类签名路由（含资源包/数据包加载失败）、栈帧归因、问题现场包 |
| `laplace protocol` | 协议兼容轨：版本矩阵最小闭环、ViaProxy / Geyser 入口、数据包断言 |
| `laplace clienttest` | CI 客户端测试轨：声明式 DSL、证据三件套、XVFB、Loom 任务渲染 |
| `laplace datapack` | 数据包冒烟轨：pack.mcmeta 解析、pack_format 版本兼容矩阵、加载失败特征 |
| `laplace loadtest` | 假玩家压测轨：压测剧本、bot 后端渲染、TPS/P95 MSPT 门禁口径 |
| `laplace mixin` | Mixin 生态适配轨：注入计数启动参数、启动日志失败扫描、冲突归因图 |
| `laplace render` | 渲染级测试轨：金标准截图与实拍截图的像素 diff（默认 0.5% 容差） |
| `laplace replay --mcpr` | 世界快照回放轨：ReplayMod 录像抽成可重放剧本 |

## 控制台（前端）

```bash
cd console && npm install && npm run dev
```

七个界面：平台管理后台、TestServer 编排控制台、管理员游戏界面、受测玩家 HUD、整合包后台面板、CI 指标看板、Live View 画面墙。

## 模块

| 模块 | 职责 |
|-|-|
| `core` | 数据模型、事件流契约、适配器 SPI、运行器、指标聚合、门禁、事件流回放（可断点/可导出） |
| `simulator` | BehaviorSimulator：物理预测器（原版运动规则）+ 行为模型库、轨迹生成、场景剧本 |
| `cheats` | AnomalyMatrix：TestSpec 样本库（content / tech / perf / network / anomaly / cheat）与注入器 |
| `network` | NetworkModel：延迟段 / 抖动 / 批处理 / 丢包重排 / 客户端版本差异 |
| `adapters` | 24 个适配器（6 类家族）+ 五步装配流水线 + GameTest 双向桥 + 社区生态矩阵 |
| `probes` | 探针桩与进程指纹库（带能力位，区分「没检出」与「查不了」） |
| `servers` | TestServer 编排（含混合端）、性能采集、Live View 信令、CI 客户端测试轨 |
| `sandbox` / `debug` / `hotreload` / `profile` / `crashdiag` / `protocol` | 第十五章前六条工具链轨 |
| `datapack` / `loadtest` / `replay` / `mixin` / `render` | 第十五章后五条工具链轨 |
| `report` | 报告生成、EvidenceVault 与 HMAC 证据链、回归库、控制台数据契约 |
| `cli` | 命令行入口（16 个子命令） |
| `ci` | 矩阵生成器与门禁脚本（三级收敛：PR / 每日 / 每周） |
| `console` | React + Vite 前端 |
| `samples` | 样本配置提取脚本与模块名映射 |
| `docker` | CLI 镜像与 8h8g 测试服编排 |

## 判定口径

| 目标类型 | 核心门禁 |
|-|-|
| 功能类（模组 / 插件 / 数据包 / 整合包） | 功能冒烟通过率 100%、性能基线不劣化、资源与装配全部通过 |
| 检测类（检测引擎） | 合法误报率 ≤0.01%、作弊检出率 ≥95%、阈值边界样本全部符合预期 |
| 客户端扫描器类 | 探针桩样本正反例全覆盖（93 条进程指纹、USB VID 等） |

另含并发安全、覆盖率 ≥70%，以及误报豁免（创造 / 旁观 / 允许飞行 / 开局缓冲 / 插件传送与击退）。

## 领域约束

平台不接触玩家真实数据，仅消费模拟样本与目标输出；平台核心零外部检测引擎依赖，对具体检测目标的依赖限定在 `adapters/` 内且可插拔。测试资产与证据链独立版本化。

## 许可

见 [LICENSE](LICENSE)。
