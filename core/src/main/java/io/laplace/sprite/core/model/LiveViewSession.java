package io.laplace.sprite.core.model;

import java.time.Instant;

/**
 * Live View 会话（11.8 一键查看：LiveViewSession.start(target)，≤3s 出画）。
 *
 * @param sessionId         会话 id
 * @param target            查看目标
 * @param share             屏幕共享会话
 * @param startedAt         发起时间
 * @param timeToFirstFrameMs 首帧耗时
 */
public record LiveViewSession(String sessionId, LiveViewTarget target, ScreenShareSession share,
                              Instant startedAt, long timeToFirstFrameMs) {

    /** 是否满足「≤3s 出画」（12.6）。 */
    public boolean meetsLatencyBudget() {
        return timeToFirstFrameMs > 0 && timeToFirstFrameMs <= 3000;
    }

    public LiveViewSession withFirstFrame(long ms) {
        return new LiveViewSession(sessionId, target, share, startedAt, ms);
    }
}
