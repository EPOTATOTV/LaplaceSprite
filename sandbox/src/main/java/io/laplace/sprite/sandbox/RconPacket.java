package io.laplace.sprite.sandbox;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * RCON 帧（15.4 控制面）：little-endian 长度 + 请求 id + 类型 + NUL 结尾的 body。
 *
 * @param id   请求 id（登录失败时时服务端回 -1）
 * @param type 帧类型
 * @param body 载荷
 */
public record RconPacket(int id, Type type, String body) {

    /** 帧类型码。 */
    public enum Type {

        LOGIN(3),
        COMMAND(2),
        RESPONSE_VALUE(0);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        /** 类型码 2 被协议复用为登录应答，这里统一归到 COMMAND。 */
        static Type fromCode(int code) {
            return switch (code) {
                case 3 -> LOGIN;
                case 2 -> COMMAND;
                case 0 -> RESPONSE_VALUE;
                default -> throw new IllegalArgumentException("未知 RCON 类型码: " + code);
            };
        }
    }

    /** 长度字段之外的固定部分：id(4) + type(4) + 两个 NUL。 */
    private static final int PAYLOAD_OVERHEAD = 10;

    private static final int MIN_FRAME_BYTES = 12;

    public RconPacket {
        body = body == null ? "" : body;
    }

    public byte[] encode() {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        int length = PAYLOAD_OVERHEAD + bodyBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(4 + length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(length);
        buffer.putInt(id);
        buffer.putInt(type.code());
        buffer.put(bodyBytes);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        return buffer.array();
    }

    public static RconPacket decode(byte[] frame) {
        if (frame == null || frame.length < MIN_FRAME_BYTES) {
            throw new IllegalArgumentException("RCON 帧长度不足: " + (frame == null ? 0 : frame.length));
        }
        ByteBuffer buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN);
        int length = buffer.getInt();
        if (length < PAYLOAD_OVERHEAD || length > frame.length - 4) {
            throw new IllegalArgumentException("RCON 帧长度字段不合法: " + length);
        }
        int id = buffer.getInt();
        int code = buffer.getInt();
        byte[] bodyBytes = new byte[length - PAYLOAD_OVERHEAD];
        buffer.get(bodyBytes);
        return new RconPacket(id, Type.fromCode(code), stripNul(bodyBytes));
    }

    /** 有的服务端会多补一个 NUL，解码时一律剔除尾部空字符。 */
    private static String stripNul(byte[] bodyBytes) {
        String text = new String(bodyBytes, StandardCharsets.UTF_8);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '\0') {
            end--;
        }
        return text.substring(0, end);
    }
}
