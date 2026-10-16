package io.laplace.sprite.protocol;

/**
 * 一条字段断言的求值结果（15.6）。
 *
 * @param packetType 数据包类型
 * @param field      字段名
 * @param expected   期望的人话描述
 * @param actual     实际取值的人话描述
 * @param passed     是否通过
 */
public record PacketAssertion(String packetType, String field, String expected, String actual, boolean passed) {

    /** 差异描述，用于报告里指出具体是哪一条对不上。 */
    public String diff() {
        return "%s.%s 期望 %s，实际 %s".formatted(packetType, field, expected, actual);
    }
}
