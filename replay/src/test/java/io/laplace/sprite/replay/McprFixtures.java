package io.laplace.sprite.replay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 测试内生成的 .mcpr 素材，不提交任何二进制录像。 */
final class McprFixtures {

    record TimedFrame(int timeMillis, int packetId, byte[] payload) {
    }

    record LegacyFrame(int packetId, byte[] payload) {
    }

    private McprFixtures() {
    }

    static byte[] zip(String metadataJson, byte[] recording) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("metaData.json"));
            zip.write(metadataJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("recording.tmcpr"));
            zip.write(recording);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    static String metadata(int fileFormatVersion, int protocol, int durationMillis) {
        return """
                {
                  "singleplayer": true,
                  "serverName": "localhost",
                  "duration": %d,
                  "date": 1700000000000,
                  "mcversion": "1.20.1",
                  "fileFormat": "MCPR",
                  "fileFormatVersion": %d,
                  "protocol": %d,
                  "generator": "ReplayMod",
                  "players": ["11111111-1111-1111-1111-111111111111"],
                  "selfId": 42
                }
                """.formatted(durationMillis, fileFormatVersion, protocol);
    }

    /** 带时间戳帧：timeBE + lenBE（含 varint 包 id）+ varint 包 id + 载荷。 */
    static byte[] timedFrames(List<TimedFrame> frames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TimedFrame frame : frames) {
            writeIntBE(out, frame.timeMillis());
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeVarInt(body, frame.packetId());
            body.write(frame.payload());
            writeIntBE(out, body.size());
            out.write(body.toByteArray());
        }
        return out.toByteArray();
    }

    /** 旧帧：length + packetId + 载荷，均为小端；length 可含或不含 packetId。 */
    static byte[] legacyFrames(List<LegacyFrame> frames, boolean lengthIncludesId,
                               boolean bigEndian) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (LegacyFrame frame : frames) {
            int length = lengthIncludesId ? frame.payload().length + 4 : frame.payload().length;
            if (bigEndian) {
                writeIntBE(out, length);
                writeIntBE(out, frame.packetId());
            } else {
                writeIntLE(out, length);
                writeIntLE(out, frame.packetId());
            }
            out.write(frame.payload());
        }
        return out.toByteArray();
    }

    static byte[] positionLook(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        ByteBuffer buffer = ByteBuffer.allocate(33);
        buffer.putDouble(x);
        buffer.putDouble(y);
        buffer.putDouble(z);
        buffer.putFloat(yaw);
        buffer.putFloat(pitch);
        buffer.put((byte) (onGround ? 1 : 0));
        return buffer.array();
    }

    static byte[] position(double x, double y, double z, boolean onGround) {
        ByteBuffer buffer = ByteBuffer.allocate(25);
        buffer.putDouble(x);
        buffer.putDouble(y);
        buffer.putDouble(z);
        buffer.put((byte) (onGround ? 1 : 0));
        return buffer.array();
    }

    static byte[] look(float yaw, float pitch, boolean onGround) {
        ByteBuffer buffer = ByteBuffer.allocate(9);
        buffer.putFloat(yaw);
        buffer.putFloat(pitch);
        buffer.put((byte) (onGround ? 1 : 0));
        return buffer.array();
    }

    static byte[] text(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int current = value;
        do {
            int bits = current & 0x7F;
            current >>>= 7;
            if (current != 0) {
                bits |= 0x80;
            }
            out.write(bits);
        } while (current != 0);
    }

    private static void writeIntBE(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }
}
