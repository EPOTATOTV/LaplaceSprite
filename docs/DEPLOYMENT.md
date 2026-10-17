# 部署

## 一、本机跑起来

需要 JDK 21。Gradle 用仓库自带 wrapper。

```bash
./gradlew :cli:installDist
./cli/build/install/laplace/bin/laplace run --suite legit --players 1000
```

产物在 `cli/build/install/laplace/`，可以直接拷走。所在网络有 TLS 中间代理时，给 JVM 加 `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT`（或等价的环境变量）。这个参数只在本机用，别写进仓库的 `gradle.properties`：Linux runner 上没有 `WINDOWS-ROOT` 这个 trust store 类型，Gradle 会直接起不来。

前端单独装：

```bash
cd console && npm install && npm run build   # 产物在 console/dist
```

## 二、Docker 跑 CLI

```bash
docker build -f docker/Dockerfile.cli -t laplace-sprite:1.1.0 .
docker run --rm -v "$PWD/build/laplace:/work/build/laplace" laplace-sprite:1.1.0 \
  run --suite legit --players 1000 --ticks 200
```

镜像内部工作目录是 `/work`，所以 `--workspace` 给相对路径时会落在挂载点里。

## 三、真实环境轨（8h8g）

真实环境轨才跑性能与验收测试；进程内轨（`--platform in-process`）只用于开发期快进快出。

1. 准备一台 8 vCPU / 8GB 的机器，装 Docker 与 Compose。
2. 把被测插件放进 `docker/plugins/`（Geyser 形态放 `docker/plugins-geyser/`）。
3. 起实例：

```bash
docker compose -f docker/docker-compose.testservers.yml up -d
docker compose -f docker/docker-compose.testservers.yml logs -f
```

compose 里编排了 Paper 1.21.11 / Spigot 1.20.6 / Nukkit-MOT / Geyser / AllayMC，以及混合端 Arclight 与 Youer，内存配额之和压在 8GB 以内。AllayMC、Arclight、Youer 的发行包需要自己放进对应目录（`docker/allay/`、`docker/arclight/`、`docker/youer/`），不放则该实例不会启动——混合端官方没有 mock，只能真实启动完整端。

4. 跑基准并把指标导出来：

```bash
CLI=./cli/build/install/laplace/bin/laplace
$CLI run --suite mixed --players 10000 --ticks 1200 --seed 20261013 --workspace build/laplace
$CLI gate --workspace build/laplace
$CLI archive --workspace build/laplace --verify-chain
$CLI console --workspace build/laplace
```

`--players 10000` 是完整基准的口径；日常改动用小规模即可。

## 四、沙箱轨部署

沙箱轨是「每个用例一个一次性容器」，比常驻测试服更适合并发用例：

```bash
# 先看渲染出来的参数与敏感路径审计结果，不拉起容器
laplace sandbox --case smoke --tier STANDARD

# 确认无误后真正执行（需要 docker daemon）
laplace sandbox --case smoke --tier STANDARD --execute
```

要点：

- 只读卷挂 `mods/` / `plugins/` 与服务端 jar，只有 `/data` 可写。
- 每轮独立 `docker network`，默认拒绝出站；白名单以 `laplace.allowlist=` label 声明，**按域名放行需要宿主防火墙或出口代理消费该 label**，docker 本身不做域名级放行。
- 测试服端口只映射到 `127.0.0.1`。
- 容器因 OOM 被杀会指数退避重启若干次，但**结果仍判失败**并标注「内存不足」——不静默重试通过。
- 敏感路径审计（盘符根、`~/.ssh`、`~/.aws`、`docker.sock`、`/etc` 等）命中会直接拒绝启动。

## 五、CI

仓库自带三个工作流：

| 工作流 | 触发 | 内容 |
|-|-|-|
| `ci.yml` | push / PR | 全量单测 + 覆盖率、关键模拟基准、阈值边界与并发、适配器冒烟（含 Sponge 系与混合端）、**工具链功能轨（第十五章离线跑）**、CodeQL + gitleaks、GateResult 汇总 |
| `adapter-matrix.yml` | push / PR | {平台} × {Minecraft 版本} × {加载器版本} 矩阵（10 项，含 spongeneo / arclight / youer），失败自动归档证据 |
| `nightly-benchmark.yml` | 每天 18:00 + 手动 | 在 self-hosted 的 `laplace-8h8g` 上跑全量基准，结果入库 |

`dev-toolchain` 作业会依次跑 `debug` / `sandbox` / `profile` / `crash`（用合成崩溃样本）/ `protocol` / `clienttest`（用真实 PNG 满足截图硬要求）/ `archive --verify-chain`，全程不需要真实 Minecraft 与 docker。

`nightly-benchmark.yml` 需要一台打了 `laplace-8h8g` 标签的 runner，没有就手动触发不了。

## 六、证据链密钥

证据链的 HMAC 密钥按此顺序取用：

1. 代码里显式注入（`EvidenceVault(Path, byte[])`）
2. 环境变量 `LAPLACE_EVIDENCE_HMAC_KEY`
3. 都没有时在 vault 内生成 `hmac.key`

**生产环境请用第 2 种，并把密钥放在 vault 之外**。密钥与数据放在一起只能证明「归档后未被单独改写」；能同时改写 `chain.json` 与密钥文件的攻击者可以重算整条链。`.gitignore` 已排除 `*.key`，仓库里没有任何密钥材料。

## 七、控制台部署

`console/dist` 是纯静态产物，任意静态服务器都能托管。如果要用真实数据，让反向代理把 `/api/console` 指到 `laplace console --out <path>` 生成的 JSON；不配置的话前端用内置样本数据，仍可完整浏览。

## 八、性能基线（设计初值，待首轮实测校准）

| 平台 | 目标承载 | TPS | MSPT | 堆 |
|-|-|-|-|-|
| Paper 1.21 | ≥50 模拟玩家 | 20.0 | <50ms | 4GB |
| Spigot 1.20 | ≥40 | 20.0 | <50ms | 3GB |
| Nukkit-MOT | ≥30 | 20.0 | <50ms | 2GB |
| Geyser 桥接 | ≥40 基岩客户端 | ≥19.5 | <55ms | 3GB |
| AllayMC | ≥30 | 20.0 | <50ms | 3GB |

跨版本对比必须同一 8h8g 规格、同一地图种子、同一场景剧本。
