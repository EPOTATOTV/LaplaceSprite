package io.laplace.sprite.cli;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.profile.ProfileCollector;
import io.laplace.sprite.profile.ProfileReport;
import io.laplace.sprite.profile.ProfileView;
import io.laplace.sprite.profile.ProfileViewpoint;
import io.laplace.sprite.profile.TpsRating;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code laplace profile}：15.3 性能剖析轨。
 *
 * <p>有真实 spark 通道时取其回执；离线时降级到 MetricsAgent 合成采样，并明确说明 spark 链接缺失
 * ——不编造分享链接。</p>
 */
@Command(name = "profile",
        description = "性能剖析轨：Spark 指标采集、TPS 评级、性能回归嫌疑判定、JFR 落盘",
        mixinStandardHelpOptions = true)
final class ProfileCommand implements Callable<Integer> {

    @Option(names = "--instance", defaultValue = "paper-1", description = "实例 id")
    String instanceId;

    @Option(names = "--viewpoint", defaultValue = "DETECTION_DEVELOPER",
            description = "视角：DETECTION_DEVELOPER | MOD_AUTHOR")
    ProfileViewpoint viewpoint;

    @Option(names = "--out", description = "产物目录，默认 <workspace>/profile")
    Path out;

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        Path dir = out != null ? out : Support.workspace(workspace).resolve("profile");
        var writer = spec.commandLine().getOut();

        ProfileCollector collector = ProfileCollector.offline(instanceId);
        ProfileReport report = collector.collect(instanceId);
        ProfileView view = report.forViewpoint(viewpoint);

        writer.println("性能剖析轨（15.3） · 实例 " + instanceId + " · 视角 " + viewpoint);
        writer.println("  spark 可用=" + collector.spark().available()
                + "（不可用时降级到 MetricsAgent，spark 链接留空）");
        writer.println("  TPS 窗口 " + windows(report));
        writer.printf("  MSPT avg %.2f / min %.2f / max %.2f ms%n", report.msptAvg(), report.msptMin(),
                report.msptMax());
        writer.printf("  堆 %d/%d MB · GC p99 %s ms / max %s ms · CPU %s%% · 磁盘 %s%%%n",
                report.heapUsedMb(), report.heapMaxMb(), show(report.gcPauseP99Ms()),
                show(report.gcPauseMaxMs()), show(report.cpuPercent()), show(report.diskPercent()));
        writer.println("  TPS 评级 " + report.tpsRating() + "（" + report.tpsRating().label() + "）");
        writer.println("  性能回归嫌疑 " + report.regressionSuspect() + "（阈值 TPS <19.5 或 MSPT max >60ms）");
        writer.println("  尖刺 tick " + (report.spikeTicks().isEmpty() ? "无" : report.spikeTicks()));
        writer.println("  视角主线程 " + view.primaryThread() + " · 指标关注 " + view.threadFocus());
        writer.println("  spark 链接 " + (report.hasSparkLink() ? report.sparkLink() : "（无，已降级）"));
        writer.println("  JFR 文件 " + (report.jfrFile() == null ? "（未录制）" : report.jfrFile()));
        report.notes().forEach(note -> writer.println("    · " + note));

        write(dir.resolve("profile-" + instanceId + ".json"), Json.write(report));
        writer.println("产物目录 " + dir.toAbsolutePath());
        return 0;
    }

    private static String windows(ProfileReport report) {
        return "5s=" + show(report.tps5s()) + " 10s=" + show(report.tps10s()) + " 1m=" + show(report.tps1m())
                + " 5m=" + show(report.tps5m()) + " 15m=" + show(report.tps15m());
    }

    private static String show(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static void write(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
