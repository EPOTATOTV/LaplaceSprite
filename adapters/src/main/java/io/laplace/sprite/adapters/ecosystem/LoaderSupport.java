package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.model.PlatformFamily;

import java.util.List;
import java.util.Locale;

/**
 * 14.9 只覆盖三个模组加载器：Fabric、NeoForge、Forge。
 *
 * <p>Quilt 复用 Fabric 构建，Sponge 系挂在宿主加载器上，都不单独建生态格；
 * 其它家族直接拒绝，避免调用方误把插件线塞进模组基线。</p>
 */
final class LoaderSupport {

    static final List<PlatformFamily> MOD_LOADERS =
            List.of(PlatformFamily.FABRIC, PlatformFamily.NEOFORGE, PlatformFamily.FORGE);

    private LoaderSupport() {
    }

    static PlatformFamily requireModLoader(PlatformFamily family) {
        if (family == null || !MOD_LOADERS.contains(family)) {
            throw new IllegalArgumentException("生态基线只定义 Fabric / NeoForge / Forge，收到: " + family);
        }
        return family;
    }

    /** 制品坐标与快照里 builds 的加载器列都使用小写家族名。 */
    static String loaderId(PlatformFamily family) {
        return family.name().toLowerCase(Locale.ROOT);
    }
}
