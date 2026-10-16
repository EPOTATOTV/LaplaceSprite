package io.laplace.sprite.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条已录制的数据包（15.6）。
 *
 * <p>真实抓包必须在真实 Paper 服务端挂 ProtocolLib 监听器录制，本类只承载录制结果，
 * 不做也不伪造实时抓包。</p>
 *
 * @param seq        录制顺序
 * @param packetType 数据包类型
 * @param fields     关键字段
 */
public record RecordedPacket(long seq, String packetType, Map<String, Object> fields) {

    public RecordedPacket {
        fields = fields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Object field(String name) {
        return fields.get(name);
    }

    public boolean hasField(String name) {
        return fields.containsKey(name);
    }
}
