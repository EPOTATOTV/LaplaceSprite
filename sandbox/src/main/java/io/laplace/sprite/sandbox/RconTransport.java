package io.laplace.sprite.sandbox;

import java.io.IOException;

/**
 * RCON 传输（15.4 控制面）：交换一个完整帧，返回一个完整帧。
 *
 * <p>抽出来是为了让 {@link RconConsole} 能跑在内存替身上——协议逻辑离线可测，不必真服务端。</p>
 */
public interface RconTransport extends AutoCloseable {

    byte[] exchange(byte[] request) throws IOException;

    @Override
    default void close() throws IOException {
    }
}
