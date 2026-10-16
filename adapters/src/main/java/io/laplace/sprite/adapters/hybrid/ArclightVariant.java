package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.core.model.PlatformFamily;

/**
 * Arclight 变体（14.3）：同一混合端按宿主模组加载器分 Forge / NeoForge / Fabric 三个变体，
 * 变体只影响模组侧总线与描述符，Bukkit 侧一致。
 */
public enum ArclightVariant {

    FORGE("arclight", PlatformFamily.FORGE, "META-INF/mods.toml"),
    NEOFORGE("arclight-neoforge", PlatformFamily.NEOFORGE, "META-INF/neoforge.mods.toml"),
    FABRIC("arclight-fabric", PlatformFamily.FABRIC, "fabric.mod.json");

    private final String adapterId;
    private final PlatformFamily loaderFamily;
    private final String modDescriptor;

    ArclightVariant(String adapterId, PlatformFamily loaderFamily, String modDescriptor) {
        this.adapterId = adapterId;
        this.loaderFamily = loaderFamily;
        this.modDescriptor = modDescriptor;
    }

    public String adapterId() {
        return adapterId;
    }

    public PlatformFamily loaderFamily() {
        return loaderFamily;
    }

    public String modDescriptor() {
        return modDescriptor;
    }
}
