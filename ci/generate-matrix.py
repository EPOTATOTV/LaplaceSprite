#!/usr/bin/env python3
"""生成生态兼容矩阵（设计文档 14.9）。

输入是仓库内离线快照 adapters/src/main/resources/ecosystem/version-snapshot.json，
与 Java 侧 MatrixCellGenerator 共用同一份数据，不访问网络。

用法：
    python ci/generate-matrix.py --tier pr
    python ci/generate-matrix.py --tier weekly --format yaml
    python ci/generate-matrix.py --tier pr --github-output --cell-file build/ci/matrix-cells.json
"""

import argparse
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DEFAULT_SNAPSHOT = os.path.join(
    ROOT, "adapters", "src", "main", "resources", "ecosystem", "version-snapshot.json"
)

LOADERS = ("fabric", "neoforge", "forge")
PAPER = "paper"

BASELINE = {
    "fabric": ["fabric-api", "jei", "sodium", "lithium"],
    "neoforge": ["jei", "lithium"],
    "forge": ["jei", "lithium"],
}

TIERS = {
    "pr": {
        "limit": 1,
        "latest_lts": True,
        "max_parallel": 4,
        "fail_fast": False,
        "plugin_line": False,
        "plugin_bundles": ["baseline-quartet"],
        "mod_bundles": ["baseline-quartet"],
    },
    "daily": {
        "limit": 3,
        "latest_lts": False,
        "max_parallel": 4,
        "fail_fast": False,
        "plugin_line": True,
        "plugin_bundles": ["baseline-quartet", "top-plugins"],
        "mod_bundles": ["baseline-quartet"],
    },
    "weekly": {
        "limit": None,
        "latest_lts": False,
        "max_parallel": 4,
        "fail_fast": False,
        "plugin_line": True,
        "plugin_bundles": ["baseline-quartet", "top-plugins", "heavy-plugins"],
        "mod_bundles": ["baseline-quartet", "content-create-alex"],
    },
}


def version_key(version):
    """按版本段拆数字，与 core 的 VersionOrder 口径一致。"""
    parts = []
    for token in re.split(r"[.\-+_]", version or ""):
        digits = re.match(r"\d+", token)
        if digits:
            parts.append(int(digits.group()))
    return tuple(parts)


def jdk_for(mc, minecraft_releases):
    """26.1 起强制 Java 25；其余取快照声明的 JDK。"""
    if version_key(mc) >= version_key("26.1"):
        return 25
    for release in minecraft_releases:
        if release.get("version") == mc:
            return int(release.get("java", 21))
    return 21


def select_versions(tier, minecraft_releases):
    stable = [release for release in minecraft_releases
              if str(release.get("channel", "")).lower() == "stable"]
    stable.sort(key=lambda release: version_key(release.get("version", "")), reverse=True)
    if TIERS[tier]["latest_lts"]:
        lts = [release for release in stable if release.get("lts")]
        chosen = lts[:1] if lts else stable[:1]
    else:
        limit = TIERS[tier]["limit"]
        chosen = stable if limit is None else stable[:limit]
    return [release["version"] for release in chosen]


def build_key(project, mc, loader):
    return "%s|%s|%s" % (project, mc, loader)


def loader_release(snapshot, mc, loader):
    candidates = [release for release in snapshot.get(loader, [])
                  if release.get("minecraft") == mc and release.get("stable", True)]
    if not candidates:
        return None
    return max(candidates, key=lambda release: version_key(release.get("loaderVersion", "")))


def paper_build(snapshot, mc):
    for item in snapshot.get("paper", []):
        if item.get("minecraft") == mc and str(item.get("channel", "")).upper() == "STABLE":
            return item
    return None


def make_cell(cell_id, platform, mc, loader, adapter, jdk, bundles, baseline=None):
    return {
        "cell": cell_id,
        "platform": platform,
        "mc": mc,
        "loader": loader,
        "adapter": adapter,
        "jdk": jdk,
        "job-line": "default",
        "bundles": list(bundles),
        "baseline": list(baseline or []),
    }


def generate(tier, snapshot):
    config = TIERS[tier]
    builds = {build_key(item.get("project"), item.get("minecraft"), item.get("loader"))
              for item in snapshot.get("builds", [])}
    minecraft_releases = snapshot.get("minecraft", [])
    cells = []
    dropped = []

    for mc in select_versions(tier, minecraft_releases):
        jdk = jdk_for(mc, minecraft_releases)

        paper = paper_build(snapshot, mc)
        if config["plugin_line"]:
            paper_cell = make_cell("%s/%s" % (PAPER, mc), PAPER, mc, None, PAPER, jdk,
                                   config["plugin_bundles"])
            if paper is None:
                dropped.append({"cell": paper_cell, "reason": "PaperMC STABLE 通道无 %s 构建" % mc})
            else:
                cells.append(paper_cell)

        for loader in LOADERS:
            base = make_cell("%s/%s" % (loader, mc), loader, mc, None, loader, jdk,
                             config["mod_bundles"])
            release = loader_release(snapshot, mc, loader)
            if release is None:
                dropped.append({"cell": base, "reason": "%s 在 %s 无稳定加载器版本" % (loader, mc)})
                continue
            missing = next((project for project in BASELINE[loader]
                            if build_key(project, mc, loader) not in builds), None)
            if missing is not None:
                dropped.append({
                    "cell": base,
                    "reason": "基线模组 %s 在 (%s, %s) 无可用构建" % (missing, mc, loader),
                })
                continue
            loader_version = release.get("loaderVersion")
            cells.append(make_cell(
                "%s/%s/%s" % (loader, mc, loader_version),
                loader, mc, loader_version, loader, jdk,
                config["mod_bundles"], BASELINE[loader]))

    return {
        "tier": tier,
        "fail-fast": config["fail_fast"],
        "max-parallel": config["max_parallel"],
        "include": cells,
        "dropped": dropped,
    }


def yaml_fragment(payload):
    lines = [
        "strategy:",
        "  fail-fast: %s" % ("true" if payload["fail-fast"] else "false"),
        "  max-parallel: %d" % payload["max-parallel"],
    ]
    if not payload["include"]:
        lines.append("  matrix:")
        lines.append("    include: []")
        return "\n".join(lines) + "\n"
    lines.append("  matrix:")
    lines.append("    include:")
    for cell in payload["include"]:
        parts = ["cell: %s" % cell["cell"], "platform: %s" % cell["platform"],
                 'mc: "%s"' % cell["mc"]]
        if cell["loader"] is not None:
            parts.append('loader: "%s"' % cell["loader"])
        parts.extend([
            "adapter: %s" % cell["adapter"],
            "jdk: %d" % cell["jdk"],
            "job-line: %s" % cell["job-line"],
            "bundles: [%s]" % ", ".join(cell["bundles"]),
        ])
        lines.append("      - { %s }" % ", ".join(parts))
    return "\n".join(lines) + "\n"


def append_github_output(payload):
    path = os.environ.get("GITHUB_OUTPUT")
    if not path:
        return
    matrix = json.dumps(payload["include"], ensure_ascii=False, separators=(",", ":"))
    with open(path, "a", encoding="utf-8") as handle:
        handle.write("matrix=%s\n" % matrix)
        handle.write("cell_count=%d\n" % len(payload["include"]))
        handle.write("dropped_count=%d\n" % len(payload["dropped"]))


def main(argv=None):
    parser = argparse.ArgumentParser(description="生成 Laplace Sprite 生态兼容矩阵")
    parser.add_argument("--tier", choices=sorted(TIERS), default="pr",
                        help="门禁档位：pr / daily / weekly")
    parser.add_argument("--snapshot", default=DEFAULT_SNAPSHOT,
                        help="离线版本快照路径")
    parser.add_argument("--format", choices=("json", "yaml"), default="json",
                        help="输出格式；json 为矩阵定义，yaml 为 Actions include 片段")
    parser.add_argument("--github-output", action="store_true",
                        help="把 matrix 写入 $GITHUB_OUTPUT")
    parser.add_argument("--cell-file", default=None,
                        help="把完整格定义（含裁剪原因）落盘，供失败回溯")
    args = parser.parse_args(argv)

    with open(args.snapshot, "r", encoding="utf-8") as handle:
        snapshot = json.load(handle)

    payload = generate(args.tier, snapshot)

    if args.cell_file:
        directory = os.path.dirname(os.path.abspath(args.cell_file))
        if directory:
            os.makedirs(directory, exist_ok=True)
        with open(args.cell_file, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
            handle.write("\n")

    if args.github_output:
        append_github_output(payload)

    if args.format == "yaml":
        sys.stdout.write(yaml_fragment(payload))
    else:
        json.dump(payload, sys.stdout, ensure_ascii=False, indent=2)
        sys.stdout.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
