package io.laplace.sprite.sandbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * RCON 的 TCP 传输：帧头是 little-endian 的整数，Java 流按 big-endian 读，需要手动翻转。
 */
public final class SocketRconTransport implements RconTransport {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;

    private static final int MIN_PAYLOAD_BYTES = 10;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    private SocketRconTransport(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    public static SocketRconTransport connect(String host, int port) throws IOException {
        return connect(host, port, DEFAULT_CONNECT_TIMEOUT_MS);
    }

    public static SocketRconTransport connect(String host, int port, int connectTimeoutMs) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setTcpNoDelay(true);
        return new SocketRconTransport(socket);
    }

    @Override
    public byte[] exchange(byte[] request) throws IOException {
        out.write(request);
        out.flush();
        int length = Integer.reverseBytes(in.readInt());
        if (length < MIN_PAYLOAD_BYTES) {
            throw new IOException("RCON 响应长度字段不合法: " + length);
        }
        byte[] frame = new byte[length + 4];
        frame[0] = (byte) (length & 0xFF);
        frame[1] = (byte) (length >> 8 & 0xFF);
        frame[2] = (byte) (length >> 16 & 0xFF);
        frame[3] = (byte) (length >> 24 & 0xFF);
        in.readFully(frame, 4, length);
        return frame;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
