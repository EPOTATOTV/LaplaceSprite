#!/bin/sh
# 生态门禁（设计文档 14.9）：laplace gate + 对应档位的冒烟。
# 退出码非 0 即门禁未通过，CI 直接失败。
#
# 用法：
#   ci/gate.sh pr
#   ci/gate.sh daily
#   ci/gate.sh weekly
#
# 矩阵单格只跑冒烟时设置 SMOKE_ONLY=1 PLATFORM=<平台> MC=<版本> ADAPTER=<加载器版本>。

set -eu

TIER="${1:-pr}"
LAPLACE="${LAPLACE:-./cli/build/install/laplace/bin/laplace}"
WORKSPACE="${WORKSPACE:-build/laplace}"
MC="${MC:-1.21.11}"
SMOKE_ONLY="${SMOKE_ONLY:-0}"

if [ ! -x "$LAPLACE" ]; then
    echo "找不到 laplace 可执行文件: $LAPLACE" >&2
    echo "先跑 ./gradlew :cli:installDist" >&2
    exit 1
fi

run_gate() {
    "$LAPLACE" run --suite "$1" --players "${PLAYERS:-2000}" --ticks "${TICKS:-600}" --workspace "$WORKSPACE"
    "$LAPLACE" gate --workspace "$WORKSPACE"
}

run_smoke() {
    mc="$1"
    if [ -n "${PLATFORM:-}" ]; then
        "$LAPLACE" test --platform "$PLATFORM" --mc "$mc" --adapter "${ADAPTER:-$PLATFORM}"
    else
        "$LAPLACE" test --all --mc "$mc"
    fi
}

case "$TIER" in
    pr|daily|weekly) ;;
    *)
        echo "未知门禁档位: $TIER（可选: pr / daily / weekly）" >&2
        exit 2
        ;;
esac

if [ "$SMOKE_ONLY" != "1" ]; then
    case "$TIER" in
        pr|daily) run_gate legit ;;
        weekly) run_gate mixed ;;
    esac
fi

# 矩阵单格只跑自己那一格，不把整档的版本列表再铺一遍。
if [ "$SMOKE_ONLY" = "1" ]; then
    run_smoke "$MC"
    exit 0
fi

case "$TIER" in
    pr)
        run_smoke "$MC"
        ;;
    daily)
        for version in ${MC_LIST:-26.1 1.21.11 1.21.1}; do
            run_smoke "$version"
        done
        ;;
    weekly)
        for version in ${MC_LIST:-26.1 1.21.11 1.21.1 1.20.1}; do
            run_smoke "$version"
        done
        ;;
esac
