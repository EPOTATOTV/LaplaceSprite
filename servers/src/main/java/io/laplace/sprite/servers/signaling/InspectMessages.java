package io.laplace.sprite.servers.signaling;

import io.laplace.sprite.core.io.Json;

import java.util.List;

/**
 * 信令消息（11.8 画面流）：形状对齐 通用查端协议的 inspect_offer / inspect_answer / inspect_ice，
 * 由 Laplace 侧独立实现，不依赖对端实现细节。
 */
public final class InspectMessages {

    private InspectMessages() {
    }

    /** 提议：{@code {"type":"inspect_offer", ...}}。 */
    public record InspectOffer(String type, String sessionId, String target, String sdp) {

        public static InspectOffer of(String sessionId, String target, String sdp) {
            return new InspectOffer("inspect_offer", sessionId, target, sdp);
        }

        public String toJson() {
            return Json.write(this);
        }
    }

    /** 应答：{@code {"type":"inspect_answer", ...}}。 */
    public record InspectAnswer(String type, String sessionId, String sdp, List<String> ice) {

        public InspectAnswer {
            ice = ice == null ? List.of() : List.copyOf(ice);
        }

        public static InspectAnswer of(String sessionId, String sdp, List<String> ice) {
            return new InspectAnswer("inspect_answer", sessionId, sdp, ice);
        }

        public String toJson() {
            return Json.write(this);
        }
    }

    /** ICE 候选：{@code {"type":"inspect_ice", ...}}。 */
    public record InspectIce(String type, String sessionId, List<String> candidates) {

        public InspectIce {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        public static InspectIce of(String sessionId, List<String> candidates) {
            return new InspectIce("inspect_ice", sessionId, candidates);
        }

        public String toJson() {
            return Json.write(this);
        }
    }

    /**
     * 信令通道（WSS 路径可配置）。
     *
     * @param host 信令主机
     * @param path 通道路径
     */
    public record SignalingChannel(String host, String path) {

        public static SignalingChannel defaults() {
            return new SignalingChannel("laplace.local", "/live-view/signaling");
        }

        public String wssUrl() {
            return "wss://%s%s".formatted(host, path);
        }
    }
}
