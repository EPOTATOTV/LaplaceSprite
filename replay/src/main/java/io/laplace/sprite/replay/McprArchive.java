package io.laplace.sprite.replay;

import java.util.List;
import java.util.Objects;

/**
 * 解析后的 ReplayMod 录像。
 *
 * @param metadata      metaData.json
 * @param packets       录制流中的帧
 * @param sizeBytes     .mcpr 文件字节数
 * @param durationTicks 录制时长（tick）
 * @param mcVersion     Minecraft 版本
 * @param generator     录制器标识
 * @param playerName    录制者标识（.mcpr 只存 UUID）
 * @param truncated     录制流是否在末尾被截断
 */
public record McprArchive(McprMetadata metadata, List<PacketRecord> packets, long sizeBytes,
                          int durationTicks, String mcVersion, String generator, String playerName,
                          boolean truncated) {

    public McprArchive {
        metadata = Objects.requireNonNull(metadata, "metadata");
        packets = packets == null ? List.of() : List.copyOf(packets);
        mcVersion = mcVersion == null ? "" : mcVersion;
        generator = generator == null ? "" : generator;
        playerName = playerName == null ? "" : playerName;
        if (sizeBytes < 0 || durationTicks < 0) {
            throw new IllegalArgumentException("文件大小与时长不能为负数");
        }
    }

    public int packetCount() {
        return packets.size();
    }
}
