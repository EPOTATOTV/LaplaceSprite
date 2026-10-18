package io.laplace.sprite.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * PACC 夜间基准的趋势产出（升级实施文档 L-10）。
 *
 * <p>一次夜间运行只关心两个数：合法样本误报率与作弊矩阵检出率。趋势图用 SVG 直接生成，
 * 不引图表依赖；左轴是检出率（0.90–1.00），右轴是误报率（0–最大值），两条线各自归一化。</p>
 */
public final class DetectionTrend {

    /**
     * 一次夜间基准结果。
     *
     * @param version       被测版本
     * @param legitSamples  合法样本数
     * @param falsePositives 误报数
     * @param cheatSamples  作弊样本数
     * @param detected      检出数
     */
    public record BaselineRun(String version, int legitSamples, int falsePositives,
                              int cheatSamples, int detected) {

        public BaselineRun {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("版本号不能为空");
            }
            if (legitSamples < 0 || falsePositives < 0 || cheatSamples < 0 || detected < 0) {
                throw new IllegalArgumentException("样本计数不能为负: " + version);
            }
            if (falsePositives > legitSamples) {
                throw new IllegalArgumentException("误报数不能超过合法样本数: " + version);
            }
            if (detected > cheatSamples) {
                throw new IllegalArgumentException("检出数不能超过作弊样本数: " + version);
            }
        }

        public double falsePositiveRate() {
            return legitSamples == 0 ? 0 : (double) falsePositives / legitSamples;
        }

        public double detectionRate() {
            return cheatSamples == 0 ? 0 : (double) detected / cheatSamples;
        }
    }

    /**
     * 趋势上的一个点。
     *
     * @param version          版本
     * @param falsePositiveRate 误报率
     * @param detectionRate    检出率
     */
    public record Point(String version, double falsePositiveRate, double detectionRate) {
    }

    private final List<Point> points;

    private DetectionTrend(List<Point> points) {
        this.points = List.copyOf(points);
    }

    public static DetectionTrend from(List<BaselineRun> runs) {
        Objects.requireNonNull(runs, "runs");
        List<Point> points = new ArrayList<>(runs.size());
        for (BaselineRun run : runs) {
            points.add(new Point(run.version(), run.falsePositiveRate(), run.detectionRate()));
        }
        return new DetectionTrend(points);
    }

    public List<Point> points() {
        return points;
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("| 版本 | 误报率 | 检出率 |\n");
        md.append("| --- | ---: | ---: |\n");
        for (Point point : points) {
            md.append("| ").append(point.version())
                    .append(" | ").append(String.format(Locale.ROOT, "%.4f%%", point.falsePositiveRate() * 100))
                    .append(" | ").append(String.format(Locale.ROOT, "%.2f%%", point.detectionRate() * 100))
                    .append(" |\n");
        }
        return md.toString();
    }

    /** 双轴折线图；点太少时仍画坐标轴与标签，不伪造趋势。 */
    public String toSvg(int width, int height) {
        int w = Math.max(240, width);
        int h = Math.max(140, height);
        int left = 52;
        int right = 50;
        int top = 28;
        int bottom = 34;
        int plotW = w - left - right;
        int plotH = h - top - bottom;

        double maxFpr = points.stream().mapToDouble(Point::falsePositiveRate).max().orElse(0.0);
        if (maxFpr <= 0) {
            maxFpr = 1.0;
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(w)
                .append("\" height=\"").append(h).append("\" viewBox=\"0 0 ").append(w).append(' ')
                .append(h).append("\" role=\"img\" aria-label=\"PACC 夜间基准误报与检出趋势\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        svg.append("<text x=\"").append(left).append("\" y=\"18\" font-family=\"monospace\" font-size=\"11\"")
                .append(" fill=\"#4d5a70\">检出率（左） / 误报率（右）</text>\n");

        for (int i = 0; i <= 4; i++) {
            double ratio = i / 4.0;
            int y = top + (int) Math.round(plotH * ratio);
            svg.append("<line x1=\"").append(left).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(left + plotW).append("\" y2=\"").append(y)
                    .append("\" stroke=\"#e7eaf1\" stroke-width=\"1\"/>\n");
            double detection = 1.0 - ratio * 0.10;
            svg.append("<text x=\"").append(left - 8).append("\" y=\"").append(y + 4)
                    .append("\" text-anchor=\"end\" font-family=\"monospace\" font-size=\"9\" fill=\"#6d798f\">")
                    .append(String.format(Locale.ROOT, "%.2f", detection)).append("</text>\n");
            double fpr = maxFpr * ratio;
            svg.append("<text x=\"").append(left + plotW + 8).append("\" y=\"").append(y + 4)
                    .append("\" font-family=\"monospace\" font-size=\"9\" fill=\"#6d798f\">")
                    .append(String.format(Locale.ROOT, "%.4f", fpr * 100)).append("%</text>\n");
        }

        if (points.isEmpty()) {
            svg.append("<text x=\"").append(w / 2).append("\" y=\"").append(h / 2)
                    .append("\" text-anchor=\"middle\" font-family=\"sans-serif\" font-size=\"12\"")
                    .append(" fill=\"#6d798f\">没有基准数据</text>\n");
        } else {
            svg.append(polyline(points, true, left, top, plotW, plotH)).append('\n');
            svg.append(polyline(points, false, left, top, plotW, plotH, maxFpr)).append('\n');
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                int x = xAt(i, points.size(), left, plotW);
                int yDetection = detectionY(point.detectionRate(), top, plotH);
                int yFpr = fprY(point.falsePositiveRate(), maxFpr, top, plotH);
                svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(yDetection)
                        .append("\" r=\"2.6\" fill=\"#2b4d86\">")
                        .append("<title>").append(escape(point.version())).append(" 检出率 ")
                        .append(String.format(Locale.ROOT, "%.2f%%", point.detectionRate() * 100))
                        .append("</title></circle>\n");
                svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(yFpr)
                        .append("\" r=\"2.2\" fill=\"#c6433a\"/>\n");
                svg.append("<text x=\"").append(x).append("\" y=\"").append(top + plotH + 18)
                        .append("\" text-anchor=\"middle\" font-family=\"monospace\" font-size=\"9\"")
                        .append(" fill=\"#6d798f\">").append(escape(point.version())).append("</text>\n");
            }
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    public void writeSvg(Path file, int width, int height) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, toSvg(width, height), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入趋势图失败: " + file, ex);
        }
    }

    private static String polyline(List<Point> points, boolean detection, int left, int top,
                                   int plotW, int plotH, double... maxFpr) {
        StringBuilder out = new StringBuilder("<polyline fill=\"none\" stroke=\"")
                .append(detection ? "#2b4d86" : "#c6433a")
                .append("\" stroke-width=\"1.8\" points=\"");
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            int x = xAt(i, points.size(), left, plotW);
            int y = detection ? detectionY(point.detectionRate(), top, plotH)
                    : fprY(point.falsePositiveRate(), maxFpr.length == 0 ? 1.0 : maxFpr[0], top, plotH);
            out.append(x).append(',').append(y).append(' ');
        }
        return out.append("\"/>").toString();
    }

    private static int xAt(int index, int count, int left, int plotW) {
        return count <= 1 ? left + plotW / 2 : left + (int) Math.round(plotW * (double) index / (count - 1));
    }

    private static int detectionY(double value, int top, int plotH) {
        double clamped = Math.min(1.0, Math.max(0.90, value));
        return top + (int) Math.round(plotH * (1.0 - (clamped - 0.90) / 0.10));
    }

    private static int fprY(double value, double maxFpr, int top, int plotH) {
        double clamped = maxFpr <= 0 ? 0 : Math.min(1.0, Math.max(0, value / maxFpr));
        return top + (int) Math.round(plotH * (1.0 - clamped));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
