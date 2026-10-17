package io.laplace.sprite.replay;

import java.util.List;

/**
 * 聊天 / 指令轨迹（15.10）。
 *
 * @param entries 按 tick 升序的记录
 */
public record CommandTrack(List<CommandEntry> entries) {

    public CommandTrack {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public int lastTick() {
        return entries.isEmpty() ? 0 : entries.get(entries.size() - 1).tick();
    }
}
