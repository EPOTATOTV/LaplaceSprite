package io.laplace.sprite.core.model;

import java.util.Objects;

/**
 * 三维矩阵键：{平台/加载器} × {Minecraft 版本} × {加载器版本}（14.7）。
 */
public record PlatformKey(PlatformFamily family, String minecraftVersion, String loaderVersion) {

    public PlatformKey {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
    }

    public static PlatformKey of(PlatformFamily family, String minecraftVersion) {
        return new PlatformKey(family, minecraftVersion, null);
    }

    public String id() {
        return loaderVersion == null || loaderVersion.isBlank()
                ? family.name().toLowerCase() + "/" + minecraftVersion
                : family.name().toLowerCase() + "/" + minecraftVersion + "/" + loaderVersion;
    }

    @Override
    public String toString() {
        return id();
    }
}
