package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.LiveViewGrid;
import io.laplace.sprite.core.model.LiveViewSession;
import io.laplace.sprite.core.model.LiveViewStatus;
import io.laplace.sprite.core.model.LiveViewTarget;
import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.core.model.PerfOverlay;
import io.laplace.sprite.core.model.ScreenShareSession;
import io.laplace.sprite.servers.signaling.InspectMessages.InspectAnswer;
import io.laplace.sprite.servers.signaling.InspectMessages.InspectIce;
import io.laplace.sprite.servers.signaling.InspectMessages.InspectOffer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一键查看与画面监控墙（11.8）。
 *
 * <p>信令走可配置的 WSS 通道，消息命名对齐 inspect_offer / inspect_answer / inspect_ice；
 * 首帧预算 3 秒（12.6）与并发路数下限 8 路都在这里显式约束并对外可查。</p>
 */
public final class LiveViewService {

    /** 首帧预算（12.6）。 */
    public static final long FIRST_FRAME_BUDGET_MS = 3000L;
    /** 画面墙下限（12.6）：至少 8 路稳定。 */
    public static final int MIN_CONCURRENT_STREAMS = 8;
    /** 默认并发上限：留出余量，超过就明确报错而不是静默丢流。 */
    public static final int DEFAULT_MAX_STREAMS = 16;
    /** 默认首帧耗时（离线模拟值，必须落在预算内）。 */
    public static final long DEFAULT_FIRST_FRAME_MS = 320L;

    private final Map<String, MetricsAgent> metrics;
    private final String signalingChannel;
    private final long firstFrameMs;
    private final int maxStreams;
    private final Map<String, LiveViewSession> sessions = new LinkedHashMap<>();
    private final List<String> signalingLog = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public LiveViewService(Map<String, MetricsAgent> metrics) {
        this(metrics, "wss://laplace.local/live-view/signaling", DEFAULT_FIRST_FRAME_MS, DEFAULT_MAX_STREAMS);
    }

    public LiveViewService(Map<String, MetricsAgent> metrics, String signalingChannel, long firstFrameMs,
                           int maxStreams) {
        this.metrics = Map.copyOf(metrics);
        this.signalingChannel = signalingChannel;
        this.firstFrameMs = firstFrameMs;
        this.maxStreams = maxStreams;
    }

    public String signalingChannel() {
        return signalingChannel;
    }

    public long firstFrameBudgetMs() {
        return FIRST_FRAME_BUDGET_MS;
    }

    /** 发起查看：分配会话并给出模拟首帧耗时。 */
    public synchronized LiveViewSession start(LiveViewTarget target) {
        if (sessions.size() >= maxStreams) {
            throw new IllegalStateException("并发画面数已达上限 %d（12.6 要求至少支持 %d 路）"
                    .formatted(maxStreams, MIN_CONCURRENT_STREAMS));
        }
        String sessionId = "lv-" + sequence.incrementAndGet();
        ScreenShareSession share = new ScreenShareSession(sessionId, null, null, List.of(),
                signalingChannel, LiveViewStatus.IDLE);
        LiveViewSession session = new LiveViewSession(sessionId, target, share, Instant.now(), firstFrameMs);
        sessions.put(sessionId, session);
        return session;
    }

    /** 协商：产出 SDP offer，并把 inspect_offer 写入信令日志。 */
    public synchronized ScreenShareSession negotiate(String sessionId, String channel) {
        LiveViewSession session = require(sessionId);
        String offer = offerSdp(sessionId, session.target());
        String channelPath = channel == null || channel.isBlank() ? signalingChannel : channel;
        ScreenShareSession share = ScreenShareSession.negotiate(sessionId, channelPath, offer);
        sessions.put(sessionId, replace(session, share));
        signalingLog.add(InspectOffer.of(sessionId, session.target().simId(), offer).toJson());
        return share;
    }

    /** 应答：收下 answer 与 ICE，会话转 LIVE。 */
    public synchronized ScreenShareSession answer(String sessionId, String answerSdp, List<String> ice) {
        LiveViewSession session = require(sessionId);
        ScreenShareSession share = session.share().answered(answerSdp, ice);
        sessions.put(sessionId, replace(session, share));
        signalingLog.add(InspectAnswer.of(sessionId, answerSdp, ice).toJson());
        signalingLog.add(InspectIce.of(sessionId, ice).toJson());
        return share;
    }

    public synchronized LiveViewGrid grid() {
        return new LiveViewGrid(new ArrayList<>(sessions.values()));
    }

    /** 性能悬浮窗（11.8 / 12.5）：取该实例当前采样，拐点自动置位并触发录制。 */
    public PerfOverlay overlay(String instanceId, int latencyMs) {
        MetricsAgent agent = metrics.get(instanceId);
        MetricSample sample = agent == null
                ? SyntheticProfile.steady().sample(0, Instant.now())
                : agent.current();
        return PerfOverlay.from(instanceId, sample, latencyMs);
    }

    public synchronized LiveViewStatus status(String sessionId) {
        return require(sessionId).share().status();
    }

    public synchronized void close(String sessionId) {
        LiveViewSession session = require(sessionId);
        ScreenShareSession share = session.share();
        ScreenShareSession closed = new ScreenShareSession(sessionId, share.offerSdp(), share.answerSdp(),
                share.iceCandidates(), share.signalingChannel(), LiveViewStatus.CLOSED);
        sessions.put(sessionId, replace(session, closed));
    }

    public synchronized boolean meetsLatencyBudget(String sessionId) {
        return require(sessionId).meetsLatencyBudget();
    }

    public List<String> signalingLog() {
        return List.copyOf(signalingLog);
    }

    public synchronized int sessionCount() {
        return sessions.size();
    }

    private LiveViewSession require(String sessionId) {
        LiveViewSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("未知 Live View 会话: " + sessionId);
        }
        return session;
    }

    private static LiveViewSession replace(LiveViewSession session, ScreenShareSession share) {
        return new LiveViewSession(session.sessionId(), session.target(), share, session.startedAt(),
                session.timeToFirstFrameMs());
    }

    /** 合成 SDP offer：离线可复现，字段足够核对会话与目标。 */
    private static String offerSdp(String sessionId, LiveViewTarget target) {
        return "v=0\r\n"
                + "o=laplace %s 0 IN IP4 127.0.0.1\r\n".formatted(sessionId)
                + "s=Laplace Live View\r\n"
                + "t=0 0\r\n"
                + "a=target:%s\r\n".formatted(target.simId())
                + "a=network:%s\r\n".formatted(target.networkCondition())
                + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n";
    }
}
