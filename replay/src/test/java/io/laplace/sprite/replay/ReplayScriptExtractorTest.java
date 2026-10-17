package io.laplace.sprite.replay;

import io.laplace.sprite.loadtest.BotAction;
import io.laplace.sprite.loadtest.BotBackend;
import io.laplace.sprite.loadtest.LoadScenario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayScriptExtractorTest {

    @Test
    void extractsMovementAndCommandsPerTick() {
        McprMetadata metadata = metadata();
        List<PacketRecord> packets = List.of(
                packet(10, 0x15, McprFixtures.positionLook(1, 64, 2, 90, 0, true)),
                packet(11, 0x14, McprFixtures.position(2, 64, 3, true)),
                packet(12, 0x16, McprFixtures.look(180, 10, false)),
                packet(13, 0x04, McprFixtures.text("mine")),
                packet(14, 0x05, McprFixtures.text("hello")));
        McprArchive archive = archive(metadata, packets);

        ReplayScript script = new ReplayScriptExtractor().extract(archive, "crash.mcpr",
                "snap-seed-1.20.1-abcdef123456-crash-42");

        assertEquals("crash.mcpr", script.sourceMcpr());
        assertEquals("1.20.1", script.mcVersion());
        assertEquals("snap-seed-1.20.1-abcdef123456-crash-42", script.worldSnapshotRef());
        assertEquals("player-uuid", script.playerName());
        assertEquals(3, script.movement().size());
        assertEquals(1.0, script.movement().samples().get(0).x(), 1e-9);
        assertEquals(90.0f, script.movement().samples().get(0).yaw(), 1e-6);
        assertEquals(2.0, script.movement().samples().get(1).x(), 1e-9);
        assertEquals(180.0f, script.movement().samples().get(2).yaw(), 1e-6);
        assertEquals(10.0f, script.movement().samples().get(2).pitch(), 1e-6);
        assertFalse(script.movement().samples().get(2).onGround());
        assertEquals(2, script.commands().size());
        assertEquals("mine", script.commands().entries().get(0).text());
        assertTrue(script.commands().entries().get(0).command());
        assertEquals("hello", script.commands().entries().get(1).text());
        assertFalse(script.commands().entries().get(1).command());
        assertEquals(14, script.durationTicks());
    }

    @Test
    void mapsScriptToLoadScenario() {
        McprMetadata metadata = metadata();
        List<PacketRecord> packets = List.of(
                packet(10, 0x15, McprFixtures.positionLook(1, 64, 2, 90, 0, true)),
                packet(20, 0x04, McprFixtures.text("setblock 1 2 3 stone")),
                packet(30, 0x05, McprFixtures.text("hello")));
        ReplayScript script = new ReplayScriptExtractor().extract(archive(metadata, packets),
                "crash.mcpr", "snap-ref");

        LoadScenario scenario = new ReplayScriptExtractor().toLoadScenario(script, "replay-crash-42");

        assertEquals("replay-crash-42", scenario.id());
        assertEquals(BotBackend.CARPET, scenario.backend());
        assertTrue(scenario.actions().containsAll(List.of(BotAction.JOIN, BotAction.WALK,
                BotAction.PLACE, BotAction.CHAT)));
        assertEquals(30, scenario.durationTicks());
        assertEquals(1, scenario.maxConcurrency());
        assertTrue(scenario.note().contains("crash.mcpr"));
    }

    @Test
    void mapsKnownCommandsToBotActions() {
        assertEquals(BotAction.MINE, ReplayScriptExtractor.actionOf(new CommandEntry(1, "dig", true)));
        assertEquals(BotAction.PLACE, ReplayScriptExtractor.actionOf(new CommandEntry(2, "place", true)));
        assertEquals(BotAction.ATTACK,
                ReplayScriptExtractor.actionOf(new CommandEntry(3, "attack", true)));
        assertEquals(BotAction.CHAT, ReplayScriptExtractor.actionOf(new CommandEntry(4, "hello", false)));
    }

    private static McprMetadata metadata() {
        return new McprMetadata(true, "localhost", 60000, 1700000000000L, "1.20.1", "MCPR", 14, 763,
                "ReplayMod", List.of("player-uuid"), "42");
    }

    private static PacketRecord packet(int tick, int packetId, byte[] payload) {
        return new PacketRecord(tick, packetId, payload.length, payload);
    }

    private static McprArchive archive(McprMetadata metadata, List<PacketRecord> packets) {
        return new McprArchive(metadata, packets, 1024, metadata.durationTicks(), metadata.mcversion(),
                metadata.generator(), metadata.selfPlayer(), false);
    }
}
