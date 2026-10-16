package io.laplace.sprite.servers.clienttest;

/**
 * 客户端用例步骤类型（15.7 声明式用例 DSL）。
 *
 * <p>{@link #SEND} 与 {@link #EXPECT_LOG_ENDS_WITH} 对应 mc-runtime-test / hopper 那套 JSON 步骤规范，
 * 「开服 → 执行命令 → 断言日志出现某行」用它就能写完；{@link #WAIT_FOR_RENDER} 与 {@link #SCREENSHOT}
 * 支撑「进世界 → 等渲染完成 → 截图」这个最小断言单元。</p>
 */
public enum ClientDslOp {

    /** 向客户端下发命令（原版 / 命令或 mod 提供的命令）。 */
    SEND,
    /** 断言客户端日志尾部出现指定行。 */
    EXPECT_LOG_ENDS_WITH,
    /** 等待若干 tick，value 为十进制整数。 */
    WAIT_TICKS,
    /** 等待渲染完成。 */
    WAIT_FOR_RENDER,
    /** 截图并作为证据上传。 */
    SCREENSHOT
}
