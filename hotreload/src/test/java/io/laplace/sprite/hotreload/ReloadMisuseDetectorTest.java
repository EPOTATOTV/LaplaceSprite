package io.laplace.sprite.hotreload;

import io.laplace.sprite.hotreload.ReloadMisuseDetector.MisuseFinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 禁忌标记（15.2）：被测插件用 reload API 热插拔时，该轮不进发布门禁。 */
class ReloadMisuseDetectorTest {

    private final ReloadMisuseDetector detector = new ReloadMisuseDetector();

    @Test
    void reloadApiUsageIsDetected() {
        List<String> logs = List.of(
                "[12:00:00] [Server thread/INFO]: Enabling ExamplePlugin v1.0.0",
                "[12:00:01] [Server thread/WARN]: org.bukkit.plugin.PluginManager.reload called by example-plugin",
                "[12:00:02] [Server thread/INFO]: /reload confirm executed");

        MisuseFinding finding = detector.inspect(logs);

        assertTrue(finding.misused());
        assertTrue(finding.evidence().size() >= 2);
        assertTrue(detector.misused(logs));
    }

    @Test
    void cleanLogsAreNotFlagged() {
        assertFalse(detector.misused(List.of("[12:00:00] [Server thread/INFO]: Done (3.1s)! For help, type \"help\"")));
        assertFalse(detector.misused(List.of()));
        assertFalse(detector.misused(null));
        assertFalse(detector.inspect(null).misused());
        assertTrue(detector.inspect(null).evidence().isEmpty());
    }

    @Test
    void misuseBlocksReleaseGate() {
        HotReloadReport report = new HotReloadCapability().evaluate("change-1", JvmKind.HOTSPOT,
                ChangeKind.METHOD_BODY);
        assertTrue(report.releaseGateAllowed());

        HotReloadReport marked = detector.apply(report, List.of("JavaPluginLoader 重新加载插件类"));

        assertTrue(marked.marked());
        assertEquals("⚠ 非真实运行态", marked.marker());
        assertEquals(ReloadMisuseDetector.MARKER, marked.marker());
        assertTrue(marked.releaseGateBlocked());
        assertFalse(marked.releaseGateAllowed(), "挂禁忌标记的轮次不进发布门禁");

        assertEquals(report, detector.apply(report, List.of("clean line")));
        assertThrows(IllegalArgumentException.class, () -> report.withMisuse(" "));
    }
}
