# LaplaceSprite

拉普拉斯精灵：一个把「构建装配 → 拉起客户端与服务器 → 自动化实测 → 出报告」收进一条命令的 Minecraft 测试平台。

面向写模组、写插件、写数据包、做整合包的人。首期以「独立反作弊检测引擎」作为参考验证目标，不是平台的唯一用途。

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
# 合法模拟：证明正常玩家不误报
./cli/build/install/laplace/bin/laplace run --suite legit --players 1000 --ticks 200

# 作弊矩阵：证明检测项抓得住真实作弊
./cli/build/install/laplace/bin/laplace run --suite cheat --tier DEFAULT

# 阈值边界样本
./cli/build/install/laplace/bin/laplace run --suite boundary

# 门禁判定（未通过返回退出码 1）
./cli/build/install/laplace/bin/laplace gate
```

结果默认写到 `build/laplace/`：`report/`（Markdown / JSON / HTML 报告）、`evidence/`（证据链，带 HMAC 链式哈希）、`regression.json`（回归用例库）。

其它子命令：

```bash
laplace test --all --mc 1.21.11              # 24 个适配器冒烟（装配 / 事件归一 / 生命周期）
laplace test --platform spongeneo --mc 1.21.10
laplace replay --seed 20261013 --ticks 80 --stepwise
laplace archive --limit 20                   # 查证据；--regression 查回归库；--verify-chain 校验证据链
laplace console                              # 导出前端控制台数据契约
```

## 工具链功能轨（第十五章）

```bash
laplace debug   --platform paper --mc 1.21.11 --changed-path src/main/resources/assets/x.png
laplace sandbox --case smoke --tier SMOKE            # 默认 dry-run + 敏感路径审计
laplace profile --instance paper-1
laplace crash   --instance-dir build/crash-fixture
laplace protocol --server-version 1.21.11 --bedrock
laplace clienttest --screenshot build/shot.png
```

| 轨 | 内容 |
|-|-|
| 调试轨 | jdwp 端口注入（默认 `suspend=n`）、源码映射（`genSources` 思路）、Mixin 织入快照（`.mixin.out`）、IDE / log4j 配置生成、条件断点模板库、日志按线程切分录制 |
| 热重载双轨制 | 开发期 Hot Loop（方法体热替换，DCEVM / JBR 扩展档）vs 发布前冷启动轨；能力自检矩阵、资源变更路由表、禁忌标记 |
| 剖析轨 | 内置 Spark（tps / health / profiler）而非自研采样器；TPS 中文评级、回归嫌疑判定、JFR 落盘、视角切分 |
| 沙箱轨 | 一次性容器 + 资源配额、只读挂载、网络隔离白名单、OOM 自愈与「OOM 也算 bug」上报、世界快照、RCON 控制面、敏感路径审计 |
| 崩溃诊断器 | 现场采集、四类特征签名路由（Mixin 冲突 / 依赖不匹配 / JVM 原生 / OOM）、栈帧 × mod 类索引归因、人话建议、问题现场包 |
| 协议兼容轨 | 服务端 × 客户端版本矩阵最小闭环、ViaProxy 统一入口、Geyser + Floodgate 基岩格、ProtocolLib 数据包级断言 |
| CI 客户端测试轨 | 声明式用例 DSL（SEND / ENDS_WITH）、证据三件套（logs / XML / 截图）、MC × 加载器 × JDK × OS 矩阵、XVFB 自动启用 |

## 控制台（前端）

```bash
cd console
npm install
npm run dev
```

七个界面：平台管理后台、TestServer 编排控制台、管理员游戏界面、受测玩家 HUD、整合包后台面板、CI 指标看板、Live View 画面墙。

## 模块

| 模块 | 职责 |
|-|-|
| `core` | 数据模型、事件流契约、适配器 SPI、运行器、指标聚合、门禁 |
| `simulator` | 物理预测器（vanilla 运动规则）+ 合法行为模型库、轨迹生成、场景剧本 |
| `cheats` | 作弊样本库（注入幅度 = 检测阈值锚点 × 档位系数）与注入器 |
| `network` | 延迟段 / 抖动 / 批处理 / 丢包重排 / 客户端版本差异 |
| `adapters` | 24 个适配器（Bukkit 系 / 代理端 / 基岩系 / 模组加载器 / Sponge 系 / 混合端）、描述符解析、依赖解析、版本矩阵 |
| `probes` | 探针桩与进程指纹库（带能力位，区分「没检出」与「查不了」） |
| `servers` | TestServer 编排（含混合端双目录）、性能采集、Live View 信令、CI 客户端测试轨 |
| `sandbox` | 沙箱轨：容器配额、只读挂载、网络隔离、OOM 自愈、世界快照、RCON |
| `debug` | 调试轨：jdwp、源码映射、Mixin 织入快照、IDE / 日志配置、断点模板 |
| `hotreload` | 热重载双轨制、能力自检矩阵、资源变更路由、禁忌标记 |
| `profile` | 剖析轨：Spark 集成、TPS 评级、JFR、视角切分 |
| `crashdiag` | 崩溃诊断器：分类路由、栈帧归因、诊断报告与现场包 |
| `protocol` | 协议兼容轨：版本矩阵、ViaProxy / Geyser 入口、数据包断言 |
| `report` | 报告生成、证据库与 HMAC 证据链、回归库、控制台数据契约 |
| `cli` | 命令行入口（run / gate / replay / archive / test / console / debug / sandbox / profile / crash / protocol / clienttest） |
| `console` | React + Vite 前端 |
| `samples` | 作弊配置提取脚本与模块名映射 |
| `docker` | CLI 镜像与 8h8g 测试服编排 |
| `.github/workflows` | PR 门禁、适配器矩阵、夜间基准 |

## 领域约束

平台不接触玩家真实数据，仅消费模拟样本与检测目标输出；平台核心零外部检测引擎依赖，对具体检测目标的依赖限定在 `adapters/` 内且可插拔。测试资产与证据链独立版本化。

## 许可

见 [LICENSE](LICENSE)。
