package io.laplace.sprite.core.engine.replay;

import java.nio.file.Path;

/**
 * 一次回放导出的结果（4.6 可导出）。
 *
 * <p>只返回路径与内容，是否归档进 EvidenceVault 由调用方决定，core 不依赖 report。</p>
 *
 * @param jsonPath     JSON 路径
 * @param markdownPath Markdown 摘要路径
 * @param json         JSON 内容
 * @param markdown     Markdown 内容
 * @param eventCount   事件条数
 */
public record ReplayExport(Path jsonPath, Path markdownPath, String json, String markdown,
                           int eventCount) {
}
