package io.laplace.sprite.crashdiag;

import java.util.List;
import java.util.Optional;

/**
 * 特征签名规则（15.5）：按 {@link #order} 从小到大匹配，命中即返回。
 *
 * @param order             匹配优先级，数值小的先判
 * @param id                签名 id，写进诊断报告
 * @param category          命中后的分类
 * @param markers           文本特征串
 * @param requiresNativeFile 是否必须存在 hs_err_pid 文件才成立
 * @param rationale         为什么排在这个位置
 */
public record CrashSignature(int order, String id, CrashCategory category, List<String> markers,
                             boolean requiresNativeFile, String rationale) {

    public CrashSignature {
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public boolean matches(String text, boolean hsErrPresent) {
        if (requiresNativeFile) {
            return hsErrPresent;
        }
        if (text == null) {
            return false;
        }
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 有序签名表。顺序即判定优先级，理由写在各条 rationale 里：
     *
     * <ol>
     *   <li>{@code hs_err_pid} 文件优先：它是 JVM 原生崩溃的铁证，且原生崩溃的日志里经常同时出现
     *       OutOfMemoryError，若先判 OOM 会把原生崩溃误归到内存分支。</li>
     *   <li>OOM 次之：内存耗尽的堆栈会连带引出一堆 Mixin / 依赖类报错，先定性内存更贴近根因。</li>
     *   <li>依赖不匹配再次：加载器显式给出的「Incompatible mod set」信息量最高，能直接产出依赖 diff。</li>
     *   <li>Mixin 冲突最后：Mixin 注入失败常常是上游依赖缺失的次生现象，单独定性容易误伤。</li>
     * </ol>
     */
    public static List<CrashSignature> ordered() {
        return List.of(
                new CrashSignature(1, "hs-err-native", CrashCategory.JVM_NATIVE, List.of(), true,
                        "hs_err_pid 是原生崩溃的直接证据，优先于日志里的任何文本特征"),
                new CrashSignature(2, "out-of-memory", CrashCategory.OUT_OF_MEMORY,
                        List.of("OutOfMemoryError", "java.lang.OutOfMemoryError",
                                "java.lang.OutOfMemoryError: Java heap space"),
                        false, "OOM 会诱发下游的 Mixin / 依赖报错，先定性内存再谈次生现象"),
                new CrashSignature(3, "incompatible-mod-set", CrashCategory.DEPENDENCY_MISMATCH,
                        List.of("Incompatible mod set", "Incompatible mods found",
                                "Mod resolution encountered an incompatible mod set"),
                        false, "加载器显式给出的一致性判定，可直接解析出要求版本与实装版本的差"),
                new CrashSignature(4, "mixin-apply-failed", CrashCategory.MIXIN_CONFLICT,
                        List.of("Mixin apply failed", "Could not access"),
                        false, "Mixin 报错多为上游依赖缺失的次生现象，排在最后定性"));
    }

    /** 按顺序取第一个命中的签名。 */
    public static Optional<CrashSignature> firstMatch(String text, boolean hsErrPresent) {
        return ordered().stream().filter(signature -> signature.matches(text, hsErrPresent)).findFirst();
    }
}
