# 贡献指南

## 环境

- JDK 21（`java -version` 确认）
- Node 20（只改前端时需要）
- Docker（跑真实环境轨或沙箱轨时需要）

```bash
./gradlew :cli:installDist
cd console && npm install
```

## 本地验证

改完先跑这三步，缺一步都可能在 CI 上红：

```bash
./gradlew test                       # 全量单测
./gradlew :cli:installDist
./cli/build/install/laplace/bin/laplace run --suite legit --players 500 --ticks 150 --workspace build/dev
./cli/build/install/laplace/bin/laplace run --suite cheat --tier DEFAULT --ticks 150 --workspace build/dev
./cli/build/install/laplace/bin/laplace run --suite boundary --ticks 150 --workspace build/dev
./cli/build/install/laplace/bin/laplace gate --workspace build/dev   # 退出码必须为 0
```

前端：

```bash
cd console && npx tsc --noEmit && npm run lint && npm run build
```

## 模块与依赖方向

```
core ← simulator / cheats / network / probes / report
network, probes ← adapters
adapters ← servers
servers ← sandbox / debug / hotreload / profile
protocol ← adapters + servers
全部 ← cli
```

`core` 不依赖任何其它模块。新增模块要同时改 `settings.gradle` 与 `README.md` 的模块表。

## 改动约定

- Java 21，不用通配符 import，不留没用的 import / 字段 / 局部变量。
- 注释与 Javadoc 用中文，只解释「为什么」；物理常量与阈值必须写清依据。
- 不为了让门禁变绿而放宽 `ThresholdGate` / `Thresholds` / `MetricSet` 的口径。
- 外部依赖（docker、ssh、spark、真实客户端）一律可配置、可注入、离线可降级；**不要伪造外部执行结果**。
- 前端用 CSS Modules + `tokens.css` 令牌；CSS Modules 里引用关键帧必须在同一模块文件内就地声明 `@keyframes`，否则动画会静默失效。

更细的契约说明见 `docs/DEVELOPMENT.md`。

## 提交

- 一个提交只做一件事，按模块或能力分批。
- 提交信息用「类型(范围): 摘要」，类型取 `feat` / `fix` / `refactor` / `test` / `docs` / `ci` / `chore`。
- 正文说明动机与验证方式，不要只罗列文件名。

## 新增平台适配器（六步）

见 `docs/DEVELOPMENT.md` 的「新增平台接入」。要点：实现五个 SPI、注册支持矩阵、补三个冒烟剧本、本机用进程内轨迭代、真实环境轨验证、提交 CI 矩阵项。

## 新增样本

样本是声明式的，放在 `cheats/src/main/resources/cheats/samples/*.yaml`。检测类样本的注入幅度由「阈值锚点 × 档位系数」派生，`SampleLibraryValidator` 会校验一致性；锚点漂移会被 `adapters` 的交叉校验测试拦下。

## 报告问题

- 缺陷与功能请求走 issue 模板；
- 用法与设计讨论走 Discussions；
- 安全问题走 `SECURITY.md` 的私密渠道。
