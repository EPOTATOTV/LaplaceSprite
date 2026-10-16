package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.PlatformKey;

import java.nio.file.Path;
import java.util.List;

/**
 * ② 安装布局（14.4）：plugins/ · mods/ · datapacks/ 与加载顺序。
 */
public interface InstallLayout {

    /** 目标目录。 */
    Path layoutDirectory(PlatformKey key);

    /** 硬/软依赖排序（depend / softdepend / loadbefore）。 */
    List<Artifact> orderByDependencies(List<Artifact> artifacts);
}
