package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.model.PlatformFamily;

import java.util.List;

/**
 * 重型插件回归：Multiverse-Core × WorldEdit × ViaVersion（14.9）。
 *
 * <p>这三个插件分别动世界容器、方块编辑状态与协议层，单独装都不难，凑在一起才容易出现
 * 跨世界剪贴板串数据或协议降级玩家被踢。只在 Bukkit 系 / Paper 系矩阵格注入。</p>
 */
public final class HeavyPluginBundle {

    private static final List<PlatformFamily> SUPPORTED = List.of(
            PlatformFamily.BUKKIT, PlatformFamily.SPIGOT, PlatformFamily.PAPER,
            PlatformFamily.PURPUR, PlatformFamily.PUFFERFISH, PlatformFamily.FOLIA);

    /**
     * 一条重型插件。
     *
     * @param id         插件名
     * @param coordinate 产物坐标
     * @param loadOrder  从 0 开始的加载位次
     * @param assertions 该插件的断言
     */
    public record PluginArtifact(String id, String coordinate, int loadOrder,
                                 List<BundleAssertion> assertions) {
    }

    private static final List<PluginArtifact> ARTIFACTS = List.of(
            new PluginArtifact("Multiverse-Core", "hangar:Multiverse-Core", 0, List.of(
                    new BundleAssertion("second-world", "/mv create 创建第二个世界成功"),
                    new BundleAssertion("world-list", "/mv list 同时列出主世界与第二世界"))),
            new PluginArtifact("WorldEdit", "hangar:WorldEdit", 1, List.of(
                    new BundleAssertion("region-set-both-worlds", "两个世界分别 //set 区域成功"),
                    new BundleAssertion("no-cross-world-clipboard",
                            "回滚与剪贴板无跨世界串数据"))),
            new PluginArtifact("ViaVersion", "hangar:ViaVersion", 2, List.of(
                    new BundleAssertion("downgraded-client-join",
                            "旧客户端在协议降级模式下进入双世界不崩"),
                    new BundleAssertion("event-bridge-protocol",
                            "事件桥断言协议降级玩家进入双世界不崩"))));

    private final PlatformFamily family;

    private HeavyPluginBundle(PlatformFamily family) {
        this.family = family;
    }

    public static HeavyPluginBundle forFamily(PlatformFamily family) {
        if (family == null || !SUPPORTED.contains(family)) {
            throw new IllegalArgumentException("重型插件回归只适用于 Bukkit 系 / Paper 系，收到: " + family);
        }
        return new HeavyPluginBundle(family);
    }

    public PlatformFamily family() {
        return family;
    }

    public List<PluginArtifact> artifacts() {
        return ARTIFACTS;
    }

    public List<String> loadOrder() {
        return ARTIFACTS.stream().map(PluginArtifact::id).toList();
    }

    public List<BundleAssertion> assertions() {
        return ARTIFACTS.stream().flatMap(artifact -> artifact.assertions().stream()).toList();
    }
}
