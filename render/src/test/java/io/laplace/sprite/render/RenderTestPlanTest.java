package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户端 GameTest 调用渲染（15.12 P0）：只渲染形态，不跑真实客户端。 */
class RenderTestPlanTest {

    private final RenderTestPlan plan = new RenderTestPlan("./gradlew");

    @Test
    void plainInvocationOmitsXvfbAndSynchronizerWorkaround() {
        String rendered = String.join(" ", plan.render(false, false));

        assertTrue(rendered.contains(RenderTestPlan.TASK));
        assertTrue(rendered.contains("./gradlew"));
        assertFalse(rendered.contains("useXVFB"));
        assertFalse(rendered.contains("disableNetworkSynchronizer"));
    }

    @Test
    void xvfbAndSynchronizerAreRenderedOnlyWhenRequired() {
        String rendered = String.join(" ", plan.render(true, true));

        assertTrue(rendered.contains(RenderTestPlan.TASK));
        assertTrue(rendered.contains("useXVFB"));
        assertTrue(rendered.contains(RenderTestPlan.SYNC_WORKAROUND_PROPERTY));
    }

    @Test
    void syncWorkaroundDetectedFromLog() {
        assertTrue(RenderTestPlan.syncWorkaroundRequired(
                "java.lang.IllegalStateException: Network synchronizer failed after 30s"));
        assertFalse(RenderTestPlan.syncWorkaroundRequired("[main/INFO] client started"));
        assertFalse(RenderTestPlan.syncWorkaroundRequired(null));
    }

    @Test
    void screenshotArtifactDirectoryMatchesLoomLayout() {
        Path dir = plan.screenshotArtifactDir(Path.of("module"));

        assertEquals("module/build/run/clientGameTest/screenshots",
                dir.toString().replace('\\', '/'));
        assertEquals("build/run/clientGameTest/screenshots", RenderTestPlan.SCREENSHOT_ARTIFACT_PATH);
    }
}
