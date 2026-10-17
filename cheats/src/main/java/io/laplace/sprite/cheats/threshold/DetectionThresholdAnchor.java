package io.laplace.sprite.cheats.threshold;

import io.laplace.sprite.core.model.CheatType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 检测阈值锚点（v1.1.0 §4.2）：每种作弊类型在目标检测引擎里的判定阈值，是样本注入幅度的基准。
 *
 * <p>锚点值对齐 {@code HeuristicDetectionEngine} 的命名阈值常量，并记录注入器实际读取的主参数键、
 * 引擎 check 名与物理量纲。生成样本时按「注入幅度 = 锚点 × 档位系数」派生，见 {@link TierCoefficient}。
 * 开源客户端名只作为样本的 {@code source} 出处标签，不再充当数值来源。</p>
 *
 * <p>{@link #covered()} 覆盖全部 15 种 {@link CheatType}；当前引擎对每种类型都有对应 check，
 * 故 {@code undetectable} 全部为 false。若某类型没有对应 check，应据实标为不可检出并写入绕过清单，
 * 而不是硬凑一个阈值。</p>
 *
 * @param type         作弊类型
 * @param value        引擎的判定阈值（对齐下方 note 里指名的常量）
 * @param paramKey     注入器实际读取的主参数键（{@code SampleTier.params} 的键）
 * @param checkName    引擎 check 名（{@code HeuristicDetectionEngine.CHECK_*}）
 * @param unit         物理量纲与比较方向
 * @param undetectable 引擎当前无对应 check（不可检出）
 * @param note         中文依据：指名的引擎常量与物理含义
 */
public record DetectionThresholdAnchor(
        CheatType type,
        double value,
        String paramKey,
        String checkName,
        String unit,
        boolean undetectable,
        String note) {

    private static final Map<CheatType, DetectionThresholdAnchor> ANCHORS = buildAnchors();

    public static DetectionThresholdAnchor of(CheatType type) {
        DetectionThresholdAnchor anchor = ANCHORS.get(type);
        if (anchor == null) {
            throw new IllegalArgumentException("未登记阈值锚点: " + type);
        }
        return anchor;
    }

    /** 已登记锚点的全部作弊类型。 */
    public static Set<CheatType> covered() {
        return Collections.unmodifiableSet(ANCHORS.keySet());
    }

    private static Map<CheatType, DetectionThresholdAnchor> buildAnchors() {
        List<DetectionThresholdAnchor> anchors = List.of(
                // GROUND_SPEED_LIMIT=0.30：注入器 speed 是位移倍率，系数把该阈值刻度换算到倍率刻度
                new DetectionThresholdAnchor(CheatType.SPEED, 0.30, "speed", "sim.speed",
                        "格每 tick（水平位移）；越大越可疑",
                        false,
                        "GROUND_SPEED_LIMIT=0.30：原版疾跑 0.28 格/tick，落地奔跑位移上限 0.30，"
                                + "超过即判位移类作弊。注入器按基线位移倍率放大，系数把阈值刻度换算到倍率刻度。"),
                // FLY_MIN_CLIMB / FLY_MIN_TICKS = 1.0 / 8
                new DetectionThresholdAnchor(CheatType.FLY, 0.125, "verticalSpeed", "sim.fly",
                        "格每 tick（上升）；越大越可疑",
                        false,
                        "FLY_MIN_CLIMB / FLY_MIN_TICKS = 1.0 / 8 = 0.125：连续 8 tick 离地上升且累计爬升"
                                + "不低于 1.0 格，即平均逐 tick 上升不低于 0.125 格/tick。"),
                new DetectionThresholdAnchor(CheatType.REACH, 3.0, "reach", "sim.reach",
                        "格（攻击距离）；越大越可疑",
                        false,
                        "REACH_LIMIT=3.0：原版生存模式攻击距离上限 3.0 格。"),
                new DetectionThresholdAnchor(CheatType.KILL_AURA, 3.0, "targets", "sim.kill-aura",
                        "个（单 tick 命中不同实体数）；越多越可疑",
                        false,
                        "KILL_AURA_MIN_TARGETS=3：原版一个 tick 只结算准星指向的一个实体，"
                                + "单 tick 命中 3 个及以上不同实体即多目标。"),
                new DetectionThresholdAnchor(CheatType.AUTO_CLICKER, 12.0, "cps", "sim.auto-clicker",
                        "次每秒（CPS）；越大越可疑",
                        false,
                        "AUTO_CLICK_MIN_CPS=12：人类连点 CPS 上限约 12，脚本恒定连点且间隔无抖动。"),
                new DetectionThresholdAnchor(CheatType.TIMER, 3.0, "speedup", "sim.timer",
                        "次（同一 tick 重复位置包下限）→ 注入器按计时倍率复制；越大越可疑",
                        false,
                        "TIMER_MIN_DUPLICATED_TICKS=3：服务端每 tick 只该收到一个位置包，重复 3 次及以上"
                                + "即计时加速。注入器参数是计时倍率，系数把计数刻度换算成倍率刻度。"),
                new DetectionThresholdAnchor(CheatType.BLINK, 1.4, "distance", "sim.blink",
                        "格（单 tick 水平突跳）；越大越可疑",
                        false,
                        "BLINK_DISTANCE=1.4：最大合法冲量（击退初速）约 1.2 格/tick，"
                                + "单 tick 水平突跳超过 1.4 格即位置不可能。"),
                new DetectionThresholdAnchor(CheatType.NO_FALL, 0.1, "fallSpeed", "sim.no-fall",
                        "格每 tick（下落）；越大越可疑（引擎判 dy ≤ -0.1）",
                        false,
                        "NO_FALL_FALL_DELTA=0.1：上报落地却每 tick 下落不低于 0.1 格且连续 3 tick，"
                                + "即伪造落地。"),
                new DetectionThresholdAnchor(CheatType.PHASE, 1.0, "depth", "sim.phase",
                        "层（穿入实体方块层数）；达到 1 即可疑",
                        false,
                        "PHASE_MIN_TICKS=1：玩家坐标落入固体方块内部一次即物理不可能。"),
                new DetectionThresholdAnchor(CheatType.JESUS, 2.0, "surfaceY", "sim.jesus",
                        "次（流体表面落地 tick 下限）→ 注入器参数为水面高度（格）",
                        false,
                        "JESUS_MIN_TICKS=2：站在流体表面却报落地 2 次及以上即异常。水面高度本身无阈值量纲，"
                                + "注入器参数取游泳基线液面附近，系数把计数刻度换算成高度刻度。"),
                new DetectionThresholdAnchor(CheatType.SCAFFOLD, 0.8, "blocksPerTick", "sim.scaffold",
                        "占比（脚下补块移动 tick 占比下限）→ 注入器参数为每 tick 补块数",
                        false,
                        "SCAFFOLD_RATIO=0.8：脚下持续补块（占比不低于 0.8）但平均俯仰角未低头（低于 45°）"
                                + "即脚手架。注入器参数是每 tick 补块数，系数把占比刻度换算成计数刻度。"),
                new DetectionThresholdAnchor(CheatType.FAST_PLACE, 3.0, "placementsPerTick", "sim.fast-place",
                        "次（单 tick 放置次数）；越多越可疑",
                        false,
                        "FAST_PLACE_MIN_PER_TICK=3：原版右键放置受约 4 tick 冷却约束，"
                                + "单 tick 放置 3 次及以上不可能。"),
                new DetectionThresholdAnchor(CheatType.FAST_EAT, 16.0, "consumeTicks", "sim.fast-eat",
                        "tick（进食耗时）；越小越可疑（引擎判 <16）",
                        false,
                        "FAST_EAT_MIN_TICKS=16：原版最快干海带 16 tick，进食耗时低于此即绕过消费计时。"),
                new DetectionThresholdAnchor(CheatType.AUTO_TOTEM, 4.0, "swapDelayTicks", "sim.auto-totem",
                        "tick（换手延迟）；越小越可疑（引擎判 ≤4）",
                        false,
                        "AUTO_TOTEM_MAX_DELAY=4：人类反应下界约 150–250ms（3–5 tick），"
                                + "受击到换手不超过 4 tick 超出人类极限。"),
                new DetectionThresholdAnchor(CheatType.XRAY, 6.0, "range", "sim.xray",
                        "格（破坏距离）；越大越可疑",
                        false,
                        "XRAY_MAX_MINE_REACH=6.0：原版方块交互距离 4.5，破坏中心超出 6.0 格即不可达。"));
        Map<CheatType, DetectionThresholdAnchor> map = new EnumMap<>(CheatType.class);
        for (DetectionThresholdAnchor anchor : anchors) {
            if (map.put(anchor.type(), anchor) != null) {
                throw new IllegalStateException("阈值锚点重复登记: " + anchor.type());
            }
        }
        return map;
    }
}
