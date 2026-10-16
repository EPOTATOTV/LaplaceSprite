package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.deps.DependencyOrderer;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.InstallLayout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 混合端安装布局（14.3 组合适配层第一件事）：双目录合并。
 *
 * <p>Bukkit 插件落 {@code plugins/}，模组落 {@code mods/}，两个生态各自装配、互不搬家。
 * {@link #layoutDirectory} 只能返回单个路径，这里返回服务端根目录，具体两个子目录用
 * {@link #pluginDirectory()} / {@link #modDirectory()} 取。</p>
 *
 * <p>依赖排序跨生态统一交给 {@link DependencyOrderer}：连接器（Connector）产的模组与插件之间存在
 * 跨生态依赖，不能各排各的。</p>
 */
public final class HybridInstallLayout implements InstallLayout {

    private static final DependencyOrderer ORDERER = new DependencyOrderer();

    private final Path serverRoot;

    public HybridInstallLayout() {
        this(Path.of("server"));
    }

    public HybridInstallLayout(Path serverRoot) {
        this.serverRoot = serverRoot;
    }

    @Override
    public Path layoutDirectory(PlatformKey key) {
        return serverRoot;
    }

    public Path pluginDirectory() {
        return serverRoot.resolve("plugins");
    }

    public Path modDirectory() {
        return serverRoot.resolve("mods");
    }

    /** 双目录，报告与装配脚本按此下单。 */
    public List<Path> directories() {
        return List.of(pluginDirectory(), modDirectory());
    }

    @Override
    public List<Artifact> orderByDependencies(List<Artifact> artifacts) {
        return ORDERER.order(artifacts);
    }

    /** 把插件与模组两个集合合成一条依赖序，跨生态硬依赖缺失即失败。 */
    public List<Artifact> mergeAcrossEcosystems(List<Artifact> plugins, List<Artifact> mods) {
        List<Artifact> merged = new ArrayList<>(plugins);
        merged.addAll(mods);
        return orderByDependencies(merged);
    }
}
