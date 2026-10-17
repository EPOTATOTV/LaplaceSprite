package io.laplace.sprite.loadtest;

import java.util.Objects;

/**
 * 一个已拉起的 bot 会话。
 *
 * @param id      会话 id
 * @param backend 所属后端
 * @param live    是否真的建立了连接；内存替身固定为 false
 * @param note    会话说明
 */
public record BotSession(String id, BotBackend backend, boolean live, String note) {

    public BotSession {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("会话 id 不能为空");
        }
        backend = Objects.requireNonNull(backend, "backend");
        note = note == null ? "" : note;
    }
}
