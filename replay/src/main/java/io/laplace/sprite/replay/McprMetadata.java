package io.laplace.sprite.replay;

import java.util.List;

/**
 * {@code metaData.json} 的字段（ReplayMod 真实字段名）。
 *
 * <p>{@code duration} 的单位是毫秒，不是 tick；{@code players} 存的是 UUID，不是显示名。
 * 字段缺失时给空值，不做猜测。</p>
 *
 * @param singleplayer     是否单人生成
 * @param serverName       服务器名
 * @param duration         录制时长（毫秒）
 * @param date             录制时间（Unix 毫秒）
 * @param mcversion        Minecraft 版本
 * @param fileFormat       文件格式标识
 * @param fileFormatVersion 文件格式版本
 * @param protocol         网络协议号
 * @param generator        录制器标识
 * @param players          参与者 UUID 列表
 * @param selfId           录制者实体 id 或 UUID 字符串
 */
public record McprMetadata(boolean singleplayer, String serverName, int duration, long date,
                           String mcversion, String fileFormat, int fileFormatVersion, int protocol,
                           String generator, List<String> players, String selfId) {

    public McprMetadata {
        serverName = serverName == null ? "" : serverName;
        mcversion = mcversion == null ? "" : mcversion;
        fileFormat = fileFormat == null ? "" : fileFormat;
        generator = generator == null ? "" : generator;
        players = players == null ? List.of() : List.copyOf(players);
        selfId = selfId == null ? "" : selfId;
    }

    /** 毫秒按 20 TPS 折算成 tick。 */
    public int durationTicks() {
        return duration <= 0 ? 0 : duration / 50;
    }

    /** 录制者标识：ReplayMod 只存 UUID，取 players[0]，缺失时退回 selfId。 */
    public String selfPlayer() {
        if (!players.isEmpty()) {
            return players.get(0);
        }
        return selfId.isBlank() ? "unknown" : selfId;
    }
}
