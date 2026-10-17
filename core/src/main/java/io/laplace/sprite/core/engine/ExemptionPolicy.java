package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.ExemptionRule;
import io.laplace.sprite.core.model.GameMode;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.model.SessionContext;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 误报豁免策略（4.1 豁免规则）。
 *
 * <p>口径：被豁免的 flag 仍然记录进证据链，但不计入 {@code MetricSet.falsePositives}，
 * 因此不影响误报率门禁。豁免项全部可配置，默认取自受测插件的 {@link ServerPluginConfig}。</p>
 *
 * @param enabled        生效的豁免规则
 * @param joinGraceTicks 开局缓冲期长度（tick）；仅对 {@link ExemptionRule#JOIN_GRACE} 生效
 */
public record ExemptionPolicy(Set<ExemptionRule> enabled, int joinGraceTicks) {

    /** 开局缓冲期默认 100 tick（5 秒）：足以覆盖登录后的区块下发与传送落地。 */
    public static final int DEFAULT_JOIN_GRACE_TICKS = 100;

    /**
     * 插件传送 / 击退的影响窗口：位移产生的 flag 可能落在事件之后的若干 tick，
     * 取 20 tick（1 秒）覆盖下落与惯性，避免漏豁免。
     */
    public static final int PLUGIN_EFFECT_WINDOW_TICKS = 20;

    public ExemptionPolicy {
        enabled = enabled == null ? Set.of() : Set.copyOf(enabled);
        if (joinGraceTicks < 0) {
            throw new IllegalArgumentException("开局缓冲期不能为负: " + joinGraceTicks);
        }
    }

    /** 全部关闭：所有 flag 都按真实结果计入。 */
    public static ExemptionPolicy none() {
        return new ExemptionPolicy(Set.of(), DEFAULT_JOIN_GRACE_TICKS);
    }

    /** 默认策略：与受测插件的安全默认配置一致。 */
    public static ExemptionPolicy defaults() {
        return from(ServerPluginConfig.safeDefaults());
    }

    /** 从插件配置映射；未知取值忽略（配置拼错时退化为「不放宽」）。 */
    public static ExemptionPolicy from(ServerPluginConfig config) {
        return from(config, DEFAULT_JOIN_GRACE_TICKS);
    }

    public static ExemptionPolicy from(ServerPluginConfig config, int joinGraceTicks) {
        if (config == null) {
            return none();
        }
        Set<ExemptionRule> rules = new LinkedHashSet<>();
        for (String raw : config.exemptions()) {
            ExemptionRule rule = ExemptionRule.parse(raw);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return new ExemptionPolicy(rules, joinGraceTicks);
    }

    /** 从逗号分隔的规则名构造，供命令行直接传入。 */
    public static ExemptionPolicy parse(String csv, int joinGraceTicks) {
        Set<ExemptionRule> rules = new LinkedHashSet<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                ExemptionRule rule = ExemptionRule.parse(part);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        return new ExemptionPolicy(rules, joinGraceTicks);
    }

    /**
     * 判定某个 flag 是否被豁免。按固定顺序求值，保证同一输入结果确定。
     */
    public Optional<ExemptionRule> exemptionFor(SimulatedSession session, long flagTick) {
        if (enabled.isEmpty() || session == null) {
            return Optional.empty();
        }
        SessionContext ctx = session.context();

        if (enabled.contains(ExemptionRule.CREATIVE) && ctx.gameMode() == GameMode.CREATIVE) {
            return Optional.of(ExemptionRule.CREATIVE);
        }
        if (enabled.contains(ExemptionRule.SPECTATOR) && ctx.gameMode() == GameMode.SPECTATOR) {
            return Optional.of(ExemptionRule.SPECTATOR);
        }
        if (enabled.contains(ExemptionRule.ALLOW_FLIGHT) && ctx.allowFlight()) {
            return Optional.of(ExemptionRule.ALLOW_FLIGHT);
        }
        if (enabled.contains(ExemptionRule.JOIN_GRACE) && ctx.ticksSinceJoin() <= joinGraceTicks) {
            return Optional.of(ExemptionRule.JOIN_GRACE);
        }
        if (enabled.contains(ExemptionRule.PLUGIN_TELEPORT)
                && withinWindow(ctx.pluginTeleportTicks(), flagTick)) {
            return Optional.of(ExemptionRule.PLUGIN_TELEPORT);
        }
        if (enabled.contains(ExemptionRule.PLUGIN_KNOCKBACK)
                && withinWindow(ctx.pluginKnockbackTicks(), flagTick)) {
            return Optional.of(ExemptionRule.PLUGIN_KNOCKBACK);
        }
        return Optional.empty();
    }

    public boolean exempts(SimulatedSession session, long flagTick) {
        return exemptionFor(session, flagTick).isPresent();
    }

    private static boolean withinWindow(Set<Long> ticks, long flagTick) {
        for (long tick : ticks) {
            if (flagTick >= tick && flagTick - tick <= PLUGIN_EFFECT_WINDOW_TICKS) {
                return true;
            }
        }
        return false;
    }
}
