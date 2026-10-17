package io.laplace.sprite.adapters.ecosystem;

import java.util.List;
import java.util.Optional;

/**
 * 矩阵格生成器的版本来源接缝（14.9）。
 *
 * <p>生产环境分别对接 Fabric meta、PaperMC GraphQL Downloads API（STABLE 通道）与
 * NeoForge/Forge Maven 元数据；默认实现 {@link OfflineVersionSnapshot} 只读仓库内快照，
 * 测试与默认离线路径都不碰网络。</p>
 */
public interface VersionSource {

    /**
     * 一个 Minecraft 发布。
     *
     * @param version 版本号
     * @param channel 发布通道
     * @param lts     是否 LTS 线
     * @param java    运行所需 JDK；26.1+ 为 25
     */
    record MinecraftRelease(String version, String channel, boolean lts, int java) {
    }

    /**
     * 一个加载器发布。
     *
     * @param minecraft     Minecraft 版本
     * @param loader        加载器（fabric / neoforge / forge）
     * @param loaderVersion 加载器版本
     * @param stable        是否稳定版
     */
    record LoaderRelease(String minecraft, String loader, String loaderVersion, boolean stable) {
    }

    /**
     * PaperMC STABLE 通道的一条构建。
     *
     * @param minecraft Minecraft 版本
     * @param build     构建号
     * @param channel   通道，生成器只取 STABLE
     * @param java      该构建要求的 JDK
     */
    record PaperBuild(String minecraft, String build, String channel, int java) {
    }

    List<MinecraftRelease> minecraftReleases();

    List<LoaderRelease> loaderReleases(String loader);

    /** 只返回 STABLE 通道；没有稳定构建时为空。 */
    Optional<PaperBuild> paperBuild(String minecraft);

    /** Modrinth 回查：候选模组在 (MC 版本, 加载器) 上是否有可用构建。 */
    boolean hasBuild(String project, String minecraft, String loader);
}
