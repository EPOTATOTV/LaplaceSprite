package io.laplace.sprite.cheats.market;

import io.laplace.sprite.cheats.SampleSpec;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.SampleTier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 样本包安装前沙箱校验（升级实施文档 v2 L2-07）。
 *
 * <p>只检查声明式数据，不执行样本：id 形状、重复 id、注入器与档位结构、包体大小。
 * 校验失败时市场不落任何文件。</p>
 */
public final class SampleSandbox {

    public static final int MAX_SAMPLES = 500;
    public static final int MAX_PACKAGE_BYTES = 2 * 1024 * 1024;

    private static final Pattern SAMPLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private SampleSandbox() {
    }

    /**
     * 校验结果。
     *
     * @param ok     是否通过
     * @param issues 问题明细
     */
    public record Check(boolean ok, List<String> issues) {

        public Check {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    public static Check validate(SamplePackage pkg) {
        List<String> issues = new ArrayList<>();
        if (pkg.samples().size() > MAX_SAMPLES) {
            issues.add("样本数 %d 超过上限 %d".formatted(pkg.samples().size(), MAX_SAMPLES));
        }
        int bytes = Json.write(pkg).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_PACKAGE_BYTES) {
            issues.add("包体 %d 字节超过上限 %d".formatted(bytes, MAX_PACKAGE_BYTES));
        }
        Set<String> ids = new HashSet<>();
        for (SampleSpec sample : pkg.samples()) {
            if (sample.id() == null || !SAMPLE_ID.matcher(sample.id()).matches()) {
                issues.add("样本 id 不合规: " + sample.id());
            } else if (!ids.add(sample.id())) {
                issues.add("样本 id 重复: " + sample.id());
            }
            if (sample.injector() == null || sample.injector().isBlank()) {
                issues.add("样本 " + sample.id() + " 缺少 injector");
            }
            if (sample.isCheat() && sample.cheatType() == null) {
                issues.add("作弊样本 " + sample.id() + " 缺少 cheatType");
            }
            for (SampleTier tier : sample.tiers()) {
                if (tier.params().isEmpty()) {
                    issues.add("样本 " + sample.id() + " 的 " + tier.tier() + " 档缺少参数");
                }
                if (tier.expected() == null) {
                    issues.add("样本 " + sample.id() + " 的 " + tier.tier() + " 档缺少预期检出");
                }
            }
        }
        return new Check(issues.isEmpty(), issues);
    }
}
