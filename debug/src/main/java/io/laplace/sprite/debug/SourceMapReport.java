package io.laplace.sprite.debug;

import java.nio.file.Path;
import java.util.List;

/**
 * 源码映射报告（15.1）。
 *
 * <p>{@code generated=false} 时必须能在 {@code notes} 里看到原因，报告不会出现「看起来跑过了」的空结果。</p>
 *
 * @param instanceId 调试实例 id
 * @param generated  是否真的生成了源码
 * @param sourceDir  源码目录；未生成时为 null
 * @param generator  生成方式
 * @param notes      过程说明与降级原因
 */
public record SourceMapReport(String instanceId, boolean generated, Path sourceDir, String generator,
                              List<String> notes) {

    public SourceMapReport {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
