package io.laplace.sprite.servers.clienttest;

/**
 * 客户端驱动缝（15.7）：真实实现拉起 Loom 的 runClientGameTest，离线用内存替身。
 */
@FunctionalInterface
public interface ClientHarness {

    ClientRunResult run(ClientTestCase testCase);
}
