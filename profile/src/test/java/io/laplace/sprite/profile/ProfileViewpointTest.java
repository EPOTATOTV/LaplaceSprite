package io.laplace.sprite.profile;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 视角切分（15.3）：反作弊开发者先看网络与数据包线程，模组作者先看 tick 线程。 */
class ProfileViewpointTest {

    private final ProfileReport report = ProfileCollector.offline("inst").collect("inst");

    @Test
    void detectionDeveloperLooksAtPacketHandlingFirst() {
        ProfileView view = report.forViewpoint(ProfileViewpoint.DETECTION_DEVELOPER);

        assertEquals(ProfileViewpoint.DETECTION_DEVELOPER, view.viewpoint());
        assertSame(report, view.report());
        assertTrue(view.primaryThread().contains("Netty"));
        assertTrue(view.threadFocus().get(0).contains("Netty"));
        assertTrue(view.surfacesPrimary(ProfileView.PACKET_THREAD));
        assertTrue(view.surfacesPrimary(ProfileView.NETWORK_IO));
        assertFalse(view.surfacesPrimary(ProfileView.TICK_HEALTH));
    }

    @Test
    void modAuthorLooksAtServerTickThreadFirst() {
        ProfileView view = report.forViewpoint(ProfileViewpoint.MOD_AUTHOR);

        assertEquals(ProfileViewpoint.MOD_AUTHOR, view.viewpoint());
        assertEquals("Server thread（tick 主线程）", view.primaryThread());
        assertEquals(ProfileView.TICK_HEALTH, view.primaryMetrics().get(0));
        assertTrue(view.surfacesPrimary(ProfileView.SPIKE_TICKS));
        assertFalse(view.surfacesPrimary(ProfileView.NETWORK_IO));
    }

    @Test
    void bothViewpointsCoverTheSameMetricSet() {
        ProfileView detection = report.forViewpoint(ProfileViewpoint.DETECTION_DEVELOPER);
        ProfileView mod = report.forViewpoint(ProfileViewpoint.MOD_AUTHOR);

        Set<String> detectionAll = new LinkedHashSet<>(detection.primaryMetrics());
        detectionAll.addAll(detection.deferredMetrics());
        Set<String> modAll = new LinkedHashSet<>(mod.primaryMetrics());
        modAll.addAll(mod.deferredMetrics());

        assertEquals(detectionAll, modAll, "两个视角覆盖同一批指标，只是排序不同");
        assertNotEquals(detection.primaryMetrics(), mod.primaryMetrics(), "首要指标顺序必须不同");
        assertTrue(detectionAll.containsAll(List.of(ProfileView.TICK_HEALTH, ProfileView.JVM_RESOURCES,
                ProfileView.SPIKE_TICKS, ProfileView.ATTRIBUTION, ProfileView.NETWORK_IO, ProfileView.PACKET_THREAD)));
    }
}
