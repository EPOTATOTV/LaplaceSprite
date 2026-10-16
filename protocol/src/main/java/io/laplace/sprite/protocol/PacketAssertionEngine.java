package io.laplace.sprite.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据包级断言引擎（15.6）：把已录制序列与断言规格对一遍，给出通过与差异。
 *
 * <p>真实抓包需在真实 Paper 服务端挂 ProtocolLib 监听器；本引擎只对已录制序列求值，
 * 序列缺失或字段缺失一律判不通过，不做猜测、不伪造实时抓包结果。</p>
 */
public final class PacketAssertionEngine {

    /** 对序列求值：每条规格取该类型首个数据包，逐个断言。 */
    public Evaluation evaluate(List<RecordedPacket> sequence, List<PacketAssertionSpec> specs) {
        List<PacketAssertion> assertions = new ArrayList<>();
        List<String> diffs = new ArrayList<>();
        boolean passed = true;

        for (PacketAssertionSpec spec : specs) {
            RecordedPacket packet = sequence.stream()
                    .filter(candidate -> candidate.packetType().equals(spec.packetType()))
                    .findFirst()
                    .orElse(null);
            if (packet == null) {
                PacketAssertion assertion = new PacketAssertion(spec.packetType(), spec.field(),
                        spec.matcher().expectation(), "<未捕获该类型数据包>", false);
                assertions.add(assertion);
                diffs.add(assertion.diff());
                passed = false;
                continue;
            }
            Object actual = packet.field(spec.field());
            boolean ok = spec.matcher().matches(actual);
            PacketAssertion assertion = new PacketAssertion(spec.packetType(), spec.field(),
                    spec.matcher().expectation(), describe(actual), ok);
            assertions.add(assertion);
            if (!ok) {
                diffs.add(assertion.diff() + "（seq=" + packet.seq() + "）");
                passed = false;
            }
        }
        return new Evaluation(assertions, passed, diffs);
    }

    private static String describe(Object actual) {
        if (actual == null) {
            return "<字段缺失>";
        }
        return String.valueOf(actual);
    }

    /**
     * 求值结果。
     *
     * @param assertions 每条规格的结果
     * @param passed     是否全部通过
     * @param diffs      失败项的差异描述
     */
    public record Evaluation(List<PacketAssertion> assertions, boolean passed, List<String> diffs) {

        public Evaluation {
            assertions = assertions == null ? List.of() : List.copyOf(assertions);
            diffs = diffs == null ? List.of() : List.copyOf(diffs);
        }

        public List<PacketAssertion> failures() {
            return assertions.stream().filter(assertion -> !assertion.passed()).toList();
        }
    }
}
