package io.laplace.sprite.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 数据包级断言（15.6）：对录制序列求值，通过项与差异项都要看得见。 */
class PacketAssertionEngineTest {

    private final List<RecordedPacket> sequence = RecordedPacketSequence.loadDefault();
    private final PacketAssertionEngine engine = new PacketAssertionEngine();

    @Test
    void fixtureLoadsWithExpectedShape() {
        assertEquals(6, sequence.size());
        assertEquals("LOGIN_SUCCESS", sequence.get(0).packetType());
        assertEquals(42L, sequence.get(1).field("entityId"));
        assertEquals(0.5, (Double) sequence.get(3).field("x"), 1.0e-9);
        assertTrue(sequence.get(4).hasField("material"));
    }

    @Test
    void passingSpecsProduceNoDiffs() {
        PacketAssertionEngine.Evaluation evaluation = engine.evaluate(sequence, List.of(
                new PacketAssertionSpec("ENTITY_POSITION", "x", FieldMatcher.inRange(0.0, 1.0)),
                new PacketAssertionSpec("ENTITY_POSITION", "y", FieldMatcher.eq(64.0)),
                new PacketAssertionSpec("SET_SLOT", "material", FieldMatcher.eq("DIAMOND_SWORD")),
                new PacketAssertionSpec("CHUNK_DATA", "bitmask", FieldMatcher.inRange(15.0, 15.0)),
                new PacketAssertionSpec("CHAT", "message", FieldMatcher.contains("hell"))));

        assertTrue(evaluation.passed());
        assertTrue(evaluation.diffs().isEmpty());
        assertTrue(evaluation.failures().isEmpty());
        assertEquals(5, evaluation.assertions().size());
    }

    @Test
    void failingSpecsCarryDiffs() {
        PacketAssertionEngine.Evaluation evaluation = engine.evaluate(sequence, List.of(
                new PacketAssertionSpec("SET_SLOT", "amount", FieldMatcher.eq(2)),
                new PacketAssertionSpec("ENTITY_POSITION", "x", FieldMatcher.inRange(10.0, 20.0))));

        assertFalse(evaluation.passed());
        assertEquals(2, evaluation.failures().size());
        assertEquals(2, evaluation.diffs().size());
        assertTrue(evaluation.diffs().get(0).contains("SET_SLOT.amount"));
        assertTrue(evaluation.diffs().get(0).contains("实际 1"));
        assertTrue(evaluation.diffs().get(0).contains("seq=5"));
    }

    @Test
    void missingPacketTypeIsReportedNotGuessed() {
        PacketAssertionEngine.Evaluation evaluation = engine.evaluate(sequence, List.of(
                new PacketAssertionSpec("ENTITY_VELOCITY", "x", FieldMatcher.inRange(0.0, 1.0))));

        assertFalse(evaluation.passed());
        assertEquals("<未捕获该类型数据包>", evaluation.assertions().get(0).actual());
        assertTrue(evaluation.diffs().get(0).contains("ENTITY_VELOCITY.x"));
    }

    @Test
    void missingFieldIsReportedNotFilledIn() {
        PacketAssertionEngine.Evaluation evaluation = engine.evaluate(sequence, List.of(
                new PacketAssertionSpec("ENTITY_POSITION", "yaw", FieldMatcher.inRange(-180.0, 180.0))));

        assertFalse(evaluation.passed());
        assertEquals("<字段缺失>", evaluation.assertions().get(0).actual());
    }

    @Test
    void matchersCoverBasicCases() {
        assertTrue(FieldMatcher.eq("a").matches("a"));
        assertFalse(FieldMatcher.eq("a").matches(null));
        assertTrue(FieldMatcher.contains("ell").matches("hello"));
        assertFalse(FieldMatcher.contains("ell").matches(null));
        assertTrue(FieldMatcher.inRange(0.0, 1.0).matches(0.5));
        assertTrue(FieldMatcher.inRange(0.0, 1.0).matches("0.5"));
        assertFalse(FieldMatcher.inRange(0.0, 1.0).matches("abc"));
        assertFalse(FieldMatcher.inRange(0.0, 1.0).matches(null));
        assertTrue(FieldMatcher.of(actual -> true, "任意").matches("x"));
        assertEquals("包含 ell", FieldMatcher.contains("ell").expectation());
        assertEquals("在 [0.0, 1.0] 内", FieldMatcher.inRange(0.0, 1.0).expectation());
    }

    @Test
    void parsesSequenceFromJsonDirectly() {
        List<RecordedPacket> parsed = RecordedPacketSequence.fromJson("""
                {"packets":[{"seq":7,"packetType":"SET_SLOT","fields":{"material":"STONE","amount":3}}]}
                """);
        assertEquals(1, parsed.size());
        assertEquals("STONE", parsed.get(0).field("material"));
        assertEquals(3L, parsed.get(0).field("amount"));
    }
}
