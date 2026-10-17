package io.laplace.sprite.adapters.pipeline;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 端口就绪探针（14.5 verify）。
 *
 * <p>抽成接口是为了让测试注入固定结果：真实轨道用 {@link #socketProbe(int)}，
 * 测试绝不把时间花在等待网络超时上。</p>
 */
@FunctionalInterface
public interface PortProbe {

    boolean isOpen(String host, int port);

    /** 真实轨道实现：TCP 连接探测，超时即视为未就绪。 */
    static PortProbe socketProbe(int timeoutMillis) {
        return (host, port) -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeoutMillis);
                return true;
            } catch (IOException ex) {
                return false;
            }
        };
    }
}
