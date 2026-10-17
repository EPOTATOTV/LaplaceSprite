package io.laplace.sprite.servers;

/**
 * 驱动器种类（12.2）：编排控制台按种类与配置选一条控制路径。
 *
 * <p>IN_PROCESS / LOCAL_PROCESS / DOCKER_COMPOSE 在平台端本机运行；SSH / DOCKER_API 是平台端独立部署
 * 后远控 8h8g 测试节点的两条路径。</p>
 */
public enum ServerDriverKind {

    IN_PROCESS,
    LOCAL_PROCESS,
    DOCKER_COMPOSE,
    SSH,
    DOCKER_API
}
