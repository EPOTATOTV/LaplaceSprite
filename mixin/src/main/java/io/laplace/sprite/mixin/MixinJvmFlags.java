package io.laplace.sprite.mixin;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin 启动参数（15.11 P0）。
 *
 * <p>CI 启动实例固定带 {@code -Dmixin.debug.countInjections=true}：所有 {@code require()/expect()}
 * 声明的注入点数量都会在启动期核对，数量不符即抛 {@code InvalidInjectionException} 让实例起不来。
 * 同时保留 {@code -Dmixin.debug.export=true}，把织入后的 class 落盘供跨版本字节码 diff。</p>
 *
 * @param countInjections 是否开启注入计数
 * @param export          是否导出织入后的类
 * @param agentJar        sponge-mixin jar 路径；为空表示由加载器自带，不追加 javaagent
 */
public record MixinJvmFlags(boolean countInjections, boolean export, String agentJar) {

    public static final String COUNT_INJECTIONS_FLAG = "-Dmixin.debug.countInjections=true";

    public static final String EXPORT_FLAG = "-Dmixin.debug.export=true";

    public static final String JAVA_AGENT_PREFIX = "-javaagent:";

    public MixinJvmFlags {
        agentJar = agentJar == null || agentJar.isBlank() ? null : agentJar.trim();
    }

    /** CI 默认档：计数 + 导出，不额外挂 agent。 */
    public static MixinJvmFlags ciDefault() {
        return new MixinJvmFlags(true, true, null);
    }

    /** 独立 sponge-mixin 场景：额外追加 javaagent。 */
    public static MixinJvmFlags withAgent(String agentJar) {
        return new MixinJvmFlags(true, true, agentJar);
    }

    /** 渲染成可直接拼进启动命令的参数列表，顺序固定便于日志 diff。 */
    public List<String> arguments() {
        List<String> args = new ArrayList<>(3);
        if (countInjections) {
            args.add(COUNT_INJECTIONS_FLAG);
        }
        if (export) {
            args.add(EXPORT_FLAG);
        }
        if (agentJar != null) {
            args.add(JAVA_AGENT_PREFIX + agentJar);
        }
        return List.copyOf(args);
    }

    /** 单行形式，写进 CI 日志或实例启动脚本。 */
    public String render() {
        return String.join(" ", arguments());
    }
}
