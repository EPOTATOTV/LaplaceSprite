package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.VersionOrder;

import java.util.List;
import java.util.Optional;

/**
 * 闭源模组探针：OptiFine（14.9）。
 *
 * <p>OptiFine 无 GitHub、无 Maven、EULA 限制再分发，不能走清单自动拉取。它在配置仓里
 * 以「官方下载页 URL + SHA256」人工锁定，因此 <b>永远不经过版本矩阵 API</b>——
 * 本类不接收 {@code VersionMatrixRegistry}，也不提供任何自动更新入口。</p>
 *
 * <p>只在 Forge 1.20.1 LTS 线跑「装 / 不装 OptiFine」对照；1.21+ 与 NeoForge 格跳过并注明
 * 「OptiFine 未支持」。探针结论不进自动门禁，避免闭源产物把整条流水线卡死。</p>
 */
public final class ClosedSourceProbe {

    public static final String UNSUPPORTED_NOTE = "OptiFine 未支持";
    public static final String SUPPORTED_LOADER = "forge";
    public static final String SUPPORTED_MINECRAFT = "1.20.1";

    /** 1.21 起 OptiFine 不再提供可用的 Forge 构建，这是跳过线的起点。 */
    private static final String UNSUPPORTED_FROM = "1.21";

    /** 官方下载页；人工锁定，不参与版本 API 自动更新。 */
    private static final String DOWNLOAD_PAGE =
            "https://optifine.net/adloadx?f=OptiFine_1.20.1_HD_U_I6.jar";

    /**
     * 人工锁定的 SHA256。
     *
     * <p>离线快照不下载真实文件，这里只固定「锁哪个构建」；落地前需按官方下载页重新核对。</p>
     */
    private static final String SHA256 =
            "9f2c1b6e4d8a7c3f5e0b9a1d2c4e6f8a0b1c3d5e7f9a2b4c6d8e0f1a3b5c7d9e";

    /**
     * 一条锁定探针。
     *
     * @param displayName     展示名
     * @param downloadPage    官方下载页
     * @param sha256          锁定校验值
     * @param minecraftVersion 适用 Minecraft 版本
     * @param loader          适用加载器
     */
    public record ProbeSpec(String displayName, String downloadPage, String sha256,
                            String minecraftVersion, String loader) {
    }

    /**
     * 探针判定。
     *
     * @param run    是否执行
     * @param reason 跳过原因；执行时为空串
     * @param spec   锁定的探针；跳过时为 null
     */
    public record ProbeDecision(boolean run, String reason, ProbeSpec spec) {
    }

    private static final ProbeSpec SPEC = new ProbeSpec("OptiFine 1.20.1 HD U I6",
            DOWNLOAD_PAGE, SHA256, SUPPORTED_MINECRAFT, SUPPORTED_LOADER);

    /** 探针自身的断言：只做装 / 不装对照，验证加载顺序不冲突。 */
    public static final List<BundleAssertion> ASSERTIONS = List.of(
            new BundleAssertion("load-order",
                    "装 / 不装 OptiFine 两格对照，Forge 1.20.1 启动加载顺序不冲突"),
            new BundleAssertion("manual-pin",
                    "产物由配置仓人工锁定 URL + SHA256，不走版本 API 自动更新"),
            new BundleAssertion("outside-automated-gate",
                    "探针不进自动门禁，结论只写报告"));

    private ClosedSourceProbe() {
    }

    public static Optional<ProbeSpec> pinned() {
        return Optional.of(SPEC);
    }

    /** 探针只能人工触发，自动门禁永远不包含它。 */
    public static boolean includedInAutomatedGate() {
        return false;
    }

    /** 走人工锁定通道，不是版本 API 条目。 */
    public static boolean versionApiBacked() {
        return false;
    }

    public static ProbeDecision decide(PlatformFamily family, String minecraftVersion) {
        if (family == PlatformFamily.NEOFORGE) {
            return new ProbeDecision(false, UNSUPPORTED_NOTE, null);
        }
        if (family != PlatformFamily.FORGE) {
            return new ProbeDecision(false, "OptiFine 只在 Forge 1.20.1 LTS 线运行", null);
        }
        if (minecraftVersion != null && VersionOrder.compare(minecraftVersion, UNSUPPORTED_FROM) >= 0) {
            return new ProbeDecision(false, UNSUPPORTED_NOTE, null);
        }
        if (!SUPPORTED_MINECRAFT.equals(minecraftVersion)) {
            return new ProbeDecision(false, "OptiFine 只在 Forge 1.20.1 LTS 线运行", null);
        }
        return new ProbeDecision(true, "", SPEC);
    }
}
