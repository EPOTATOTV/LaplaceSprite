package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 矩阵健康度告警（升级实施文档 v2 L2-09）：健康度下降、版本过期、样本过期三类事件走 Webhook。
 *
 * <p>告警只描述事实，不带修复建议：维护者需要知道哪一格失败、哪个版本已不在矩阵内、
 * 哪个样本超过核对时限。发送失败直接抛出，由 CLI 如实打印，不吞掉。</p>
 */
public final class MatrixAlertNotifier {

    /** 告警类型。 */
    public enum Kind {
        HEALTH_DROP,
        VERSION_EXPIRED,
        SAMPLE_EXPIRED
    }

    /**
     * 一条告警。
     *
     * @param kind    类型
     * @param subject 主体（格 id / 版本 / 样本 id）
     * @param detail  说明
     */
    public record Alert(Kind kind, String subject, String detail) {

        public Alert {
            Objects.requireNonNull(kind, "kind");
            subject = subject == null ? "" : subject;
            detail = detail == null ? "" : detail;
        }
    }

    /** Webhook 发送接缝；测试用它替换真实 HTTP。 */
    @FunctionalInterface
    public interface Sender {
        void send(String webhookUrl, String payload);
    }

    private MatrixAlertNotifier() {
    }

    /**
     * 基于健康度快照生成告警。
     *
     * @param registry                健康度
     * @param activeMinecraftVersions 当前矩阵仍在维护的 MC 版本；空集表示不判版本过期
     * @param now                     当前时间
     * @param sampleTtl               样本核对时限
     */
    public static List<Alert> evaluate(MatrixHealthRegistry registry, Set<String> activeMinecraftVersions,
                                       Instant now, Duration sampleTtl) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(sampleTtl, "sampleTtl");
        List<Alert> alerts = new ArrayList<>();

        for (MatrixHealthRegistry.CellHealth cell : registry.health()) {
            if (cell.consecutiveFailures() >= MatrixHealthRegistry.PROMOTE_AFTER_FAILURES) {
                alerts.add(new Alert(Kind.HEALTH_DROP, cell.cellId(),
                        "连续失败 %d 次，已升回每次必跑".formatted(cell.consecutiveFailures())));
            }
        }

        if (activeMinecraftVersions != null && !activeMinecraftVersions.isEmpty()) {
            for (MatrixHealthRegistry.CellHealth cell : registry.health()) {
                String minecraft = minecraftOf(cell.cellId());
                if (!minecraft.isBlank() && !activeMinecraftVersions.contains(minecraft)) {
                    alerts.add(new Alert(Kind.VERSION_EXPIRED, cell.cellId(),
                            "版本 " + minecraft + " 已不在维护矩阵内"));
                }
            }
        }

        for (MatrixHealthRegistry.SampleHealth sample : registry.samples()) {
            if (sample.updatedAt().plus(sampleTtl).isBefore(now)) {
                alerts.add(new Alert(Kind.SAMPLE_EXPIRED, sample.sampleId(),
                        "样本超过 %d 天未核对".formatted(sampleTtl.toDays())));
            }
        }
        return List.copyOf(alerts);
    }

    /** 用真实 HTTP POST 发送告警；空 URL 或空告警时直接返回。 */
    public static void dispatch(String webhookUrl, List<Alert> alerts) {
        dispatch(webhookUrl, alerts, MatrixAlertNotifier::post);
    }

    /** 可注入 Sender 的发送入口。 */
    public static void dispatch(String webhookUrl, List<Alert> alerts, Sender sender) {
        Objects.requireNonNull(sender, "sender");
        if (webhookUrl == null || webhookUrl.isBlank() || alerts == null || alerts.isEmpty()) {
            return;
        }
        URI uri = URI.create(webhookUrl.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Webhook 只支持 http / https: " + webhookUrl);
        }
        sender.send(uri.toString(), payload(alerts));
    }

    /** 载荷：一个 text 摘要 + 结构化 alerts，兼容常见 IM Webhook。 */
    public static String payload(List<Alert> alerts) {
        List<Map<String, String>> entries = alerts.stream()
                .map(alert -> Map.of("kind", alert.kind().name(), "subject", alert.subject(),
                        "detail", alert.detail()))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", "laplace-matrix");
        body.put("text", "拉普拉斯精灵矩阵告警：%d 项".formatted(alerts.size()));
        body.put("alerts", entries);
        return Json.write(body);
    }

    private static String minecraftOf(String cellId) {
        String[] parts = cellId.split("/");
        return parts.length >= 2 ? parts[1] : "";
    }

    private static void post(String webhookUrl, String payload) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Webhook 返回 " + response.statusCode());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("发送矩阵告警失败: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("发送矩阵告警被中断", ex);
        }
    }
}
