# ci

生态兼容门禁（设计文档 14.9）。这里只放脚本，工作流本体在 `.github/workflows/`：

| 档位 | 工作流 | 跑什么 |
| --- | --- | --- |
| PR 子集 | `.github/workflows/ecosystem-pr-gate.yml` | 最新 1 个 MC LTS × 最新稳定加载器 × 基线四件套；不含插件线，`fail-fast: false`、`max-parallel: 4` |
| 每日 | `.github/workflows/ecosystem-daily.yml` | 近 3 个 MC 大版本 × 全加载器，插件线加四件套 |
| 每周 | `.github/workflows/ecosystem-weekly.yml` | 全量版本与加载器，模组线加 Create × Alex's Mobs，插件线加 Multiverse × WorldEdit × ViaVersion |

基岩线与代理线在每日 / 每周工作流里各占一个独立 job，分别用 JDK 21 与 JDK 17。

## 为什么 ci/ 里不再放一份 yml

工作流只有 `.github/workflows/` 一份，避免两份定义漂移。`ci/` 只维护可执行脚本，本地和 CI 用同一条命令。

## 脚本

`generate-matrix.py`：Python 3 标准库，读 `adapters/src/main/resources/ecosystem/version-snapshot.json`
（与 Java 侧 `MatrixCellGenerator` 共用），输出矩阵 JSON 与 GitHub Actions 的 `include:` 片段。

```sh
python ci/generate-matrix.py --tier pr
python ci/generate-matrix.py --tier weekly --format yaml
python ci/generate-matrix.py --tier pr --github-output --cell-file build/ci/matrix-cells.json
```

裁剪规则：基线模组在候选 (MC 版本, 加载器) 上没有构建就丢掉整格，原因写进 `dropped`。
MC 26.1 起的格把 job 的 JDK 设为 25。

`gate.sh`：跑 `laplace run`、`laplace gate` 和该档位的 `laplace test` 冒烟，任何一步失败退出非 0。

```sh
LAPLACE=./cli/build/install/laplace/bin/laplace ci/gate.sh pr
```

矩阵单格只跑冒烟时用 `SMOKE_ONLY=1 PLATFORM=fabric MC=1.21.11 ADAPTER=0.19.5 ci/gate.sh pr`。

## OptiFine

OptiFine 无 GitHub、无 Maven、EULA 限制再分发，只在配置仓人工锁定 URL + SHA256，
只跑 Forge 1.20.1 LTS 线的装 / 不装对照。它不进版本清单，也不进自动门禁。
