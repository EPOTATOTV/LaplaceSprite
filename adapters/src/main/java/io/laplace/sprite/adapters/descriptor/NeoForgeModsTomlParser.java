package io.laplace.sprite.adapters.descriptor;

import io.laplace.sprite.core.model.PlatformFamily;

/**
 * {@code META-INF/neoforge.mods.toml} 解析（14.3 NeoForge）。
 *
 * <p>字段与 Forge 的 mods.toml 同构，差别只在文件名与 loader 版本口径，因此复用同一套解析。</p>
 */
public final class NeoForgeModsTomlParser extends ForgeModsTomlParser {

    @Override
    public boolean supports(PlatformFamily family) {
        return family == PlatformFamily.NEOFORGE;
    }

    @Override
    public String format() {
        return "META-INF/neoforge.mods.toml";
    }
}
