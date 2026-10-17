package io.laplace.sprite.replay;

import java.util.List;

/**
 * 从 .mcpr 抽出的可重放脚本（15.10）。
 *
 * @param sourceMcpr      源录像
 * @param mcVersion       Minecraft 版本
 * @param worldSnapshotRef 世界快照引用（四元组标签或快照路径）
 * @param playerName      被抽取的玩家标识
 * @param movement        移动轨迹
 * @param commands        聊天 / 指令轨迹
 */
public record ReplayScript(String sourceMcpr, String mcVersion, String worldSnapshotRef,
                           String playerName, PlayerMovementTrack movement, CommandTrack commands) {

    public ReplayScript {
        sourceMcpr = sourceMcpr == null ? "" : sourceMcpr;
        mcVersion = mcVersion == null ? "" : mcVersion;
        worldSnapshotRef = worldSnapshotRef == null ? "" : worldSnapshotRef;
        playerName = playerName == null ? "" : playerName;
        movement = movement == null ? new PlayerMovementTrack(List.of()) : movement;
        commands = commands == null ? new CommandTrack(List.of()) : commands;
    }

    /** 重放时长取轨迹与指令的最后 tick，至少 1。 */
    public int durationTicks() {
        return Math.max(1, Math.max(movement.lastTick(), commands.lastTick()));
    }
}
