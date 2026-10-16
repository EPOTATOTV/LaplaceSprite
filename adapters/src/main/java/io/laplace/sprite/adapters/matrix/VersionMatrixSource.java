package io.laplace.sprite.adapters.matrix;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.util.List;

/**
 * 版本矩阵来源（14.7「运行时拉取 + 本地快照 + 手动 pin」）。
 *
 * <p>{@link #fetch} 允许失败：网络不通就退化成本地快照，调用方不需要区分。</p>
 */
public interface VersionMatrixSource {

    /** 本来源负责的家族。 */
    PlatformFamily family();

    /** 运行时拉取；失败时返回本地快照。 */
    List<VersionMatrixEntry> fetch(PlatformFamily family);

    /** 本地离线快照。 */
    List<VersionMatrixEntry> snapshot(PlatformFamily family);

    /** 手动 pin：写入后不再被自动更新覆盖。 */
    void pin(VersionMatrixEntry entry);
}
