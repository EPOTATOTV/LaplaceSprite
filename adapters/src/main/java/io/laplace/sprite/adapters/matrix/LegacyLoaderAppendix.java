package io.laplace.sprite.adapters.matrix;

import java.util.List;

/**
 * 版本矩阵附录 · 遗留加载器结论（14.3）。
 *
 * <p>Rift 与 LiteLoader 都是客户端专属、版本冻结、没有服务端生态，接入矩阵里没有它们的适配器，
 * 只在附录注明「不支持」。存量 1.12.2 服务端兼容由 Forge 1.12.2 历史线兜底。</p>
 */
public final class LegacyLoaderAppendix {

    /**
     * 一条遗留加载器结论。
     *
     * @param loader        加载器名
     * @param versionRange  冻结的版本区间
     * @param supported     是否支持接入
     * @param reason        结论依据
     */
    public record LegacyLoader(String loader, String versionRange, boolean supported, String reason) {
    }

    private static final String FORGE_1212_FALLBACK = "Forge 1.12.2 历史线";

    private static final List<LegacyLoader> ENTRIES = List.of(
            new LegacyLoader("Rift", "1.13.x", false,
                    "客户端专属、版本冻结、无服务端生态，仅在版本矩阵附录注明"),
            new LegacyLoader("LiteLoader", "≤1.12.2", false,
                    "客户端专属、版本冻结、无服务端生态，仅在版本矩阵附录注明"));

    private LegacyLoaderAppendix() {
    }

    public static List<LegacyLoader> entries() {
        return ENTRIES;
    }

    /** 不支持接入的加载器。 */
    public static List<LegacyLoader> unsupported() {
        return ENTRIES.stream().filter(entry -> !entry.supported()).toList();
    }

    /** 存量 1.12.2 服务端兼容的兜底口径。 */
    public static String historicalFallback() {
        return FORGE_1212_FALLBACK;
    }
}
