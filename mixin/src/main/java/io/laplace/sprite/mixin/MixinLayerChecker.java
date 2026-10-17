package io.laplace.sprite.mixin;

import io.laplace.sprite.adapters.descriptor.FabricModJsonParser;
import io.laplace.sprite.adapters.descriptor.PluginDescriptor;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.spi.VersionOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mixin 版本分层兼容检查（15.11 P2）。
 *
 * <p>Fabric 的剧本：Loader 0.12+ 根据 mod 在 fabric.mod.json 里声明的 Loader 版本下界，
 * 决定是否让 Mixin 按 0.7 旧行为运行（官方模拟机制）。所以「声明」必须和启动日志里
 * SpongePowered MIXIN Subsystem Version 对得上，并且子系统版本行只能出现一条。</p>
 */
public final class MixinLayerChecker {

    /** Loader 提供旧行为模拟的起始版本。 */
    public static final String SIMULATION_FLOOR = "0.12.0";

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static final Pattern SUBSYSTEM_VERSION = Pattern.compile(
            "SpongePowered MIXIN Subsystem Version=([^\\s,;]+)");

    private static final List<String> LOADER_IDS = List.of("fabricloader", "fabric-loader");

    private MixinLayerChecker() {
    }

    /**
     * 用真实解析器读 fabric.mod.json 的 Loader 声明，再与启动日志对照。
     *
     * @param fabricModJson fabric.mod.json 原文
     * @param launchLog     启动日志原文
     */
    public static MixinLayerBaseline check(String fabricModJson, String launchLog) {
        PluginDescriptor descriptor = new FabricModJsonParser().parse(fabricModJson);
        ArtifactDependency loader = descriptor.dependencies().stream()
                .filter(dependency -> LOADER_IDS.stream().anyMatch(id -> id.equalsIgnoreCase(dependency.id())))
                .findFirst()
                .orElse(null);
        String declared = loader == null ? null : firstVersion(loader.range());
        boolean simulatedOld = declared != null && VersionOrder.compare(declared, SIMULATION_FLOOR) < 0;

        List<String> observed = subsystemVersions(launchLog);
        String observedVersion = observed.isEmpty() ? null : observed.get(0);

        List<String> notes = new ArrayList<>();
        boolean consistent = true;
        if (declared == null) {
            consistent = false;
            notes.add("fabric.mod.json 未声明 fabricloader 版本区间，无法确认 Mixin 模拟档位");
        }
        if (observed.isEmpty()) {
            consistent = false;
            notes.add("启动日志缺少 SpongePowered MIXIN Subsystem Version，无法确认子系统版本");
        } else if (observed.size() > 1) {
            consistent = false;
            notes.add("启动日志出现 %d 条 Mixin 子系统版本行（%s），要求仅一条唯一版本号"
                    .formatted(observed.size(), String.join(", ", observed)));
        }
        if (declared != null && observedVersion != null) {
            boolean observedModern = VersionOrder.compare(observedVersion, "0.8") >= 0;
            if (observedModern && simulatedOld) {
                notes.add("Loader 声明 %s 低于 %s，按官方模拟机制以 Mixin 旧行为运行；观测子系统 %s"
                        .formatted(declared, SIMULATION_FLOOR, observedVersion));
            } else if (!observedModern && simulatedOld) {
                notes.add("Loader 声明 %s 与 Mixin 子系统 %s 均为旧档，行为一致"
                        .formatted(declared, observedVersion));
            } else if (observedModern) {
                notes.add("Loader 声明 %s 与 Mixin %s 语义一致".formatted(declared, observedVersion));
            } else {
                consistent = false;
                notes.add("Loader 声明 %s 已提供 Mixin 0.8 语义，但日志报告子系统 %s；疑似重复/旧版 mixin 库先行加载"
                        .formatted(declared, observedVersion));
            }
        }
        return new MixinLayerBaseline(declared, observedVersion, simulatedOld, consistent, notes);
    }

    private static String firstVersion(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION.matcher(range);
        return matcher.find() ? matcher.group() : null;
    }

    private static List<String> subsystemVersions(String launchLog) {
        if (launchLog == null || launchLog.isBlank()) {
            return List.of();
        }
        List<String> versions = new ArrayList<>();
        Matcher matcher = SUBSYSTEM_VERSION.matcher(launchLog);
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        return versions;
    }
}
