package io.laplace.sprite.crashdiag;

/**
 * 依赖版本差（15.5 依赖分支）：日志里「要求版本 vs 实装版本」的一条。
 *
 * @param dependency       依赖名
 * @param requiredVersion  日志里声明的要求版本
 * @param installedVersion 实装版本；缺失时为 null
 */
public record VersionDiff(String dependency, String requiredVersion, String installedVersion) {

    public boolean missing() {
        return installedVersion == null;
    }

    /** 人话描述，直接进诊断建议。 */
    public String describe() {
        if (missing()) {
            return "%s：要求≥%s，当前未找到".formatted(dependency, requiredVersion);
        }
        return "%s：要求≥%s，实装 %s".formatted(dependency, requiredVersion, installedVersion);
    }
}
