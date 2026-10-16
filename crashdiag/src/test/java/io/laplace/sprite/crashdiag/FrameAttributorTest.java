package io.laplace.sprite.crashdiag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 栈帧归因（15.5）：高覆盖率与低覆盖率两种情况都要看得见置信度差异。 */
class FrameAttributorTest {

    private static final String HIGH_COVERAGE_STACK = """
            java.lang.RuntimeException: boom
            \tat com.example.questmod.QuestManager.tick(QuestManager.java:42)
            \tat com.example.questmod.QuestManager.loop(QuestManager.java:18)
            \tat com.example.qolmod.QolHooks.onTick(QolHooks.java:9)
            \tat net.minecraft.server.MinecraftServer.tick(MinecraftServer.java:800)
            """;

    private static final String LOW_COVERAGE_STACK = """
            java.lang.RuntimeException: boom
            \tat com.example.questmod.QuestManager.tick(QuestManager.java:42)
            \tat net.minecraft.server.MinecraftServer.tick(MinecraftServer.java:800)
            \tat net.minecraft.server.MinecraftServer.run(MinecraftServer.java:700)
            \tat java.lang.Thread.run(Thread.java:840)
            """;

    private static final ClassIndex FULL_INDEX = ClassIndex.of(List.of(
            new ClassIndex.Entry("com.example.questmod.", "questmod"),
            new ClassIndex.Entry("com.example.qolmod.", "qolmod"),
            new ClassIndex.Entry("net.minecraft.", "minecraft")));

    private static final ClassIndex THIN_INDEX = ClassIndex.of(List.of(
            new ClassIndex.Entry("com.example.questmod.", "questmod")));

    private final FrameAttributor attributor = new FrameAttributor();

    @Test
    void highCoverageRanksSuspectsByFrameHits() {
        FrameAttributor.Attribution attribution = attributor.attribute(HIGH_COVERAGE_STACK, FULL_INDEX);

        assertEquals(4, attribution.totalFrames());
        assertEquals(4, attribution.resolvedFrames());
        assertEquals(1.0, attribution.classIndexCoverage(), 1.0e-9);
        assertFalse(attribution.lowCoverage());

        List<FrameAttributor.Suspect> suspects = attribution.suspects();
        assertEquals(3, suspects.size());
        assertEquals("questmod", suspects.get(0).modId());
        assertEquals(2, suspects.get(0).frameHits());
        assertEquals(0.5, suspects.get(0).confidence(), 1.0e-9);
        assertEquals("minecraft", suspects.get(1).modId(), "同命中数按 modId 升序");
        assertEquals("qolmod", suspects.get(2).modId());
        assertEquals("questmod", attribution.primarySuspect().orElseThrow());
        assertTrue(attribution.warnings().stream().anyMatch(w -> w.contains("不构成定论")));
    }

    @Test
    void lowCoverageWarnsAndLowersConfidence() {
        FrameAttributor.Attribution attribution = attributor.attribute(LOW_COVERAGE_STACK, THIN_INDEX);

        assertEquals(4, attribution.totalFrames());
        assertEquals(1, attribution.resolvedFrames());
        assertEquals(0.25, attribution.classIndexCoverage(), 1.0e-9);
        assertTrue(attribution.lowCoverage());

        FrameAttributor.Suspect suspect = attribution.suspects().get(0);
        assertEquals("questmod", suspect.modId());
        assertEquals(1, suspect.frameHits());
        // 惩罚前该 mod 在可归因帧里占比 1.0，覆盖率 0.25 惩罚后只值 0.25
        assertEquals(0.25, suspect.confidence(), 1.0e-9);

        assertTrue(attribution.warnings().stream().anyMatch(w -> w.contains("类索引覆盖率仅 25%")));
        assertTrue(attribution.warnings().stream().anyMatch(w -> w.contains("不足以定性")));
    }

    @Test
    void sameModIsLessConfidentUnderThinCoverage() {
        double richConfidence = attributor.attribute(HIGH_COVERAGE_STACK, FULL_INDEX)
                .suspects().stream()
                .filter(suspect -> suspect.modId().equals("questmod"))
                .findFirst()
                .orElseThrow()
                .confidence();
        double thinConfidence = attributor.attribute(LOW_COVERAGE_STACK, THIN_INDEX)
                .suspects().get(0)
                .confidence();

        assertTrue(richConfidence > thinConfidence,
                "覆盖率低时置信度必须被下调: " + richConfidence + " vs " + thinConfidence);
    }

    @Test
    void emptyIndexResolvesNothing() {
        FrameAttributor.Attribution attribution = attributor.attribute(HIGH_COVERAGE_STACK, ClassIndex.empty());

        assertEquals(0.0, attribution.classIndexCoverage(), 1.0e-9);
        assertTrue(attribution.suspects().isEmpty());
        assertTrue(attribution.primarySuspect().isEmpty());
        assertTrue(attribution.lowCoverage());
        assertTrue(attribution.warnings().stream().anyMatch(w -> w.contains("类索引覆盖率仅 0%")));
    }

    @Test
    void textWithoutFramesIsReportedExplicitly() {
        FrameAttributor.Attribution attribution = attributor.attribute("no frames here", FULL_INDEX);

        assertEquals(0, attribution.totalFrames());
        assertTrue(attribution.suspects().isEmpty());
        assertTrue(attribution.warnings().stream().anyMatch(w -> w.contains("没有解析出栈帧")));
    }

    @Test
    void longestPrefixWinsInClassIndex() {
        ClassIndex index = ClassIndex.of(List.of(
                new ClassIndex.Entry("net.fabricmc.", "fabric-loader"),
                new ClassIndex.Entry("net.fabricmc.fabric.", "fabric-api")));

        assertEquals("fabric-api", index.modFor("net.fabricmc.fabric.api.client.Foo").orElseThrow());
        assertEquals("fabric-loader", index.modFor("net.fabricmc.loader.impl.Bar").orElseThrow());
        assertTrue(index.modFor("org.example.Unknown").isEmpty());
        assertEquals(2, index.size());
    }

    @Test
    void defaultIndexLoadsFromBundledResource() {
        ClassIndex index = ClassIndex.defaultIndex();

        assertTrue(index.size() > 10, "随模块附带的类索引应有一定规模");
        assertEquals("paper", index.modFor("io.papermc.paper.event.player.PlayerJumpEvent").orElseThrow());
        assertEquals("fabric-api", index.modFor("net.fabricmc.fabric.api.event.Event").orElseThrow());
        assertEquals("bukkit", index.modFor("org.bukkit.entity.Player").orElseThrow());
    }

    @Test
    void frameParsingKeepsOrderAndDuplicates() {
        List<String> frames = attributor.frameClasses(HIGH_COVERAGE_STACK);

        assertEquals(List.of("com.example.questmod.QuestManager", "com.example.questmod.QuestManager",
                "com.example.qolmod.QolHooks", "net.minecraft.server.MinecraftServer"), frames);
        assertTrue(attributor.frameClasses(null).isEmpty());
    }

    @Test
    void classIndexParsesJsonEntries() {
        ClassIndex index = ClassIndex.fromJson("""
                {"entries":[{"prefix":"com.demo.","modId":"demo"},{"prefix":"","modId":"ignored"}]}
                """);

        assertEquals(1, index.size());
        assertEquals("demo", index.modFor("com.demo.Foo").orElseThrow());
    }
}
