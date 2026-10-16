package io.laplace.sprite.hotreload;

import java.util.List;

/**
 * 重载能力判定（15.2 开发期热更新轨）：给一对（JVM 类型，改动类型），判能否热生效、要补什么参数。
 */
public final class HotReloadCapability {

    public static final String RESTART_REQUIRED = "需要重启";

    public static final String SPONGE_MIXIN_AGENT = "-javaagent:sponge-mixin.jar";

    /**
     * 判定结论。
     *
     * @param hotSwappable    能否热生效
     * @param requiresRestart 是否需要重启
     * @param recommendation  人话建议
     * @param agentArgs       需要的 agent / JVM 参数
     */
    public record Decision(boolean hotSwappable, boolean requiresRestart, String recommendation,
                           List<String> agentArgs) {

        public Decision {
            agentArgs = agentArgs == null ? List.of() : List.copyOf(agentArgs);
        }
    }

    public Decision decide(JvmKind jvm, ChangeKind kind) {
        return switch (kind) {
            case MODS_DIR, PLUGIN_JAR -> new Decision(false, true,
                    "mods/plugins 目录变更强制冷重启（发布前冷启动轨）", List.of());
            case RESOURCE_ASSETS -> new Decision(true, false,
                    "客户端资源变更走 F3+T 等价重载", List.of());
            case RESOURCE_DATA -> new Decision(true, false,
                    "数据包变更走原版 /reload（minecraft:reload），不是 Bukkit 插件 reload", List.of());
            case MIXIN -> new Decision(false, true,
                    "Mixin 热交换需 " + SPONGE_MIXIN_AGENT + "；未装载该 agent 时按需要重启处理",
                    List.of(SPONGE_MIXIN_AGENT));
            case METHOD_BODY -> methodBody(jvm);
            case METHOD_SIGNATURE, FIELD -> structural(jvm);
        };
    }

    public HotReloadReport evaluate(String changeId, JvmKind jvm, ChangeKind kind) {
        if (changeId == null || changeId.isBlank()) {
            throw new IllegalArgumentException("改动 id 不能为空");
        }
        Decision decision = decide(jvm, kind);
        return new HotReloadReport(changeId, kind, jvm, decision.hotSwappable(), decision.recommendation(),
                decision.requiresRestart(), decision.agentArgs(), "", false);
    }

    private static Decision methodBody(JvmKind jvm) {
        if (jvm == JvmKind.UNKNOWN) {
            return unknown();
        }
        return new Decision(true, false, "方法体改动可直接 HotSwap（标准 HotSpot 仅支持改方法体）", List.of());
    }

    private static Decision structural(JvmKind jvm) {
        if (jvm.supportsEnhancedRedefinition()) {
            return new Decision(true, false,
                    "增强类重定义可热生效，需 " + JvmProbe.ENHANCED_REDEFINITION_FLAG,
                    List.of(JvmProbe.ENHANCED_REDEFINITION_FLAG));
        }
        if (jvm == JvmKind.UNKNOWN) {
            return unknown();
        }
        return new Decision(false, true, "标准 HotSpot 不支持增删方法/字段，改签名或字段需要重启", List.of());
    }

    private static Decision unknown() {
        return new Decision(false, true, "JVM 类型未知，一律按需要重启处理", List.of());
    }
}
