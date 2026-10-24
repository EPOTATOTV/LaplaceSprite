package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.adapters.ecosystem.MatrixAlertNotifier.Alert;
import io.laplace.sprite.adapters.ecosystem.MatrixAlertNotifier.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 矩阵告警（L2-09）：健康度下降、版本过期、样本过期与 Webhook 载荷。 */
class MatrixAlertNotifierTest {

    private static final Instant NOW = Instant.parse("2026-10-23T00:00:00Z");

    @Test
    void detectsHealthDropVersionAndSampleExpiry(@TempDir Path tempDir) {
        MatrixHealthRegistry registry = new MatrixHealthRegistry(tempDir.resolve("health.json"));
        registry.record("fabric/1.21.11/0.19.5", false);
        registry.record("fabric/1.21.11/0.19.5", false);
        registry.registerSample("speed.truthfulac.bypass", NOW.minus(Duration.ofDays(200)));

        List<Alert> alerts = MatrixAlertNotifier.evaluate(registry, Set.of("26.3", "26.2"), NOW,
                Duration.ofDays(180));

        assertTrue(alerts.stream().anyMatch(alert -> alert.kind() == Kind.HEALTH_DROP
                && alert.subject().equals("fabric/1.21.11/0.19.5")));
        assertTrue(alerts.stream().anyMatch(alert -> alert.kind() == Kind.VERSION_EXPIRED
                && alert.subject().equals("fabric/1.21.11/0.19.5")));
        assertTrue(alerts.stream().anyMatch(alert -> alert.kind() == Kind.SAMPLE_EXPIRED
                && alert.subject().equals("speed.truthfulac.bypass")));
    }

    @Test
    void healthyCellsAndFreshSamplesProduceNoAlert(@TempDir Path tempDir) {
        MatrixHealthRegistry registry = new MatrixHealthRegistry(tempDir.resolve("health.json"));
        registry.record("fabric/26.3/0.22.0", true);
        registry.registerSample("speed.wurst.default", NOW.minus(Duration.ofDays(10)));

        assertTrue(MatrixAlertNotifier.evaluate(registry, Set.of("26.3"), NOW, Duration.ofDays(180)).isEmpty());
    }

    @Test
    void dispatchPostsStructuredPayload() {
        List<String> sent = new java.util.ArrayList<>();
        Alert alert = new Alert(Kind.VERSION_EXPIRED, "fabric/1.21.11/0.19.5", "版本已不在矩阵内");

        MatrixAlertNotifier.dispatch("https://hooks.example.invalid/laplace", List.of(alert),
                (url, payload) -> {
                    assertEquals("https://hooks.example.invalid/laplace", url);
                    sent.add(payload);
                });

        assertEquals(1, sent.size());
        assertTrue(sent.get(0).contains("\"kind\" : \"VERSION_EXPIRED\""), sent.get(0));
        assertTrue(sent.get(0).contains("fabric/1.21.11/0.19.5"), sent.get(0));
    }

    @Test
    void nonHttpWebhookIsRejectedBeforeSending() {
        assertThrows(IllegalArgumentException.class, () -> MatrixAlertNotifier.dispatch(
                "file:///tmp/hook", List.of(new Alert(Kind.HEALTH_DROP, "cell", "x")),
                (url, payload) -> {
                    throw new AssertionError("不应发送");
                }));
    }
}
