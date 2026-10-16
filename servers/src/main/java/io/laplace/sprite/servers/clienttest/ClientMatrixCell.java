package io.laplace.sprite.servers.clienttest;

/**
 * 客户端矩阵的一格（15.7）：MC 版本 × 加载器 × JDK × OS。
 */
public record ClientMatrixCell(String mcVersion, String loader, String jdk, String os) {

    public String key() {
        return mcVersion + "/" + loader + "/" + jdk + "/" + os;
    }

    @Override
    public String toString() {
        return key();
    }
}
