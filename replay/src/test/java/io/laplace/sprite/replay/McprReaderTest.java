package io.laplace.sprite.replay;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McprReaderTest {

    private final McprReader reader = new McprReader();

    @Test
    void readsTimedFramesAndRealMetadataFields() throws IOException {
        byte[] recording = McprFixtures.timedFrames(List.of(
                new McprFixtures.TimedFrame(1000, 0x15, McprFixtures.positionLook(1, 64, 2, 90, 0, true)),
                new McprFixtures.TimedFrame(2000, 0x04, McprFixtures.text("mine"))));
        byte[] zip = McprFixtures.zip(McprFixtures.metadata(14, 763, 60000), recording);

        McprArchive archive = reader.read(zip, "synthetic.mcpr");

        assertTrue(archive.metadata().singleplayer());
        assertEquals("localhost", archive.metadata().serverName());
        assertEquals(60000, archive.metadata().duration());
        assertEquals(14, archive.metadata().fileFormatVersion());
        assertEquals(763, archive.metadata().protocol());
        assertEquals("1.20.1", archive.mcVersion());
        assertEquals("ReplayMod", archive.generator());
        assertEquals("11111111-1111-1111-1111-111111111111", archive.playerName());
        assertEquals(1200, archive.durationTicks());
        assertEquals(2, archive.packetCount());
        assertFalse(archive.truncated());
        assertEquals(20, archive.packets().get(0).tick());
        assertEquals(0x15, archive.packets().get(0).packetId());
        assertEquals(33, archive.packets().get(0).payloadLength());
        assertEquals(40, archive.packets().get(1).tick());
        assertEquals(0x04, archive.packets().get(1).packetId());
        assertArrayEquals(McprFixtures.text("mine"), archive.packets().get(1).payload());
    }

    @Test
    void readsLegacyLittleEndianFraming() throws IOException {
        byte[] recording = McprFixtures.legacyFrames(List.of(
                new McprFixtures.LegacyFrame(0x14, McprFixtures.position(1, 2, 3, true)),
                new McprFixtures.LegacyFrame(0x05, McprFixtures.text("hello"))), true, false);
        byte[] zip = McprFixtures.zip(McprFixtures.metadata(13, 763, 1000), recording);

        McprArchive archive = reader.read(zip, "legacy.mcpr");

        assertEquals(2, archive.packetCount());
        assertFalse(archive.truncated());
        assertEquals(0, archive.packets().get(0).tick());
        assertEquals(1, archive.packets().get(1).tick());
        assertEquals(0x14, archive.packets().get(0).packetId());
        assertEquals(25, archive.packets().get(0).payloadLength());
        assertEquals(0x05, archive.packets().get(1).packetId());
    }

    @Test
    void legacyLengthMayExcludePacketId() throws IOException {
        byte[] recording = McprFixtures.legacyFrames(List.of(
                new McprFixtures.LegacyFrame(0x16, McprFixtures.look(10, 20, false)),
                new McprFixtures.LegacyFrame(0x17, new byte[]{1})), false, false);
        byte[] zip = McprFixtures.zip(McprFixtures.metadata(13, 763, 1000), recording);

        McprArchive archive = reader.read(zip, "legacy-excludes-id.mcpr");

        assertEquals(2, archive.packetCount());
        assertEquals(9, archive.packets().get(0).payloadLength());
        assertEquals(1, archive.packets().get(1).payloadLength());
    }

    @Test
    void legacyBigEndianFallbackIsTolerated() throws IOException {
        byte[] recording = McprFixtures.legacyFrames(List.of(
                new McprFixtures.LegacyFrame(0x14, McprFixtures.position(4, 5, 6, true))), true, true);
        byte[] zip = McprFixtures.zip(McprFixtures.metadata(13, 763, 1000), recording);

        McprArchive archive = reader.read(zip, "legacy-be.mcpr");

        assertEquals(1, archive.packetCount());
        assertEquals(0x14, archive.packets().get(0).packetId());
    }

    @Test
    void truncatedStreamIsFlaggedInsteadOfThrowing() throws IOException {
        byte[] complete = McprFixtures.timedFrames(List.of(
                new McprFixtures.TimedFrame(1000, 0x15, McprFixtures.positionLook(1, 64, 2, 90, 0, true)),
                new McprFixtures.TimedFrame(2000, 0x04, McprFixtures.text("mine"))));
        byte[] truncated = Arrays.copyOf(complete, complete.length - 5);
        byte[] zip = McprFixtures.zip(McprFixtures.metadata(14, 763, 4000), truncated);

        McprArchive archive = reader.read(zip, "truncated.mcpr");

        assertTrue(archive.truncated());
        assertEquals(1, archive.packetCount());
        assertEquals(0x15, archive.packets().get(0).packetId());
    }
}
