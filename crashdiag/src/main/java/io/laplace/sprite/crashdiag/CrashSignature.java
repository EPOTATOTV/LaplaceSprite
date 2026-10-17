package io.laplace.sprite.crashdiag;

import java.util.List;
import java.util.Optional;

/**
 * 特征签名规则（15.5）：按 {@link #order} 从小到大匹配，命中即返回。
 *
 * @param order             匹配优先级，数值小的先判
 * @param id                签名 id，写进诊断报告
 * @param category          命中后的分类
 * @param markers           文本特征串，命中任意一个即可
 * @param contextMarkers    上下文特征串，非空时要求同时命中其中之一；用于避免把无关异常算进来
 * @param requiresNativeFile 是否必须存在 hs_err_pid 文件才成立
 * @param rationale         为什么排在这个位置
 */
public record CrashSignature(int order, String id, CrashCategory category, List<String> markers,
                             List<String> contextMarkers, boolean requiresNativeFile, String rationale) {

    public CrashSignature {
        markers = markers == null ? List.of() : List.copyOf(markers);
        contextMarkers = contextMarkers == null ? List.of() : List.copyOf(contextMarkers);
    }

    /** 兼容不带上下文的签名。 */
    public CrashSignature(int order, String id, CrashCategory category, List<String> markers,
                          boolean requiresNativeFile, String rationale) {
        this(order, id, category, markers, List.of(), requiresNativeFile, rationale);
    }

    public boolean matches(String text, boolean hsErrPresent) {
        if (requiresNativeFile) {
            return hsErrPresent;
        }
        if (text == null) {
            return false;
        }
        boolean markerHit = markers.stream().anyMatch(text::contains);
        if (!markerHit) {
            return false;
        }
        return contextMarkers.isEmpty() || contextMarkers.stream().anyMatch(text::contains);
    }

    /**
     * 有序签名表。顺序即判定优先级，理由写在各条 rationale 里：
     *
     * <ol>
     *   <li>{@code hs_err_pid} 文件优先：它是 JVM 原生崩溃的铁证，且原生崩溃的日志里经常同时出现
     *       OutOfMemoryError，若先判 OOM 会把原生崩溃误归到内存分支。</li>
     *   <li>OOM 次之：内存耗尽的堆栈会连带引出一堆 Mixin / 依赖类报错，先定性内存更贴近根因。</li>
     *   <li>数据包 / 资源包三条排在 OOM 之后、依赖与 Mixin 之前：包 JSON 报错本身就足够具体，
     *       且依赖缺失、Mixin 注入失败常是包加载失败后的次生现象。</li>
     *   <li>依赖不匹配再次：加载器显式给出的「Incompatible mod set」信息量最高，能直接产出依赖 diff。</li>
     *   <li>Mixin 冲突最后：Mixin 注入失败常常是上游依赖缺失的次生现象，单独定性容易误伤。</li>
     * </ol>
     *
     * <p>包类三条之间：世界加载失败是终态（MC-310240）排最前；{@code /reload} 时的 JSON 解析异常
     * （MC-123460）次之；找不到 pack.mcmeta（MC-123459）最后。三条都不影响第 1 条：现场有
     * hs_err_pid 时仍判原生崩溃。</p>
     */
    public static List<CrashSignature> ordered() {
        return List.of(
                new CrashSignature(1, "hs-err-native", CrashCategory.JVM_NATIVE, List.of(), true,
                        "hs_err_pid 是原生崩溃的直接证据，优先于日志里的任何文本特征"),
                new CrashSignature(2, "out-of-memory", CrashCategory.OUT_OF_MEMORY,
                        List.of("OutOfMemoryError", "java.lang.OutOfMemoryError",
                                "java.lang.OutOfMemoryError: Java heap space"),
                        false, "OOM 会诱发下游的 Mixin / 依赖报错，先定性内存再谈次生现象"),
                new CrashSignature(3, "pack-world-load-failure", CrashCategory.RESOURCE_PACK_LOAD_FAILURE,
                        List.of("Failed to load datapacks", "Failed to load data packs",
                                "Failed to load packs", "Failed to load world"),
                        List.of("JsonSyntaxException", "MalformedJsonException"),
                        false, "世界加载失败的终态信息量最高；要求同时出现 JSON 解析异常才算，"
                                + "避免把缺函数引用之类的普通加载失败算成 JSON 问题"),
                new CrashSignature(4, "pack-meta-json-syntax", CrashCategory.RESOURCE_PACK_LOAD_FAILURE,
                        List.of("JsonSyntaxException", "MalformedJsonException"),
                        List.of("pack.mcmeta", "datapack", "Datapack", "data pack", "Data pack", "数据包"),
                        false, "reload 时的 pack.mcmeta 解析异常（MC-123460）；要求带包上下文，"
                                + "否则模组配置文件解析失败也会被误判成数据包问题"),
                new CrashSignature(5, "pack-meta-missing", CrashCategory.RESOURCE_PACK_LOAD_FAILURE,
                        List.of("Couldn't find pack meta", "Could not find pack meta",
                                "Couldn't find pack metadata"),
                        false, "找不到 pack.mcmeta（MC-123459）；措辞本身已指明包，无需额外上下文"),
                new CrashSignature(6, "incompatible-mod-set", CrashCategory.DEPENDENCY_MISMATCH,
                        List.of("Incompatible mod set", "Incompatible mods found",
                                "Mod resolution encountered an incompatible mod set"),
                        false, "加载器显式给出的一致性判定，可直接解析出要求版本与实装版本的差"),
                new CrashSignature(7, "mixin-apply-failed", CrashCategory.MIXIN_CONFLICT,
                        List.of("Mixin apply failed", "Could not access"),
                        false, "Mixin 报错多为上游依赖缺失的次生现象，排在最后定性"));
    }

    /** 按顺序取第一个命中的签名。 */
    public static Optional<CrashSignature> firstMatch(String text, boolean hsErrPresent) {
        return ordered().stream().filter(signature -> signature.matches(text, hsErrPresent)).findFirst();
    }
}
