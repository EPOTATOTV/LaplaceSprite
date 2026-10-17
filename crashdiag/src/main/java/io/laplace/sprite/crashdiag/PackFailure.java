package io.laplace.sprite.crashdiag;

/**
 * 从日志里提取出的资源包 / 数据包失败细节（15.8）。
 *
 * @param mojiraId 对应 Mojira 编号
 * @param file     出错文件或包目录
 * @param jsonPath 出错 JSON 路径，如 {@code $.pack.description}
 */
public record PackFailure(String mojiraId, String file, String jsonPath) {

    /** 一行可读描述。 */
    public String describe() {
        return "%s（%s）[%s]".formatted(file, jsonPath, mojiraId);
    }
}
