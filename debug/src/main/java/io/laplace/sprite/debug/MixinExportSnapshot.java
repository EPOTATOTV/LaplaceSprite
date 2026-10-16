package io.laplace.sprite.debug;

import java.nio.file.Path;
import java.util.List;

/**
 * Mixin 织入快照（15.1）：反注入失败最硬的证据。
 *
 * @param instanceId            调试实例 id
 * @param exportDir             导出目录
 * @param exportedClassCount    织入后类文件数
 * @param decompiledSourceCount 反编译源码文件数
 * @param targets               涉及的目标类名（按类文件相对路径还原）
 */
public record MixinExportSnapshot(String instanceId, Path exportDir, int exportedClassCount,
                                  int decompiledSourceCount, List<String> targets) {

    public MixinExportSnapshot {
        targets = targets == null ? List.of() : List.copyOf(targets);
    }

    public boolean empty() {
        return exportedClassCount == 0 && decompiledSourceCount == 0;
    }
}
