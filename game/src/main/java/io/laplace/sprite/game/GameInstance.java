package io.laplace.sprite.game;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 一个可运行的独立游戏实例。
 *
 * @param instanceId 实例 id
 * @param version    版本 id
 * @param root       实例目录
 * @param clientJar  实例自己的 client.jar
 * @param metadata   该版本的元数据
 */
public record GameInstance(String instanceId, String version, Path root, Path clientJar,
                           GameVersionMetadata metadata) {

    public GameInstance {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(clientJar, "clientJar");
        Objects.requireNonNull(metadata, "metadata");
    }
}
