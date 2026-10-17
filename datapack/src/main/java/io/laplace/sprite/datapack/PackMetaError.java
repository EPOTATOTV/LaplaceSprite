package io.laplace.sprite.datapack;

import java.util.Objects;

/**
 * pack.mcmeta 解析失败的结构化结果（15.8）。
 *
 * @param file     出错文件名或路径
 * @param jsonPath 出错位置，如 {@code $.pack.description}；无法定位时为 {@code $}
 * @param kind     失败类别
 * @param message  原始报错或说明
 * @param mojiraId 对应的 Mojira 编号；没有可对上的编号时为 null
 */
public record PackMetaError(String file, String jsonPath, PackMetaErrorKind kind, String message,
                            String mojiraId) {

    public PackMetaError {
        file = file == null || file.isBlank() ? PackMetaParser.FILE_NAME : file;
        jsonPath = jsonPath == null || jsonPath.isBlank() ? "$" : jsonPath;
        kind = Objects.requireNonNull(kind, "kind");
        message = message == null ? "" : message;
    }

    public boolean hasMojiraId() {
        return mojiraId != null && !mojiraId.isBlank();
    }

    /** 一行可读描述，直接进建议或报告。 */
    public String describe() {
        String id = hasMojiraId() ? " [" + mojiraId + "]" : "";
        return "%s（%s）：%s%s".formatted(file, jsonPath, message, id);
    }
}
