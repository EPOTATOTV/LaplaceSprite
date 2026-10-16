package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.deps.DependencyOrderer;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.InstallLayout;

import java.nio.file.Path;
import java.util.List;

/**
 * 安装布局（14.4 ②）：plugins/ · mods/ · datapacks/ 与加载顺序。
 *
 * <p>目录按家族定一次，顺序统一交给 {@link DependencyOrderer}，适配器不再各写一套。</p>
 */
public final class InstallLayouts {

    private static final DependencyOrderer ORDERER = new DependencyOrderer();

    private InstallLayouts() {
    }

    public static InstallLayout forFamily(PlatformFamily family) {
        return new DirectoryLayout(directoryFor(family));
    }

    /** 家族到装配目录：模组走 mods/，插件（含挂载的 Geyser）走 plugins/，地图数据包走 datapacks/。 */
    static Path directoryFor(PlatformFamily family) {
        if (family.isModLoader()) {
            return Path.of("server", "mods");
        }
        if (family == PlatformFamily.VANILLA_DATAPACK) {
            return Path.of("server", "world", "datapacks");
        }
        if (family.isPluginPlatform() || family == PlatformFamily.GEYSER) {
            return Path.of("server", "plugins");
        }
        return Path.of("server");
    }

    private record DirectoryLayout(Path directory) implements InstallLayout {

        @Override
        public Path layoutDirectory(PlatformKey key) {
            return directory;
        }

        @Override
        public List<Artifact> orderByDependencies(List<Artifact> artifacts) {
            return ORDERER.order(artifacts);
        }
    }
}
