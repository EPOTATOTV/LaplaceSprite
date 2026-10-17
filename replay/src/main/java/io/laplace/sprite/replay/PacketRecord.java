package io.laplace.sprite.replay;

/**
 * 录制流里的一帧。
 *
 * <p>旧格式没有时间戳，{@code tick} 用包序号代替，仅用于排序；带时间戳的格式按
 * 毫秒时间戳除以 50 折算。</p>
 *
 * @param tick          该帧所属 tick
 * @param packetId      协议包 id
 * @param payloadLength 载荷长度
 * @param payload       原始载荷
 */
public record PacketRecord(int tick, int packetId, int payloadLength, byte[] payload) {

    public PacketRecord {
        if (tick < 0) {
            throw new IllegalArgumentException("tick 不能为负数: " + tick);
        }
        if (packetId < 0) {
            throw new IllegalArgumentException("包 id 不能为负数: " + packetId);
        }
        payload = payload == null ? new byte[0] : payload.clone();
        if (payloadLength != payload.length) {
            throw new IllegalArgumentException("载荷长度字段与字节数不一致: " + payloadLength + " / "
                    + payload.length);
        }
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
