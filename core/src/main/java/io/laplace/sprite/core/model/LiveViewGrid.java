package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 画面监控墙（11.8 LiveViewGrid）：多路画面并排，≥8 路稳定（12.6）。
 */
public record LiveViewGrid(List<LiveViewSession> sessions) {

    public LiveViewGrid {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    public static LiveViewGrid empty() {
        return new LiveViewGrid(List.of());
    }

    public LiveViewGrid add(LiveViewSession session) {
        var next = new java.util.ArrayList<>(sessions);
        next.add(session);
        return new LiveViewGrid(List.copyOf(next));
    }

    public long liveCount() {
        return sessions.stream().filter(s -> s.share().status() == LiveViewStatus.LIVE).count();
    }
}
