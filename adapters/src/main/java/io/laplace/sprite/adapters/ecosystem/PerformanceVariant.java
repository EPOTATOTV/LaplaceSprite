package io.laplace.sprite.adapters.ecosystem;

import java.util.List;

/**
 * 性能模组矩阵的五种渲染管线形态（14.9）。
 *
 * <p>矩阵行按渲染管线拆分：裸原版 / +Lithium / +Sodium / +Sodium+Iris / +OptiFine。
 * 加载器维度不在这里展开，它由 {@link MatrixCellGenerator} 的格定义负责。</p>
 */
public enum PerformanceVariant {

    VANILLA("裸原版", List.of()),
    LITHIUM("+Lithium", List.of("lithium")),
    SODIUM("+Sodium", List.of("sodium")),
    SODIUM_IRIS("+Sodium+Iris", List.of("sodium", "iris")),
    OPTIFINE("+OptiFine", List.of("optifine"));

    private final String displayName;
    private final List<String> mods;

    PerformanceVariant(String displayName, List<String> mods) {
        this.displayName = displayName;
        this.mods = List.copyOf(mods);
    }

    public String displayName() {
        return displayName;
    }

    public List<String> mods() {
        return mods;
    }

    /** 渲染管线形态只用于客户端格；服务端只能验证 Lithium 的 tick 行为。 */
    public boolean clientOnly() {
        return this != VANILLA && this != LITHIUM;
    }
}
