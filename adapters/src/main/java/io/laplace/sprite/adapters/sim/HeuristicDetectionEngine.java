package io.laplace.sprite.adapters.sim;

import io.laplace.sprite.core.engine.DetectionContext;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.FlagEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启发式检测引擎（4.4 进程内轨的兜底实现）。
 *
 * <p>判定只读归一化事件流本身，不看 {@code cheatSpec}/{@code tier}（档位仅用于 flag 标注）。
 * 每条规则都对着「原版可达状态」定阈值：优先抓物理上不可能的状态（位置突跳、超越原版攻击/破坏
 * 距离、单 tick 命中多个实体、落地却持续下落、站在流体表面、位置进入固体方块、放置方块时不看放置面、
 * 绕过进食计时），而不是抓「少见但合法」的状态（击退、搭桥、鞘翅滑翔、高速载具）。</p>
 *
 * <p>阈值都按 20 TPS 换算成「格/tick」：原版疾跑 5.612 格/秒 ≈ 0.28，步行 4.317 ≈ 0.215。</p>
 */
public final class HeuristicDetectionEngine implements DetectionEngine {

    public static final String CHECK_SPEED = "sim.speed";
    public static final String CHECK_FLY = "sim.fly";
    public static final String CHECK_REACH = "sim.reach";
    public static final String CHECK_KILL_AURA = "sim.kill-aura";
    public static final String CHECK_AUTO_CLICKER = "sim.auto-clicker";
    public static final String CHECK_TIMER = "sim.timer";
    public static final String CHECK_BLINK = "sim.blink";
    public static final String CHECK_NO_FALL = "sim.no-fall";
    public static final String CHECK_JESUS = "sim.jesus";
    public static final String CHECK_PHASE = "sim.phase";
    public static final String CHECK_SCAFFOLD = "sim.scaffold";
    public static final String CHECK_FAST_PLACE = "sim.fast-place";
    public static final String CHECK_FAST_EAT = "sim.fast-eat";
    public static final String CHECK_AUTO_TOTEM = "sim.auto-totem";
    public static final String CHECK_XRAY = "sim.xray";

    /**
     * 落地状态的合法水平位移上限（格/tick）：原版疾跑 0.28，助跑起跳的空中横向速度瞬时到 ~0.33，
     * 落地奔跑受摩擦限制不会超过 0.30。位移类作弊会把整段落地位移抬到 0.30 以上。
     */
    private static final double GROUND_SPEED_LIMIT = 0.30;
    /** 连续超限 tick 下限：单 tick 的合法冲量（末影珍珠冲刺、击退落地帧）不计入，持续超限才有意义。 */
    private static final int SPEED_MIN_TICKS = 8;
    /** 单 tick 水平位移上限（格）：本模拟里最大合法冲量是击退初速 1.2 格/tick，更高即位置突跳。 */
    private static final double BLINK_DISTANCE = 1.4;
    /** 离地上升的连续 tick 下限与累计爬升下限（格）。 */
    private static final int FLY_MIN_TICKS = 8;
    private static final double FLY_MIN_CLIMB = 1.0;
    /** 原版生存模式攻击距离上限（格）。 */
    private static final double REACH_LIMIT = 3.0;
    /** 单 tick 命中的不同实体数下限：原版一个 tick 只结算准星指向的一个实体（横扫只波及主目标邻近实体）。 */
    private static final int KILL_AURA_MIN_TARGETS = 3;
    /** 判定自动点击所需的最少连击样本、最小 CPS、以及间隔抖动下限（tick 标准差）。 */
    private static final int AUTO_CLICK_MIN_SAMPLES = 8;
    private static final int AUTO_CLICK_MIN_CPS = 12;
    private static final double AUTO_CLICK_JITTER = 0.5;
    /** 同一服务端 tick 重复出现位置包的 tick 数下限：服务端每 tick 只该收到一个位置包。 */
    private static final int TIMER_MIN_DUPLICATED_TICKS = 3;
    /** 落地却持续下落的连续 tick 下限与每 tick 下落下限（格）。 */
    private static final int NO_FALL_MIN_TICKS = 3;
    private static final double NO_FALL_FALL_DELTA = 0.1;
    /** 站在流体方块上（onGround 为真）出现次数下限。 */
    private static final int JESUS_MIN_TICKS = 2;
    /** 位置落入固体方块内部的次数下限：进入一次即物理不可能。 */
    private static final int PHASE_MIN_TICKS = 1;
    /** 脚手架：脚下有补块的移动 tick 占比下限，以及认定「低头看放置面」的俯仰角下限（度）。 */
    private static final double SCAFFOLD_RATIO = 0.8;
    private static final double SCAFFOLD_LOOK_DOWN_PITCH = 45.0;
    private static final int SCAFFOLD_MIN_TICKS = 10;
    /** 单 tick 放置次数上限：原版右键放置受约 4 tick 冷却约束，一 tick 多次放置不可能。 */
    private static final int FAST_PLACE_MIN_PER_TICK = 3;
    /** 进食耗时下限（tick）：原版食物 32 tick，最快干海带 16 tick，低于此即绕过消费计时。 */
    private static final int FAST_EAT_MIN_TICKS = 16;
    /** 受击到副手换入图腾的最大延迟（tick）：人类反应下界约 150–250ms，换手还需一次物品栏操作。 */
    private static final int AUTO_TOTEM_MAX_DELAY = 4;
    /** 可破坏方块的合法交互距离（格）：原版方块交互距离 4.5。 */
    private static final double XRAY_MAX_MINE_REACH = 6.0;

    @Override
    public List<FlagEvent> evaluate(DetectionContext ctx) {
        List<FlagEvent> flags = new ArrayList<>();
        EventStream stream = ctx.stream();
        if (stream == null || stream.isEmpty()) {
            return flags;
        }
        List<Event> events = stream.events();
        List<Event.Move> moves = moves(events);
        List<Event.Combat> combats = combats(events);
        List<Event.Interact> interacts = interacts(events);
        List<Event.Inventory> inventories = inventories(events);

        long blinkTick = firstBlinkTick(moves);
        if (blinkTick >= 0) {
            flags.add(flag(ctx, CHECK_BLINK, CheatType.BLINK, 0.95, blinkTick));
        }
        long speedTick = firstSustainedSpeedTick(moves);
        if (speedTick >= 0) {
            flags.add(flag(ctx, CHECK_SPEED, CheatType.SPEED, 0.9, speedTick));
        }
        long flyTick = firstFlyTick(moves);
        if (flyTick >= 0) {
            flags.add(flag(ctx, CHECK_FLY, CheatType.FLY, 0.9, flyTick));
        }
        long reachTick = firstReachTick(combats);
        if (reachTick >= 0) {
            flags.add(flag(ctx, CHECK_REACH, CheatType.REACH, 0.95, reachTick));
        }
        long killAuraTick = firstKillAuraTick(combats);
        if (killAuraTick >= 0) {
            flags.add(flag(ctx, CHECK_KILL_AURA, CheatType.KILL_AURA, 0.9, killAuraTick));
        }
        long autoClickTick = firstAutoClickTick(combats);
        if (autoClickTick >= 0) {
            flags.add(flag(ctx, CHECK_AUTO_CLICKER, CheatType.AUTO_CLICKER, 0.9, autoClickTick));
        }
        long timerTick = firstTimerTick(moves);
        if (timerTick >= 0) {
            flags.add(flag(ctx, CHECK_TIMER, CheatType.TIMER, 0.9, timerTick));
        }
        long noFallTick = firstNoFallTick(moves);
        if (noFallTick >= 0) {
            flags.add(flag(ctx, CHECK_NO_FALL, CheatType.NO_FALL, 0.9, noFallTick));
        }
        long jesusTick = firstJesusTick(moves, fluidChanges(events));
        if (jesusTick >= 0) {
            flags.add(flag(ctx, CHECK_JESUS, CheatType.JESUS, 0.9, jesusTick));
        }
        long phaseTick = firstPhaseTick(events);
        if (phaseTick >= 0) {
            flags.add(flag(ctx, CHECK_PHASE, CheatType.PHASE, 0.9, phaseTick));
        }
        long scaffoldTick = firstScaffoldTick(moves, interacts);
        if (scaffoldTick >= 0) {
            flags.add(flag(ctx, CHECK_SCAFFOLD, CheatType.SCAFFOLD, 0.85, scaffoldTick));
        }
        long fastPlaceTick = firstFastPlaceTick(interacts);
        if (fastPlaceTick >= 0) {
            flags.add(flag(ctx, CHECK_FAST_PLACE, CheatType.FAST_PLACE, 0.9, fastPlaceTick));
        }
        long fastEatTick = firstFastEatTick(interacts, inventories);
        if (fastEatTick >= 0) {
            flags.add(flag(ctx, CHECK_FAST_EAT, CheatType.FAST_EAT, 0.9, fastEatTick));
        }
        long autoTotemTick = firstAutoTotemTick(events);
        if (autoTotemTick >= 0) {
            flags.add(flag(ctx, CHECK_AUTO_TOTEM, CheatType.AUTO_TOTEM, 0.85, autoTotemTick));
        }
        long xrayTick = firstXrayTick(events);
        if (xrayTick >= 0) {
            flags.add(flag(ctx, CHECK_XRAY, CheatType.XRAY, 0.9, xrayTick));
        }
        return List.copyOf(flags);
    }

    private static List<Event.Move> moves(List<Event> events) {
        return events.stream().filter(Event.Move.class::isInstance).map(Event.Move.class::cast).toList();
    }

    private static List<Event.Combat> combats(List<Event> events) {
        return events.stream().filter(Event.Combat.class::isInstance).map(Event.Combat.class::cast).toList();
    }

    private static List<Event.Interact> interacts(List<Event> events) {
        return events.stream().filter(Event.Interact.class::isInstance).map(Event.Interact.class::cast)
                .toList();
    }

    private static List<Event.Inventory> inventories(List<Event> events) {
        return events.stream().filter(Event.Inventory.class::isInstance).map(Event.Inventory.class::cast)
                .toList();
    }

    /** 单 tick 位移超过 BLINK_DISTANCE 的首次出现。 */
    private static long firstBlinkTick(List<Event.Move> moves) {
        for (int i = 1; i < moves.size(); i++) {
            if (horizontal(moves.get(i - 1), moves.get(i)) > BLINK_DISTANCE) {
                return moves.get(i).tick();
            }
        }
        return -1;
    }

    /** 连续 SPEED_MIN_TICKS 个「落地且水平位移超限」的 tick；击退/珍珠的孤立冲量不会累计到下限。 */
    private static long firstSustainedSpeedTick(List<Event.Move> moves) {
        int run = 0;
        for (int i = 1; i < moves.size(); i++) {
            Event.Move current = moves.get(i);
            if (current.onGround() && horizontal(moves.get(i - 1), current) > GROUND_SPEED_LIMIT) {
                run++;
                if (run >= SPEED_MIN_TICKS) {
                    return moves.get(i - SPEED_MIN_TICKS + 1).tick();
                }
            } else {
                run = 0;
            }
        }
        return -1;
    }

    /** 连续离地上升达到 FLY_MIN_TICKS 且累计爬升不低于 FLY_MIN_CLIMB。 */
    private static long firstFlyTick(List<Event.Move> moves) {
        int run = 0;
        double climb = 0;
        for (int i = 1; i < moves.size(); i++) {
            Event.Move previous = moves.get(i - 1);
            Event.Move current = moves.get(i);
            double dy = current.y() - previous.y();
            if (!current.onGround() && dy > 0) {
                run++;
                climb += dy;
                if (run >= FLY_MIN_TICKS && climb >= FLY_MIN_CLIMB) {
                    return moves.get(i - FLY_MIN_TICKS + 1).tick();
                }
            } else {
                run = 0;
                climb = 0;
            }
        }
        return -1;
    }

    /** 攻击距离超过原版上限的首次出现。 */
    private static long firstReachTick(List<Event.Combat> combats) {
        for (Event.Combat combat : combats) {
            if (combat.reach() > REACH_LIMIT) {
                return combat.tick();
            }
        }
        return -1;
    }

    /** 单 tick 命中不同实体数达到下限的首次出现。 */
    private static long firstKillAuraTick(List<Event.Combat> combats) {
        Map<Long, Set<String>> targetsByTick = new LinkedHashMap<>();
        for (Event.Combat combat : combats) {
            targetsByTick.computeIfAbsent(combat.tick(), key -> new LinkedHashSet<>())
                    .add(combat.targetId());
        }
        for (Map.Entry<Long, Set<String>> entry : targetsByTick.entrySet()) {
            if (entry.getValue().size() >= KILL_AURA_MIN_TARGETS) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * 连击间隔方差接近零、每次上报 CPS 都不低于下限。人类点击间隔必有抖动，实测 CPS 也会波动；
     * 脚本连点恒定，且不会出现低 CPS 的样本。
     */
    private static long firstAutoClickTick(List<Event.Combat> combats) {
        if (combats.size() < AUTO_CLICK_MIN_SAMPLES) {
            return -1;
        }
        for (Event.Combat combat : combats) {
            if (combat.clicksPerSecond() < AUTO_CLICK_MIN_CPS) {
                return -1;
            }
        }
        List<Long> intervals = new ArrayList<>(combats.size() - 1);
        for (int i = 1; i < combats.size(); i++) {
            intervals.add(combats.get(i).tick() - combats.get(i - 1).tick());
        }
        double mean = intervals.stream().mapToLong(Long::longValue).average().orElse(0);
        double variance = intervals.stream()
                .mapToDouble(interval -> Math.pow(interval - mean, 2))
                .sum() / intervals.size();
        return Math.sqrt(variance) < AUTO_CLICK_JITTER ? combats.get(0).tick() : -1;
    }

    /** 同一服务端 tick 出现多个位置包的 tick 数达到下限。 */
    private static long firstTimerTick(List<Event.Move> moves) {
        Map<Long, Integer> countByTick = new LinkedHashMap<>();
        for (Event.Move move : moves) {
            countByTick.merge(move.tick(), 1, Integer::sum);
        }
        int duplicated = 0;
        long first = -1;
        for (Map.Entry<Long, Integer> entry : countByTick.entrySet()) {
            if (entry.getValue() > 1) {
                duplicated++;
                if (first < 0) {
                    first = entry.getKey();
                }
            }
        }
        return duplicated >= TIMER_MIN_DUPLICATED_TICKS ? first : -1;
    }

    /** 连续上报落地却每 tick 持续下落的首次出现。 */
    private static long firstNoFallTick(List<Event.Move> moves) {
        int run = 0;
        for (int i = 1; i < moves.size(); i++) {
            Event.Move previous = moves.get(i - 1);
            Event.Move current = moves.get(i);
            if (current.onGround() && current.y() - previous.y() <= -NO_FALL_FALL_DELTA) {
                run++;
                if (run >= NO_FALL_MIN_TICKS) {
                    return moves.get(i - NO_FALL_MIN_TICKS + 1).tick();
                }
            } else {
                run = 0;
            }
        }
        return -1;
    }

    /** 落地状态出现在流体方块表面的首次次数达标的 tick。 */
    private static long firstJesusTick(List<Event.Move> moves,
                                       Map<Long, List<Event.BlockChange>> fluidByTick) {
        int hits = 0;
        long first = -1;
        for (Event.Move move : moves) {
            if (!move.onGround()) {
                continue;
            }
            List<Event.BlockChange> changes = fluidByTick.get(move.tick());
            if (changes == null) {
                continue;
            }
            String feet = key((int) Math.floor(move.x()), (int) Math.floor(move.y()),
                    (int) Math.floor(move.z()));
            for (Event.BlockChange change : changes) {
                if (feet.equals(key(change.blockX(), change.blockY(), change.blockZ()))) {
                    hits++;
                    if (first < 0) {
                        first = move.tick();
                    }
                    break;
                }
            }
        }
        return hits >= JESUS_MIN_TICKS ? first : -1;
    }

    /** 顺序回放方块变化，玩家坐标落入固体方块内部的首次 tick。 */
    private static long firstPhaseTick(List<Event> events) {
        Set<String> solid = new HashSet<>();
        Set<String> fluid = new HashSet<>();
        int hits = 0;
        long first = -1;
        for (Event event : events) {
            if (event instanceof Event.BlockChange change) {
                String blockKey = key(change.blockX(), change.blockY(), change.blockZ());
                String to = change.to();
                if (isAir(to)) {
                    solid.remove(blockKey);
                    fluid.remove(blockKey);
                } else if (isFluid(to)) {
                    solid.remove(blockKey);
                    fluid.add(blockKey);
                } else {
                    fluid.remove(blockKey);
                    solid.add(blockKey);
                }
            } else if (event instanceof Event.Move move) {
                String feet = key((int) Math.floor(move.x()), (int) Math.floor(move.y()),
                        (int) Math.floor(move.z()));
                if (solid.contains(feet)) {
                    hits++;
                    if (first < 0) {
                        first = move.tick();
                    }
                }
            }
        }
        return hits >= PHASE_MIN_TICKS ? first : -1;
    }

    /**
     * 脚手架：移动过程中脚下持续被补块，但整体视角并未低头看放置面。
     * 原版放置方块需要准星命中放置面，把脚下当目标必然低头（俯仰角接近 +90）；取平均俯仰角判断，
     * 避免合法玩家长时间搭桥时逐 tick 的视角抖动误伤。
     */
    private static long firstScaffoldTick(List<Event.Move> moves, List<Event.Interact> interacts) {
        if (moves.size() < SCAFFOLD_MIN_TICKS) {
            return -1;
        }
        Set<String> placed = new HashSet<>();
        for (Event.Interact interact : interacts) {
            if ("place".equals(interact.action())) {
                placed.add(key(interact.blockX(), interact.blockY(), interact.blockZ()));
            }
        }
        if (placed.isEmpty()) {
            return -1;
        }
        int covered = 0;
        double pitchSum = 0;
        long first = -1;
        for (Event.Move move : moves) {
            int x = (int) Math.floor(move.x());
            int y = (int) Math.floor(move.y()) - 1;
            int z = (int) Math.floor(move.z());
            if (!placed.contains(key(x, y, z))) {
                continue;
            }
            covered++;
            pitchSum += move.pitch();
            if (first < 0) {
                first = move.tick();
            }
        }
        if (covered < SCAFFOLD_MIN_TICKS || covered < SCAFFOLD_RATIO * moves.size()) {
            return -1;
        }
        return pitchSum / covered < SCAFFOLD_LOOK_DOWN_PITCH ? first : -1;
    }

    /** 单 tick 放置次数达到下限的首次出现。 */
    private static long firstFastPlaceTick(List<Event.Interact> interacts) {
        Map<Long, Integer> countByTick = new LinkedHashMap<>();
        for (Event.Interact interact : interacts) {
            if ("place".equals(interact.action())) {
                countByTick.merge(interact.tick(), 1, Integer::sum);
            }
        }
        for (Map.Entry<Long, Integer> entry : countByTick.entrySet()) {
            if (entry.getValue() >= FAST_PLACE_MIN_PER_TICK) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /** 「使用」交互到物品变化的间隔低于原版进食耗时的首次出现。 */
    private static long firstFastEatTick(List<Event.Interact> interacts, List<Event.Inventory> inventories) {
        for (Event.Interact interact : interacts) {
            if (!"use".equals(interact.action())) {
                continue;
            }
            Event.Inventory consumed = firstInventoryAfter(inventories, interact.tick());
            if (consumed != null && consumed.tick() - interact.tick() < FAST_EAT_MIN_TICKS) {
                return interact.tick();
            }
        }
        return -1;
    }

    private static Event.Inventory firstInventoryAfter(List<Event.Inventory> inventories, long tick) {
        for (Event.Inventory inventory : inventories) {
            if (inventory.tick() >= tick) {
                return inventory;
            }
        }
        return null;
    }

    /** 受击后极短时间内副手换入图腾的首次出现。 */
    private static long firstAutoTotemTick(List<Event> events) {
        long lastCombat = Long.MIN_VALUE;
        for (Event event : events) {
            if (event instanceof Event.Combat combat) {
                lastCombat = combat.tick();
            } else if (event instanceof Event.Inventory inventory
                    && inventory.slot() != null && inventory.slot().contains("offhand")
                    && inventory.itemId() != null && inventory.itemId().contains("totem")
                    && lastCombat != Long.MIN_VALUE
                    && inventory.tick() - lastCombat <= AUTO_TOTEM_MAX_DELAY) {
                return inventory.tick();
            }
        }
        return -1;
    }

    /** 破坏/挖掘的方块中心超出原版交互距离的首次出现。 */
    private static long firstXrayTick(List<Event> events) {
        boolean hasPosition = false;
        double px = 0;
        double py = 0;
        double pz = 0;
        for (Event event : events) {
            if (event instanceof Event.Move move) {
                px = move.x();
                py = move.y();
                pz = move.z();
                hasPosition = true;
            } else if (hasPosition && event instanceof Event.Interact interact
                    && ("mine".equals(interact.action()) || "break".equals(interact.action()))) {
                double dx = interact.blockX() + 0.5 - px;
                double dy = interact.blockY() + 0.5 - py;
                double dz = interact.blockZ() + 0.5 - pz;
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) > XRAY_MAX_MINE_REACH) {
                    return interact.tick();
                }
            }
        }
        return -1;
    }

    /** 按 tick 归集「变成流体方块」的方块变化。 */
    private static Map<Long, List<Event.BlockChange>> fluidChanges(List<Event> events) {
        Map<Long, List<Event.BlockChange>> byTick = new HashMap<>();
        for (Event event : events) {
            if (event instanceof Event.BlockChange change && isFluid(change.to())) {
                byTick.computeIfAbsent(change.tick(), key -> new ArrayList<>()).add(change);
            }
        }
        return byTick;
    }

    private static boolean isAir(String block) {
        return block == null || block.endsWith("air");
    }

    private static boolean isFluid(String block) {
        return block != null && (block.contains("water") || block.contains("lava"));
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static double horizontal(Event.Move from, Event.Move to) {
        return Math.hypot(to.x() - from.x(), to.z() - from.z());
    }

    private static FlagEvent flag(DetectionContext ctx, String check, CheatType type, double confidence,
                                  long tick) {
        String runId = ctx.runId() == null ? "sim-run" : ctx.runId();
        String playerId = ctx.playerId() == null ? "SimID-sim" : ctx.playerId();
        String trajectoryRef = ctx.scenario() == null ? "sim" : ctx.scenario().id();
        SampleTierName tier = ctx.tier();
        return FlagEvent.truePositive(runId, playerId, check, type, tier, confidence, tick, trajectoryRef,
                "sim-heuristic");
    }
}
