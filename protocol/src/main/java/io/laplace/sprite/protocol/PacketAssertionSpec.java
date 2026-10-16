package io.laplace.sprite.protocol;

import java.util.Objects;

/**
 * 一条数据包字段断言规格（15.6）。
 *
 * @param packetType 数据包类型，如 ENTITY_POSITION / CHUNK_DATA / SET_SLOT
 * @param field      被断言的关键字段
 * @param matcher    匹配器，内含期望值
 */
public record PacketAssertionSpec(String packetType, String field, FieldMatcher matcher) {

    public PacketAssertionSpec {
        Objects.requireNonNull(packetType, "packetType");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(matcher, "matcher");
    }
}
