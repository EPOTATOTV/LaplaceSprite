package io.laplace.sprite.debug;

import java.nio.file.Path;

/**
 * 录制产物（15.1 事件流回放一期）：切分后的一条日志流。
 *
 * @param id         产物 id
 * @param threadName 来源线程名（未识别时为 unknown / all）
 * @param path       落盘路径
 * @param lineCount  行数
 */
public record RecordingArtifact(String id, String threadName, Path path, int lineCount) {
}
