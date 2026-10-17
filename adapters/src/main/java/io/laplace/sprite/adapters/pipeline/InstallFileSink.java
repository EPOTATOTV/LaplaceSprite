package io.laplace.sprite.adapters.pipeline;

import io.laplace.sprite.core.model.Artifact;

import java.nio.file.Path;

/**
 * 装配写入出口（14.5 install）。
 *
 * <p>写入抽成接口是为了让装配测试完全不碰真实服务端目录：测试注入内存实现，
 * 真实轨道用 {@link DirectoryFileSink} 落到工作根下。</p>
 */
public interface InstallFileSink {

    /** 把产物写入目标目录，并回报实际落盘的摘要（哈希 / 签名校验的对象是「实际落盘」结果）。 */
    WrittenFile write(Path targetDirectory, Artifact artifact);

    /** 工作根下是否存在 EULA 声明。 */
    boolean hasEulaDeclaration(Path workingRoot);

    /**
     * 落盘摘要。
     *
     * @param fileName  落盘文件名
     * @param bytes     落盘字节数
     * @param sha512    实际内容哈希；无法计算时为 null
     * @param signature 实际签名标识；产物未声明签名时为 null
     */
    record WrittenFile(String fileName, long bytes, String sha512, String signature) {
    }
}
