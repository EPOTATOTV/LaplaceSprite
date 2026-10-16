package io.laplace.sprite.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * spark 客户端（15.3）：通过命令通道下发 spark 命令并解析文本回执。
 *
 * <p>通道不可用时进入降级态：{@link #available()} 为 false，各查询返回空结果，
 * 不返回任何编造的数据，尤其是不会捏造分享链接。</p>
 *
 * <p>解析按 spark 输出里的标签行取值。标签措辞随 spark 版本变化，取不到就留 null，
 * 不去猜。</p>
 */
public final class SparkClient {

    /** 分享链接形如 https://spark.lucko.me/AbCdEf1234。 */
    private static final Pattern SHARE_LINK = Pattern.compile("https?://spark\\.lucko\\.me/[A-Za-z0-9]+");
    private static final Pattern HEAP = Pattern.compile("Heap:\\s*([\\d,]+)\\s*MB\\s*/\\s*([\\d,]+)\\s*MB");
    private static final Pattern GC_P99 = Pattern.compile("GC pause p99:\\s*([\\d.]+)\\s*ms");
    private static final Pattern GC_MAX = Pattern.compile("GC pause max:\\s*([\\d.]+)\\s*ms");
    private static final Pattern CPU = Pattern.compile("CPU:\\s*([\\d.]+)\\s*%");
    private static final Pattern DISK = Pattern.compile("Disk:\\s*([\\d.]+)\\s*%");

    private final CommandChannel channel;
    private volatile boolean available = true;

    public SparkClient(CommandChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    /** 通道是否可用：以最近一次下发结果为准，首次下发前按可用处理。 */
    public boolean available() {
        return available;
    }

    /** 下发 /spark tps 并解析多窗口指标。 */
    public SparkTps tps() {
        String output = send(SparkCommandFactory.tps());
        return output == null ? SparkTps.empty() : parseTps(output);
    }

    /** 下发 /spark health 并解析 JVM 资源。 */
    public SparkHealth health() {
        String output = send(SparkCommandFactory.health());
        return output == null ? SparkHealth.empty() : parseHealth(output);
    }

    /**
     * 发起一次采样剖面（/spark profiler start --timeout 60）并取分享链接。
     *
     * <p>命令通道单次回执可能只含「已开始」，此时解析不到链接，返回空——由调用方在
     * timeout 结束后再取一次回执。</p>
     */
    public Optional<String> profilerLink() {
        String output = send(SparkCommandFactory.profilerStart());
        return output == null ? Optional.empty() : parseProfilerLink(output);
    }

    /** 抓 tick 尖刺（--only-ticks-over）。 */
    public Optional<String> spikeProfilerLink() {
        String output = send(SparkCommandFactory.spikeProfiler());
        return output == null ? Optional.empty() : parseProfilerLink(output);
    }

    private String send(SparkCommand command) {
        String commandLine = command.commandLine();
        try {
            String output = channel.send(commandLine);
            available = true;
            return output;
        } catch (RuntimeException ex) {
            // 通道抛错即视作不可用，降级但不伪造回执
            available = false;
            return null;
        }
    }

    /**
     * 解析 TPS 与 tick 耗时行。
     *
     * <p>支持 5 值窗口（5s,10s,1m,5m,15m）与 3 值窗口（1m,5m,15m）两种排布。</p>
     */
    public static SparkTps parseTps(String output) {
        if (output == null || output.isBlank()) {
            return SparkTps.empty();
        }
        Double[] tpsWindows = new Double[]{null, null, null, null, null};
        String tpsLine = labelledLine(output, "TPS from last");
        if (tpsLine != null) {
            List<Double> values = parseDoubles(afterLastColon(tpsLine), ",");
            if (values.size() == 5) {
                for (int i = 0; i < 5; i++) {
                    tpsWindows[i] = values.get(i);
                }
            } else if (values.size() == 3) {
                tpsWindows[2] = values.get(0);
                tpsWindows[3] = values.get(1);
                tpsWindows[4] = values.get(2);
            }
        }

        Double msptAvg = null;
        Double msptMin = null;
        Double msptMax = null;
        String tickLine = labelledLine(output, "Tick durations");
        if (tickLine != null) {
            List<String> groups = List.of(afterLastColon(tickLine).split(";"));
            double sumMed = 0.0;
            int medCount = 0;
            for (String group : groups) {
                List<Double> quad = parseDoubles(group, "/");
                if (quad.size() < 4) {
                    continue;
                }
                Double min = quad.get(0);
                Double med = quad.get(1);
                Double max = quad.get(3);
                msptMin = msptMin == null ? min : Math.min(msptMin, min);
                msptMax = msptMax == null ? max : Math.max(msptMax, max);
                sumMed += med;
                medCount++;
            }
            if (medCount > 0) {
                msptAvg = sumMed / medCount;
            }
        }
        return new SparkTps(tpsWindows[0], tpsWindows[1], tpsWindows[2], tpsWindows[3], tpsWindows[4],
                msptAvg, msptMin, msptMax);
    }

    /** 解析 Heap / GC / CPU / Disk 标签行。 */
    public static SparkHealth parseHealth(String output) {
        if (output == null || output.isBlank()) {
            return SparkHealth.empty();
        }
        Long heapUsed = null;
        Long heapMax = null;
        Matcher heap = HEAP.matcher(output);
        if (heap.find()) {
            heapUsed = Long.parseLong(heap.group(1).replace(",", ""));
            heapMax = Long.parseLong(heap.group(2).replace(",", ""));
        }
        return new SparkHealth(heapUsed, heapMax,
                firstDouble(GC_P99, output), firstDouble(GC_MAX, output),
                firstDouble(CPU, output), firstDouble(DISK, output));
    }

    /** 从回执中提取 spark 分享链接。 */
    public static Optional<String> parseProfilerLink(String output) {
        if (output == null || output.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = SHARE_LINK.matcher(output);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private static String labelledLine(String output, String label) {
        for (String line : output.split("\\R")) {
            if (line.contains(label)) {
                return line;
            }
        }
        return null;
    }

    private static String afterLastColon(String line) {
        int index = line.lastIndexOf(':');
        return index < 0 ? line : line.substring(index + 1);
    }

    private static List<Double> parseDoubles(String text, String separator) {
        List<Double> values = new ArrayList<>();
        for (String token : text.split(Pattern.quote(separator))) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                values.add(Double.parseDouble(trimmed));
            } catch (NumberFormatException ex) {
                // 取不到就跳过该值，不用 0 占位
            }
        }
        return values;
    }

    private static Double firstDouble(Pattern pattern, String output) {
        Matcher matcher = pattern.matcher(output);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }
}
