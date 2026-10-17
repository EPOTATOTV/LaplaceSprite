package io.laplace.sprite.replay;

import io.laplace.sprite.core.io.Json;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * {@code .mcpr} 解析（15.10）。
 *
 * <p>支持的帧格式以 {@code fileFormatVersion} 分界：</p>
 * <ul>
 *   <li>14 及以上：{@code [timeBE:int32][lenBE:int32][varint packetId][payload]}，
 *       与 mc-replay-go 的 {@code CurrentFileFormatVersion = 14} 一致；tick 由毫秒时间戳折算。</li>
 *   <li>14 以下：{@code [len:int32][packetId:int32][payload]}。设计文档给定的字节序是
 *       little-endian，这里先按小端读；长度或边界不合理时回退大端，兼容旧 ReplayStudio
 *       用 DataOutputStream 写出的流。</li>
 * </ul>
 *
 * <p>长度字段按 ReplayStudio 的 {@code writeInt(len + 4)} 语义优先理解为「含 packetId」，
 * 同时用下一帧边界做校验；若只有「不含 packetId」的解释能接上，则采用后者。</p>
 *
 * <p>流被截断时不抛异常：已解析的帧照常返回，{@link McprArchive#truncated()} 置真。
 * 缺少 metaData.json 或 recording.tmcpr 才判为不是有效的 .mcpr。</p>
 */
public final class McprReader {

    public static final String METADATA_ENTRY = "metaData.json";
    public static final String RECORDING_ENTRY = "recording.tmcpr";

    /** 带毫秒时间戳与 varint 包 id 的帧从该版本开始。 */
    public static final int TIMED_FRAME_VERSION = 14;

    /** 20 TPS 下 1 tick = 50ms。 */
    public static final int MILLIS_PER_TICK = 50;

    public McprArchive read(Path file) {
        try {
            return read(Files.readAllBytes(file), file.getFileName().toString());
        } catch (IOException ex) {
            throw new UncheckedIOException("读取 .mcpr 失败: " + file, ex);
        }
    }

    public McprArchive read(byte[] zipBytes, String sourceName) {
        byte[] metadataBytes = null;
        byte[] recordingBytes = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (METADATA_ENTRY.equalsIgnoreCase(name)) {
                    metadataBytes = zip.readAllBytes();
                } else if (RECORDING_ENTRY.equalsIgnoreCase(name)) {
                    recordingBytes = zip.readAllBytes();
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("读取 .mcpr ZIP 失败: " + sourceName, ex);
        }
        if (metadataBytes == null) {
            throw new IllegalArgumentException("不是有效的 .mcpr（缺少 " + METADATA_ENTRY + "）: "
                    + sourceName);
        }
        if (recordingBytes == null) {
            throw new IllegalArgumentException("不是有效的 .mcpr（缺少 " + RECORDING_ENTRY + "）: "
                    + sourceName);
        }

        McprMetadata metadata = Json.read(new String(metadataBytes, StandardCharsets.UTF_8),
                McprMetadata.class);
        FrameParse parse = metadata.fileFormatVersion() >= TIMED_FRAME_VERSION
                ? parseTimedFrames(recordingBytes)
                : parseLegacyFrames(recordingBytes);
        return new McprArchive(metadata, parse.packets(), zipBytes.length, metadata.durationTicks(),
                metadata.mcversion(), metadata.generator(), metadata.selfPlayer(), parse.truncated());
    }

    private static FrameParse parseLegacyFrames(byte[] data) {
        List<PacketRecord> packets = new ArrayList<>();
        int position = 0;
        boolean truncated = false;
        while (position < data.length) {
            if (data.length - position < 8) {
                truncated = true;
                break;
            }
            int length = readIntLE(data, position);
            int packetId = readIntLE(data, position + 4);
            int payloadLength = chooseLegacyPayloadLength(data, position, length);
            if (payloadLength < 0) {
                int bigEndianLength = readIntBE(data, position);
                int bigEndianId = readIntBE(data, position + 4);
                int bigEndianPayload = chooseLegacyPayloadLength(data, position, bigEndianLength);
                if (bigEndianPayload < 0) {
                    truncated = true;
                    break;
                }
                length = bigEndianLength;
                packetId = bigEndianId;
                payloadLength = bigEndianPayload;
            }
            if (packetId < 0 || length < 0 || position + 8L + payloadLength > data.length) {
                truncated = true;
                break;
            }
            packets.add(new PacketRecord(packets.size(), packetId, payloadLength,
                    Arrays.copyOfRange(data, position + 8, position + 8 + payloadLength)));
            position += 8 + payloadLength;
        }
        return new FrameParse(packets, truncated);
    }

    private static FrameParse parseTimedFrames(byte[] data) {
        List<PacketRecord> packets = new ArrayList<>();
        int position = 0;
        boolean truncated = false;
        while (position < data.length) {
            if (data.length - position < 8) {
                truncated = true;
                break;
            }
            int timeMillis = readIntBE(data, position);
            int length = readIntBE(data, position + 4);
            int frameStart = position + 8;
            VarInt packetId = readVarInt(data, frameStart);
            if (length <= 0 || packetId == null || packetId.value() < 0) {
                truncated = true;
                break;
            }
            int payloadLength = chooseTimedPayloadLength(data, frameStart, packetId.next(), length);
            if (payloadLength < 0 || (long) packetId.next() + payloadLength > data.length) {
                truncated = true;
                break;
            }
            int payloadStart = packetId.next();
            packets.add(new PacketRecord(Math.max(0, timeMillis / MILLIS_PER_TICK), packetId.value(),
                    payloadLength, Arrays.copyOfRange(data, payloadStart, payloadStart + payloadLength)));
            position = payloadStart + payloadLength;
        }
        return new FrameParse(packets, truncated);
    }

    /**
     * 旧帧载荷长度：优先按「length 含 4 字节 packetId」，用下一帧边界校验；
     * 若只有「length 就是载荷长度」能接上，则用后者。
     */
    private static int chooseLegacyPayloadLength(byte[] data, int position, int length) {
        if (length < 0) {
            return -1;
        }
        int includesId = length - 4;
        int excludesId = length;
        boolean includesValid = includesId >= 0 && validLegacyBoundary(data, position, includesId);
        boolean excludesValid = validLegacyBoundary(data, position, excludesId);
        if (includesValid && !excludesValid) {
            return includesId;
        }
        if (excludesValid && !includesValid) {
            return excludesId;
        }
        if (includesValid) {
            return includesId;
        }
        // 下一帧被截断时保留当前帧，截断在下一轮循环标记
        if (includesId >= 0 && (long) position + 4 + length <= data.length) {
            return includesId;
        }
        if ((long) position + 8 + length <= data.length) {
            return excludesId;
        }
        return -1;
    }

    private static boolean validLegacyBoundary(byte[] data, int position, int payloadLength) {
        long next = (long) position + 8 + payloadLength;
        if (next > data.length) {
            return false;
        }
        if (next == data.length) {
            return true;
        }
        if (data.length - next < 8) {
            return false;
        }
        int nextPosition = (int) next;
        return plausibleLegacyLength(data, nextPosition, readIntLE(data, nextPosition))
                || plausibleLegacyLength(data, nextPosition, readIntBE(data, nextPosition));
    }

    private static boolean plausibleLegacyLength(byte[] data, int position, int length) {
        if (length < 0) {
            return false;
        }
        // 下一帧的 length 可能含 packetId，也可能只是载荷；两种都算合理
        boolean includesId = length >= 4 && (long) position + 4 + length <= data.length;
        boolean excludesId = (long) position + 8 + length <= data.length;
        return includesId || excludesId;
    }

    /** 带时间戳帧的载荷长度：length 可能含 varint 包 id 的字节，也可能只是载荷。 */
    private static int chooseTimedPayloadLength(byte[] data, int frameStart, int payloadStart,
                                                int length) {
        int includesId = frameStart + length - payloadStart;
        int excludesId = length;
        boolean includesValid = includesId >= 0 && validTimedBoundary(data, frameStart + length);
        boolean excludesValid = validTimedBoundary(data, payloadStart + length);
        if (includesValid && !excludesValid) {
            return includesId;
        }
        if (excludesValid && !includesValid) {
            return excludesId;
        }
        if (includesValid) {
            return includesId;
        }
        // 下一帧被截断时两种严格边界都校验不过；当前帧自身放得下就按含 id 的语义保留，
        // 截断留给下一轮循环标记
        if (includesId >= 0 && (long) frameStart + length <= data.length) {
            return includesId;
        }
        if ((long) payloadStart + length <= data.length) {
            return excludesId;
        }
        return -1;
    }

    private static boolean validTimedBoundary(byte[] data, int next) {
        if (next < 0 || next > data.length) {
            return false;
        }
        if (next == data.length) {
            return true;
        }
        if (data.length - next < 8) {
            return false;
        }
        int nextLength = readIntBE(data, next + 4);
        return nextLength > 0 && (long) next + 8 + nextLength <= data.length;
    }

    private static int readIntLE(byte[] data, int position) {
        return (data[position] & 0xFF)
                | ((data[position + 1] & 0xFF) << 8)
                | ((data[position + 2] & 0xFF) << 16)
                | ((data[position + 3] & 0xFF) << 24);
    }

    private static int readIntBE(byte[] data, int position) {
        return ((data[position] & 0xFF) << 24)
                | ((data[position + 1] & 0xFF) << 16)
                | ((data[position + 2] & 0xFF) << 8)
                | (data[position + 3] & 0xFF);
    }

    private static VarInt readVarInt(byte[] data, int position) {
        int value = 0;
        int shift = 0;
        int cursor = position;
        while (cursor < data.length && shift < 35) {
            int current = data[cursor++] & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return new VarInt(value, cursor);
            }
            shift += 7;
        }
        return null;
    }

    private record VarInt(int value, int next) {
    }

    private record FrameParse(List<PacketRecord> packets, boolean truncated) {

        private FrameParse {
            packets = List.copyOf(packets);
        }
    }
}
