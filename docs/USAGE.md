# 使用

## 命令总览

```
laplace run         跑基准，产出报告、证据与回归入库
laplace gate        对已有运行结果判门禁
laplace replay      按 seed 回放一条轨迹
laplace archive     查证据库与回归库；--verify-chain 校验证据链
laplace test        适配器矩阵冒烟
laplace console     导出前端控制台数据
laplace debug       调试轨 + 热重载双轨制
laplace sandbox     沙箱轨
laplace profile     性能剖析轨
laplace crash       崩溃诊断器
laplace protocol    协议兼容轨
laplace clienttest  CI 客户端测试轨
```

每个子命令都支持 `--help`。

## run

```bash
laplace run --suite legit --players 10000 --ticks 1200 --seed 20261013
laplace run --suite cheat --tier DEFAULT --cheat speed.wurst.default
laplace run --suite boundary
laplace run --suite mixed --platform paper --mc 1.21.11 --parallel
```

| 参数 | 说明 |
|-|-|
| `--suite` | `legit` 合法模拟 / `cheat` 作弊矩阵 / `boundary` 阈值边界 / `mixed` 两者合并 |
| `--platform` | 目标平台；`in-process`（默认）走进程内仿真轨 |
| `--mc` / `--adapter` | Minecraft 版本 / 加载器版本（模组线用） |
| `--scenario` | 场景剧本，内置 `pvp` `mining` `bridge-sprint` `reconnect` `respawn` `warmup` `low-tps` |
| `--players` / `--ticks` / `--seed` | 模拟规模 / 每轨迹 tick 数 / 随机种子 |
| `--tier` | 作弊档位 `min` `default` `max` `hidden` |
| `--cheat` | 只跑指定样本 id，可重复 |
| `--parallel` | 并发灌入，用于并发安全断言 |
| `--coverage` | 核心模块行覆盖率，由 JaCoCo 结果回填 |
| `--workspace` | 工作目录，默认 `build/laplace` |
| `--json` | 以 JSON 输出本次运行 |

退出码：门禁通过 0，未通过 1。

报告目录里累积历次运行（门禁按「一次完整基准」口径评估），所以连续跑 legit 与 cheat 后，`gate` 会同时看到两侧数据。想重新开始就换一个 `--workspace`，或删掉 `report/report.json`。

合法轨迹由「物理预测器 + 亚容差噪声」生成：位移来自按原版运动规则逐 tick 预测的结果，噪声预算严于检测阈值，因此正常玩家不误报是实现出来的性质，而不是调参调出来的。

## gate

```bash
laplace gate --workspace build/laplace
laplace gate --report build/laplace/report/report.json --max-fpr 0.0001 --min-detection 0.95
```

门禁口径固定：误报率 ≤0.01%、检出率 ≥95%、边界样本全通过、并发安全全通过、覆盖率 ≥70%。后三项可以用 `--allow-concurrency-failure` / `--allow-boundary-failure` / 不传 `--min-coverage` 放宽；前两项是平台的核心价值，不提供放宽开关。

## replay

```bash
laplace replay --seed 20261013 --profile SPRINT --ticks 200 --stepwise --limit 40
laplace replay --cheat speed.wurst.default --tier DEFAULT
```

轨迹由 seed 与行为模型唯一决定，所以「同 seed 同结果」本身就是可复现证据。`--cheat` 回放注入轨迹，用来复现某条 flag。

## archive

```bash
laplace archive --limit 20
laplace archive --run run-20261016-221747
laplace archive --regression
laplace archive --verify-chain
```

误报一旦入库（`run` 自动写入），同一轨迹不会重复入库——回归库对 `trajectoryRef` 幂等。

`--verify-chain` 校验证据链：每条证据的 `payloadHash` 与 HMAC（`index | timestamp | payloadHash | prevMac`）逐环核对，能发现内容被改写、链接被删/被重排、mac 被伪造。**密钥策略**：优先用 `LAPLACE_EVIDENCE_HMAC_KEY`，没有就在 vault 里生成 `hmac.key`。密钥与数据放在一起只能证明「归档后未被单独改写」，要拿到真正的防删改保证，密钥必须放在 vault 之外。

## test

```bash
laplace test --platform paper --mc 1.21.11
laplace test --platform spongeneo --mc 1.21.10
laplace test --platform arclight --mc 1.21.1
laplace test --all --mc 1.21.11
```

对每个适配器跑三个冒烟剧本：装配成功、核心事件归一（要求 ≥10 类）、生命周期全链路（`onLoad → onEnable → onTick → onShutdown`，不依赖 `/reload`）。当前 24 个适配器、6 类家族，`--all` 共 72 项检查。

## console

```bash
laplace console --workspace build/laplace --out build/laplace/console.json
```

导出 `ConsoleData`：六张指标卡片、运行表、检出率三视图、误报/检出趋势、夜间基准、门禁结果、模块健康、插件配置、画面墙、日志。前端 `console/` 直接消费这份 JSON。

## 工具链功能轨（第十五章）

### debug — 调试轨与热重载双轨制

```bash
laplace debug --platform paper --mc 1.21.11 \
  --project . --modid sprite \
  --change METHOD_BODY \
  --changed-path src/main/resources/assets/sprite/textures/x.png \
  --jdwp-port 5005 [--wait-ide] [--log logs/latest.log]
```

产出：

- jdwp 参数与端口映射（默认 `suspend=n`，不让 CI 卡住；`--wait-ide` 才用 `suspend=y`）
- 源码映射状态（`--project` 指向 Loom 工程时走 `genSources`；没有源码映射断点打不进 MC 类）
- Mixin 织入快照（`-Dmixin.debug.export=true`，`.mixin.out` 有产物时打包成证据 zip）
- `.run/<配置名>.xml`（IntelliJ Remote 配置）与 `log4j-dev.xml`（按 modid 开 DEBUG）
- 条件断点模板库（8 个高频场景）
- 热重载判定：`--change` 给改动类型，报告「本次改动能否热生效」；给 `--log` 时顺带检测 `/reload` 热插拔禁忌（命中会挂 `⚠ 非真实运行态` 并阻断发布门禁）
- `hotreload-matrix.md`：每个 (JVM × 改动类型) 的能力自检矩阵
- 冷启动轨步骤与校验（`/reload` 会被拒）
- 资源变更路由（`assets/` → F3+T 等价；`data/` → 原版数据包 reload；`mods/` → 强制冷重启）

### sandbox — 沙箱轨

```bash
laplace sandbox --case smoke --tier SMOKE [--mount host=container] [--execute]
```

默认 **dry-run**：渲染 `docker network create` 与 `docker run` 参数、写 `docker-compose.sandbox.yml`，并做敏感路径审计。审计命中宿主敏感路径（盘符根、`~/.ssh`、`~/.aws`、`docker.sock`、`/etc`…）会直接拒绝启动并返回 1。加 `--execute` 且本机 docker 可用时才真正拉起容器——不可用会明确说「仅渲染」，不假装跑过。

### profile — 剖析轨

```bash
laplace profile --instance paper-1 [--viewpoint MOD_AUTHOR]
```

有真实 spark 通道时取 `/spark tps`、`/spark health` 回执并解析；离线降级到 MetricsAgent 合成采样，此时 `spark 链接` 显示「（无，已降级）」，不编造分享链接。输出 TPS 五窗口、MSPT 三项、堆/GC/CPU/磁盘、TPS 中文评级、性能回归嫌疑（TPS <19.5 或 MSPT max >60ms）、尖刺 tick、视角切分（反作弊看 Netty 数据包线程，模组作者看 Server thread）。

### crash — 崩溃诊断器

```bash
laplace crash --instance paper-1 --instance-dir build/crash-fixture [--heap-used 7800 --heap-max 8192]
```

采集 `crash-reports/`、`logs/latest.log`、`hs_err_pid*.log`、启动命令行、JVM 参数、mod 清单，按四类特征签名路由（Mixin 冲突 / 依赖不匹配 / JVM 原生 / OOM），做栈帧 × mod 类索引归因并给出覆盖率与置信度（覆盖率低时明确警告、不给定论）。产出 `crash-diagnosis.json`、`crash-diagnosis.md`（可直接作为报告一节）与 `scene-package.zip`（问题现场包）。

### protocol — 协议兼容轨

```bash
laplace protocol --server-version 1.21.11 [--client 1.20.6 --client 1.21] [--no-via-proxy] [--bedrock]
```

按服务端版本取相邻客户端版本构矩阵，逐格跑「登录 → 进世界 → 放置方块 → 打怪 → 聊天」最小闭环，记录失败格与失败阶段；`--no-via-proxy` 用来对比不经统一入口时的失败分布；`--bedrock` 追加 Geyser + Floodgate 格。数据包级断言对关键包类型（实体位置、区块数据、物品栈）做「类型 + 关键字段」检查——夹具是离线录制的样本，真实抓包需在 Paper 侧挂 ProtocolLib 监听器。

### clienttest — CI 客户端测试轨

```bash
laplace clienttest [--suite path/to/suite.json] [--screenshot build/shot.png]
```

跑声明式用例（`SEND` / `EXPECT_LOG_ENDS_WITH` / `WAIT_TICKS` / `WAIT_FOR_RENDER` / `SCREENSHOT`）。**截图是硬要求**：不给 `--screenshot` 时 SCREENSHOT 步骤直接判失败，这正是「失败必传截图」的落地。还会报告 MC × 加载器 × JDK × OS 矩阵格数、XVFB 是否自动启用，以及 Loom 任务命令行（网络同步器报错时自动追加 `-Dfabric.client.gametest.disableNetworkSynchronizer=true`）。离线用脚本替身推演，不声称跑过真实客户端。

## 加一个作弊样本

样本是声明式的，放在 `cheats/src/main/resources/cheats/samples/*.yaml`。注入幅度由「检测阈值锚点 × 档位系数」派生：

```
注入幅度 = DetectionThresholdAnchor.of(type) × TierCoefficient.of(type, tier)
```

锚点对齐目标引擎实际使用的阈值，档位系数决定 4 档（min 贴阈值边界、default 稳过、max 激进、hidden 隐蔽）。`source` 字段只是出处标签（Wurst / LiquidBounce / Meteor …），不再是数值来源。`SampleLibraryValidator` 会强制校验整库的主参数确实等于锚点 × 系数，锚点漂移会被 `adapters` 侧的交叉校验测试拦下。

从开源客户端批量提取参数草案：

```bash
python samples/extract/extract_cheat_configs.py --client Wurst --source D:\tmp\Wurst7 --out samples/out
```

脚本只读公开源码，产出的是草案，必须人工复核。
