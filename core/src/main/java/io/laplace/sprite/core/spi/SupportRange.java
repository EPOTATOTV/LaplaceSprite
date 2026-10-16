package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.model.PlatformKey;

/**
 * 适配器自报支持区间（14.4）：矩阵不匹配时按兼容链回退（Purpur→Paper→Bukkit；Quilt→Fabric；PNX→Nukkit）。
 */
public record SupportRange(String minMinecraft, String maxMinecraft, String minLoader, String maxLoader) {

    public static SupportRange any() {
        return new SupportRange(null, null, null, null);
    }

    public static SupportRange minecraft(String min, String max) {
        return new SupportRange(min, max, null, null);
    }

    public boolean supports(PlatformKey key) {
        return supportsMinecraft(key.minecraftVersion()) && supportsLoader(key.loaderVersion());
    }

    private boolean supportsMinecraft(String version) {
        if (version == null) {
            return true;
        }
        return inRange(version, minMinecraft, maxMinecraft);
    }

    private boolean supportsLoader(String loader) {
        if (loader == null || (minLoader == null && maxLoader == null)) {
            return true;
        }
        return inRange(loader, minLoader, maxLoader);
    }

    static boolean inRange(String version, String min, String max) {
        if (min != null && VersionOrder.compare(version, min) < 0) {
            return false;
        }
        return max == null || VersionOrder.compare(version, max) <= 0;
    }
}
