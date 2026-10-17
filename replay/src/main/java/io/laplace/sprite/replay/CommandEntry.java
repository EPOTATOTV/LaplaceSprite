package io.laplace.sprite.replay;

/**
 * 一条聊天或指令记录。
 *
 * @param tick    tick
 * @param text    文本或指令内容
 * @param command 是否为指令包；false 表示聊天包
 */
public record CommandEntry(int tick, String text, boolean command) {

    public CommandEntry {
        if (tick < 0) {
            throw new IllegalArgumentException("tick 不能为负数: " + tick);
        }
        text = text == null ? "" : text;
    }
}
