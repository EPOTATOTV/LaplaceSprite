package io.laplace.sprite.servers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 驱动器工厂（12.2）：按种类选出正确实现。
 *
 * <p>远控轨的降级行为用注入的伪造通道在 {@link SshCommandDriverTest} / {@link DockerApiDriverTest}
 * 里确定性验证，这里不触碰真实环境（环境变量恰好配好时会误连真实节点）。</p>
 */
class ServerDriverFactoryTest {

    @Test
    void selectsImplementationByKind() {
        assertInstanceOf(InProcessDriver.class, ServerDriverFactory.create(ServerDriverKind.IN_PROCESS));
        assertInstanceOf(LocalProcessDriver.class, ServerDriverFactory.create(ServerDriverKind.LOCAL_PROCESS));
        assertInstanceOf(DockerComposeDriver.class, ServerDriverFactory.create(ServerDriverKind.DOCKER_COMPOSE));
        assertInstanceOf(SshCommandDriver.class, ServerDriverFactory.create(ServerDriverKind.SSH));
        assertInstanceOf(DockerApiDriver.class, ServerDriverFactory.create(ServerDriverKind.DOCKER_API));

        assertEquals("ssh", ServerDriverFactory.create(ServerDriverKind.SSH).name());
        assertEquals("docker-api", ServerDriverFactory.create(ServerDriverKind.DOCKER_API).name());
    }
}
