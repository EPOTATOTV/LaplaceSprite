package io.laplace.sprite.cheats.market;

import io.laplace.sprite.cheats.SampleSpec;

import java.util.List;
import java.util.Objects;

/**
 * 社区样本分发包（升级实施文档 L-08）。
 *
 * <p>一个包携带一组 {@link SampleSpec} 与发布者信息，签名覆盖 id / 版本 / 发布者与样本内容。
 * 包本身不执行任何代码，安装只是把声明式样本落盘并更新当前版本指针。</p>
 *
 * @param id        包 id
 * @param version   版本号（点分数字）
 * @param publisher 发布者
 * @param samples   样本列表
 * @param signature HMAC-SHA256 十六进制签名
 */
public record SamplePackage(String id, String version, String publisher, List<SampleSpec> samples,
                            String signature) {

    public SamplePackage {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("样本包 id 不能为空");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("样本包版本不能为空");
        }
        publisher = publisher == null ? "" : publisher;
        samples = samples == null ? List.of() : List.copyOf(samples);
        signature = signature == null ? "" : signature;
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("样本包至少要有一个样本: " + id);
        }
    }

    /** 去掉签名后的副本，用于计算规范串。 */
    public SamplePackage unsigned() {
        return new SamplePackage(id, version, publisher, samples, "");
    }

    public boolean signed() {
        return !signature.isBlank();
    }

    /** 包里样本的稳定 id 列表（排序后）。 */
    public List<String> sampleIds() {
        return samples.stream().map(SampleSpec::id).sorted().toList();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SamplePackage that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
