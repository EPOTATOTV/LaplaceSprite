package io.laplace.sprite.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已录制数据包序列（15.6）：从 JSON 夹具加载，供离线断言求值。
 *
 * <p>真实序列由 Paper 侧的 ProtocolLib 监听器录制后落成同样的结构；本类不产生序列，
 * 只读序列。</p>
 */
public final class RecordedPacketSequence {

    /** 随模块附带的录制夹具。 */
    public static final String FIXTURE_RESOURCE = "/protocol/packet-sequence.json";

    private RecordedPacketSequence() {
    }

    /** 加载随模块附带的夹具；资源缺失时返回空序列。 */
    public static List<RecordedPacket> loadDefault() {
        try (InputStream stream = RecordedPacketSequence.class.getResourceAsStream(FIXTURE_RESOURCE)) {
            if (stream == null) {
                return List.of();
            }
            return fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return List.of();
        }
    }

    /** 解析 {@code {"packets":[{"seq":1,"packetType":"...","fields":{...}}]}}。 */
    public static List<RecordedPacket> fromJson(String json) {
        List<RecordedPacket> packets = new ArrayList<>();
        try {
            JsonNode root = Json.jsonMapper().readTree(json);
            for (JsonNode node : root.path("packets")) {
                String packetType = node.path("packetType").asText(null);
                if (packetType == null) {
                    continue;
                }
                Map<String, Object> fields = new LinkedHashMap<>();
                node.path("fields").fields().forEachRemaining(entry ->
                        fields.put(entry.getKey(), scalar(entry.getValue())));
                packets.add(new RecordedPacket(node.path("seq").asLong(), packetType, fields));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return List.copyOf(packets);
    }

    private static Object scalar(JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        return node.asText();
    }
}
