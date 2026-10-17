package io.laplace.sprite.mixin;

import io.laplace.sprite.core.spi.VersionOrder;

import java.util.List;

/**
 * Forge 旧包的 Mixin 0.7-0.8 兼容指引（15.11 P2）。
 *
 * <p>1.10.2–1.16.x 的旧 mod 按 Mixin 0.7 语义编写，新加载器默认加载 0.8 库后行为会漂移；
 * 这一段用 NotStirred 的 Mixin 0.7-0.8 Compatibility 保证 0.8 库先加载。1.17 起不再需要。</p>
 *
 * @param artifact        兼容组件名
 * @param minecraftRange  适用 MC 区间
 * @param guidance        中文操作说明
 */
public record OldMixinCompatibility(String artifact, String minecraftRange, String guidance) {

    public static final String MIN_MINECRAFT = "1.10.2";

    /** 上界开区间：覆盖 1.16.x，不含 1.17。 */
    public static final String UPPER_EXCLUSIVE = "1.17";

    public static final OldMixinCompatibility NOT_STIRRED = new OldMixinCompatibility(
            "NotStirred Mixin 0.7-0.8 Compatibility",
            "1.10.2–1.16.x",
            "把 NotStirred 的 Mixin 0.7-0.8 Compatibility 放进 mods/，并确保 0.8 库先于旧 mod 加载；"
                    + "否则 0.7 语义的注入会在新加载器上按 0.8 规则重新解析。");

    /** 该 MC 版本是否需要兼容组件。 */
    public static boolean needed(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) {
            return false;
        }
        return VersionOrder.compare(mcVersion, MIN_MINECRAFT) >= 0
                && VersionOrder.compare(mcVersion, UPPER_EXCLUSIVE) < 0;
    }

    /** 指引条目作为数据暴露，报告侧直接渲染。 */
    public static List<OldMixinCompatibility> catalog() {
        return List.of(NOT_STIRRED);
    }
}
