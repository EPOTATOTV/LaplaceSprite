package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.LiveViewStatus;
import io.laplace.sprite.core.model.LiveViewTarget;
import io.laplace.sprite.core.model.PerfOverlay;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Live View（11.8 / 12.6）：首帧预算、并发路数、信令形状、性能悬浮窗。 */
class LiveViewServiceTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");

    @Test
    void defaultSessionMeetsThreeSecondFirstFrameBudget() {
        LiveViewService service = new LiveViewService(Map.of());
        var session = service.start(LiveViewTarget.adminSpectator("alpha", PAPER));
        assertTrue(session.meetsLatencyBudget(), "默认应在 3 秒预算内出画");
        assertTrue(service.meetsLatencyBudget(session.sessionId()));
        assertEquals(3000L, service.firstFrameBudgetMs());
        assertEquals(LiveViewStatus.IDLE, service.status(session.sessionId()));
    }

    @Test
    void slowFirstFrameIsReportedAsBudgetBreach() {
        LiveViewService service = new LiveViewService(Map.of(), "wss://laplace.local/live-view", 4000L, 8);
        var session = service.start(LiveViewTarget.player("alpha", "SimID-1", "wifi", PAPER));
        assertFalse(session.meetsLatencyBudget());
        assertFalse(service.meetsLatencyBudget(session.sessionId()));
    }

    @Test
    void negotiateAndAnswerDriveSessionToLive() {
        LiveViewService service = new LiveViewService(Map.of());
        var session = service.start(LiveViewTarget.adminSpectator("alpha", PAPER));
        var offer = service.negotiate(session.sessionId(), "wss://laplace.local/live-view/signaling");
        assertTrue(offer.offerSdp().startsWith("v=0"));
        assertEquals(LiveViewStatus.CONNECTING, service.status(session.sessionId()));

        var answer = service.answer(session.sessionId(), "v=0\\r\\ns=answer", List.of("candidate:1 udp"));
        assertEquals(LiveViewStatus.LIVE, answer.status());
        assertEquals(1L, service.grid().liveCount());
    }

    @Test
    void supportsAtLeastEightConcurrentStreams() {
        LiveViewService service = new LiveViewService(Map.of());
        List<String> sessions = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            var session = service.start(LiveViewTarget.player("instance-" + index, "SimID-" + index,
                    "wifi", PAPER));
            sessions.add(session.sessionId());
        }
        for (String sessionId : sessions) {
            service.negotiate(sessionId, null);
            service.answer(sessionId, "v=0", List.of("candidate:1 udp"));
        }
        assertEquals(10, service.sessionCount());
        assertTrue(service.grid().liveCount() >= 8, "画面墙至少 8 路稳定");
        assertEquals(10, service.grid().sessions().size());
    }

    @Test
    void exceedingMaxStreamsFailsLoudly() {
        LiveViewService service = new LiveViewService(Map.of(), "wss://laplace.local/live-view", 320L, 8);
        for (int index = 0; index < 8; index++) {
            service.start(LiveViewTarget.adminSpectator("instance-" + index, PAPER));
        }
        assertThrows(IllegalStateException.class,
                () -> service.start(LiveViewTarget.adminSpectator("overflow", PAPER)));
    }

    @Test
    void signalingMessagesMirrorPaccShapes() {
        LiveViewService service = new LiveViewService(Map.of());
        var session = service.start(LiveViewTarget.adminSpectator("alpha", PAPER));
        service.negotiate(session.sessionId(), null);
        service.answer(session.sessionId(), "v=0", List.of("candidate:1 udp"));
        List<String> messages = service.signalingLog();
        assertEquals(3, messages.size());
        assertTrue(messages.get(0).contains("inspect_offer"));
        assertTrue(messages.get(1).contains("inspect_answer"));
        assertTrue(messages.get(2).contains("inspect_ice"));
        assertEquals("wss://laplace.local/live-view/signaling", service.signalingChannel());
    }

    @Test
    void overlayRaisesKneeAlertAndTriggersRecording() {
        MetricsAgent breached = MetricsAgent.synthetic("alpha", SyntheticProfile.msptRamp());
        for (int step = 0; step < 8; step++) {
            breached.sampleNow();
        }
        LiveViewService service = new LiveViewService(Map.of("alpha", breached));
        PerfOverlay overlay = service.overlay("alpha", 42);
        assertTrue(overlay.kneeAlert(), "拐点必须告警");
        assertTrue(overlay.recordingTriggered(), "拐点必须触发录制");
        assertEquals(42, overlay.latencyMs());

        LiveViewService healthy = new LiveViewService(Map.of());
        PerfOverlay steady = healthy.overlay("unknown", 30);
        assertFalse(steady.kneeAlert());
    }

    @Test
    void closeMarksSessionClosedAndUnknownSessionFails() {
        LiveViewService service = new LiveViewService(Map.of());
        var session = service.start(LiveViewTarget.adminSpectator("alpha", PAPER));
        service.close(session.sessionId());
        assertEquals(LiveViewStatus.CLOSED, service.status(session.sessionId()));
        assertEquals(0L, service.grid().liveCount());
        assertThrows(IllegalArgumentException.class, () -> service.status("lv-missing"));
    }
}
