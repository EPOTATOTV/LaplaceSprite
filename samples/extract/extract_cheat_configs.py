#!/usr/bin/env python3
"""从开源作弊客户端的源码里提取参数，按「检测阈值 × 档位系数」生成 CheatSpec 草案。

用法::

    python extract_cheat_configs.py --client Wurst --source D:\\tmp\\Wurst7 --out samples\\out
    python extract_cheat_configs.py --client Meteor --source D:\\tmp\\meteor-client --merge

只读公开源码，不接触任何玩家数据（对应设计文档 6. 数据边界）。

v1.1.0 起口径变化：开源客户端名只作为**出处标签**（`source` 字段），不再是数值来源；
每档的注入幅度由 `锚点 × 档位系数` 派生（见 samples/mapping/thresholds.yaml 与
cheats 模块的 DetectionThresholdAnchor / TierCoefficient）。

脚本抽出的数值只用于两件事：
1. 判断该客户端是否真的存在这个模块（决定要不要为它生成一条样本）；
2. 给辅助参数（点击分布、超时、比例等）提供参考值。

主参数不从这里取——它必须等于锚点 × 档位系数，否则 SampleLibraryValidator 会判不合格。
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

try:
    import yaml
except ImportError:  # pragma: no cover
    yaml = None

SOURCE_SUFFIXES = {".java", ".kt", ".kts", ".json", ".toml"}

# 各客户端设置 API 的取值形态。field 名 = CheatType，value = 正则列表（组 1 名称，组 2 数值/布尔）
SETTING_PATTERNS: dict[str, list[re.Pattern[str]]] = {
    "wurst": [
        re.compile(r'new\s+SliderSetting\(\s*"([^"]+)"\s*,\s*([0-9.]+)'),
        re.compile(r'new\s+CheckboxSetting\(\s*"([^"]+)"\s*,\s*(true|false)'),
    ],
    "liquidbounce": [
        re.compile(r'floatValue\(\s*"([^"]+)"\s*,\s*([0-9.]+)'),
        re.compile(r'booleanValue\(\s*"([^"]+)"\s*,\s*(true|false)'),
        re.compile(r'choiceValue\(\s*"([^"]+)"\s*,\s*"([^"]+)"'),
    ],
    "meteor": [
        re.compile(r'SGuiDouble\(\s*"([^"]+)"\s*,\s*([0-9.]+)'),
        re.compile(r'SGuiInt\(\s*"([^"]+)"\s*,\s*([0-9]+)'),
        re.compile(r'SGuiBool\(\s*"([^"]+)"\s*,\s*(true|false)'),
    ],
    "generic": [
        re.compile(r'"([A-Za-z][A-Za-z0-9 _]+)"\s*[:=]\s*([0-9]*\.?[0-9]+)'),
        re.compile(r'"([A-Za-z][A-Za-z0-9 _]+)"\s*[:=]\s*(true|false)'),
    ],
}

DEFAULT_CLIENTS = ["wurst", "liquidbounce", "meteor", "generic"]


@dataclass
class ModuleHit:
    module: str
    cheat_type: str
    settings: dict[str, object] = field(default_factory=dict)
    files: set[str] = field(default_factory=set)


def load_mapping(path: Path) -> dict[str, str]:
    lower: dict[str, str] = {}
    if not path.is_file():
        return lower
    if yaml is None:
        raise SystemExit("需要 PyYAML：pip install pyyaml")
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    for cheat_type, names in data.items():
        for name in names or []:
            lower[str(name).lower()] = cheat_type
    return lower


def classify(stem: str, mapping: dict[str, str]) -> str | None:
    key = stem.replace("_", "").replace("-", "").lower()
    for name, cheat_type in mapping.items():
        if name.replace("_", "").replace("-", "").lower() == key:
            return cheat_type
    for name, cheat_type in mapping.items():
        if name.lower() in stem.lower():
            return cheat_type
    return None


def scan(start: Path, client: str, mapping: dict[str, str]) -> list[ModuleHit]:
    patterns = SETTING_PATTERNS.get(client, SETTING_PATTERNS["generic"])
    hits: dict[str, ModuleHit] = {}
    for path in sorted(start.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in SOURCE_SUFFIXES:
            continue
        cheat_type = classify(path.stem, mapping)
        if cheat_type is None:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        hit = hits.setdefault(path.stem, ModuleHit(path.stem, cheat_type))
        for pattern in patterns:
            for name, value in pattern.findall(text):
                hit.settings.setdefault(name, coerce(value))
        hit.files.add(str(path))

    for path in sorted(start.rglob("*.json")):
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        module_name = path.parent.name or path.stem
        cheat_type = classify(module_name, mapping)
        if cheat_type is None:
            continue
        hit = hits.setdefault(module_name, ModuleHit(module_name, cheat_type))
        for name, value in SETTING_PATTERNS["generic"][0].findall(text):
            hit.settings.setdefault(name, coerce(value))
        hit.files.add(str(path))

    return list(hits.values())


def coerce(value: str) -> object:
    if value in ("true", "false"):
        return value == "true"
    try:
        number = float(value)
        return int(number) if number.is_integer() else number
    except ValueError:
        return value


def load_thresholds(path: Path) -> tuple[dict[str, dict], dict[str, float]]:
    """读取锚点与档位系数镜像；缺文件时返回空表，由调用方明确失败。"""
    if not path.is_file():
        return {}, {}
    if yaml is None:
        raise SystemExit("需要 PyYAML：pip install pyyaml")
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    return data.get("anchors", {}) or {}, data.get("coefficients", {}) or {}


def to_spec(hit: ModuleHit, client: str, anchors: dict[str, dict],
            coefficients: dict[str, float]) -> dict:
    anchor = anchors.get(hit.cheat_type)
    if not anchor:
        raise SystemExit(f"{hit.cheat_type} 在 thresholds.yaml 里没有锚点，先补锚点再提取")

    anchor_value = float(anchor["value"])
    param_key = anchor["param"]
    direction = anchor.get("direction", "higher")

    tiers = []
    for tier_name, coefficient in coefficients.items():
        magnitude = anchor_value * coefficient
        tier = {"tier": tier_name, "params": {param_key: round(magnitude, 4)}}
        if tier_name == "MIN":
            tier["expected"] = {
                "expectedDetected": True, "minConfidence": 0.0, "boundary": True,
                "note": "阈值边界档：刚过阈值，允许贴线检出",
            }
        else:
            tier["expected"] = {
                "expectedDetected": True, "minConfidence": 0.9 if tier_name != "HIDDEN" else 0.6,
                "boundary": False, "note": None,
            }
        tiers.append(tier)

    auxiliary = {k: v for k, v in sorted(hit.settings.items()) if k != param_key}
    return {
        "id": f"{hit.cheat_type.lower()}.{client.lower()}.draft",
        "name": f"{hit.module}（{client} 提取草案）",
        "type": hit.cheat_type,
        "target": "detection-engine",
        "source": client,
        "injector": hit.cheat_type.lower(),
        "notes": (
            f"注入幅度 = 阈值锚点 {anchor_value}（{anchor.get('unit', '')}，{direction}）"
            f" × 档位系数；{client} 仅作出处标签。来源文件 {len(hit.files)} 个，"
            f"辅助参数 {auxiliary or '无'} 需人工复核。"
        ),
        "tiers": tiers,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="从开源作弊客户端源码提取 CheatSpec 草案")
    parser.add_argument("--client", required=True, help="客户端名（Wurst/LiquidBounce/Meteor/...）")
    parser.add_argument("--source", required=True, type=Path, help="该客户端的源码目录")
    base = Path(__file__).resolve().parents[1] / "mapping"
    parser.add_argument("--mapping", type=Path, default=base / "modules.yaml")
    parser.add_argument("--thresholds", type=Path, default=base / "thresholds.yaml",
                        help="锚点与档位系数镜像")
    parser.add_argument("--out", type=Path, help="输出目录；省略则打印到标准输出")
    args = parser.parse_args()

    if not args.source.is_dir():
        print(f"源码目录不存在: {args.source}", file=sys.stderr)
        return 2

    mapping = load_mapping(args.mapping)
    if not mapping:
        print(f"未读到模块名映射: {args.mapping}", file=sys.stderr)
        return 2

    anchors, coefficients = load_thresholds(args.thresholds)
    if not anchors or not coefficients:
        print(f"未读到锚点或档位系数: {args.thresholds}", file=sys.stderr)
        return 2

    hits = scan(args.source, args.client.lower(), mapping)
    if not hits:
        print("未命中任何已知模块，检查 --client 与源码目录", file=sys.stderr)
        return 1

    specs = [to_spec(hit, args.client, anchors, coefficients) for hit in hits]
    payload = {"client": args.client, "specs": specs}

    if yaml is None:
        print("需要 PyYAML：pip install pyyaml", file=sys.stderr)
        return 2

    text = dump_yaml(payload)
    if args.out:
        args.out.mkdir(parents=True, exist_ok=True)
        target = args.out / f"{args.client.lower()}.draft.yaml"
        target.write_text(text, encoding="utf-8")
        print(f"已写出 {target}（{len(specs)} 条草案）")
    else:
        print(text)
    return 0


def dump_yaml(payload: dict) -> str:
    """输出不带 YAML 锚点的文本：草案要给人读和手工改，锚点反而碍事。"""

    class _Dumper(yaml.SafeDumper):
        def ignore_aliases(self, data):
            return True

    return yaml.dump(payload, Dumper=_Dumper, allow_unicode=True, sort_keys=False,
                     default_flow_style=False)


if __name__ == "__main__":
    raise SystemExit(main())
