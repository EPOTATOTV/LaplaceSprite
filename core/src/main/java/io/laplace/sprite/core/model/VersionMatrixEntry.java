package io.laplace.sprite.core.model;

import java.time.Instant;

/**
 * 版本矩阵条目（14.7）：运行时拉取 + 本地快照 + 手动 pin。
 *
 * @param family           平台/加载器家族
 * @param minecraftVersion Minecraft 版本
 * @param loaderVersion    加载器版本（可为空）
 * @param source           来源（fabric-meta / quilt-meta / forge-promotions / neoforge-projects / manual）
 * @param fetchedAt        拉取时间
 * @param pinned           是否手动 pin（pin 后禁止自动更新）
 */
public record VersionMatrixEntry(PlatformFamily family, String minecraftVersion, String loaderVersion,
                                 String source, Instant fetchedAt, boolean pinned) {

    public PlatformKey key() {
        return new PlatformKey(family, minecraftVersion, loaderVersion);
    }
}
