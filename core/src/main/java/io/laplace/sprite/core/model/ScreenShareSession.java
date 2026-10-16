package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 屏幕共享会话（11.8 画面流）：SDP offer / answer + ICE，信令经平台 WSS。
 * 协议形态对齐 通用查端协议 inspect_offer / inspect_answer / inspect_ice，由 Laplace 侧独立实现。
 *
 * @param sessionId        会话 id
 * @param offerSdp         提议 SDP
 * @param answerSdp        应答 SDP
 * @param iceCandidates    ICE 候选
 * @param signalingChannel 信令通道（WSS 路径）
 * @param status           状态
 */
public record ScreenShareSession(String sessionId, String offerSdp, String answerSdp,
                                 List<String> iceCandidates, String signalingChannel,
                                 LiveViewStatus status) {

    public ScreenShareSession {
        iceCandidates = iceCandidates == null ? List.of() : List.copyOf(iceCandidates);
    }

    public static ScreenShareSession negotiate(String sessionId, String signalingChannel, String offerSdp) {
        return new ScreenShareSession(sessionId, offerSdp, null, List.of(), signalingChannel,
                LiveViewStatus.CONNECTING);
    }

    public ScreenShareSession answered(String newAnswerSdp, List<String> newIce) {
        return new ScreenShareSession(sessionId, offerSdp, newAnswerSdp, newIce, signalingChannel,
                LiveViewStatus.LIVE);
    }
}
