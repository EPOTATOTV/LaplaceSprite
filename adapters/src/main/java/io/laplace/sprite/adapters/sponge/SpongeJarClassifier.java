package io.laplace.sprite.adapters.sponge;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * mods/ 目录内 JAR 的归属识别（14.3 SpongeForge 识别顺序）。
 *
 * <p>sponge_plugins.json 排在 Forge / NeoForge 元数据之前：SpongeForge 下同一个 JAR 既是 Sponge 插件
 * 又是 Forge 模组，先命中的是 Sponge，只有缺失 Sponge 描述符时才回退到加载器元数据。</p>
 */
public final class SpongeJarClassifier {

    /** Sponge 系描述符。 */
    public static final String SPONGE_DESCRIPTOR = "META-INF/sponge_plugins.json";
    public static final String NEOFORGE_DESCRIPTOR = "META-INF/neoforge.mods.toml";
    public static final String FORGE_DESCRIPTOR = "META-INF/mods.toml";

    /** JAR 归属。 */
    public enum JarKind {
        SPONGE,
        FORGE_MOD,
        NEOFORGE_MOD,
        UNKNOWN
    }

    /** 识别顺序，先到先得；报告与测试据此核对优先级。 */
    public static List<String> precedence() {
        return List.of(SPONGE_DESCRIPTOR, NEOFORGE_DESCRIPTOR, FORGE_DESCRIPTOR);
    }

    /** 按 {@link #precedence()} 判定 JAR 归属；描述符名大小写不敏感。 */
    public JarKind classify(Collection<String> descriptorFiles) {
        if (present(descriptorFiles, SPONGE_DESCRIPTOR)) {
            return JarKind.SPONGE;
        }
        if (present(descriptorFiles, NEOFORGE_DESCRIPTOR)) {
            return JarKind.NEOFORGE_MOD;
        }
        if (present(descriptorFiles, FORGE_DESCRIPTOR)) {
            return JarKind.FORGE_MOD;
        }
        return JarKind.UNKNOWN;
    }

    private static boolean present(Collection<String> descriptorFiles, String expected) {
        if (descriptorFiles == null) {
            return false;
        }
        String target = expected.toLowerCase(Locale.ROOT);
        return descriptorFiles.stream()
                .filter(name -> name != null)
                .anyMatch(name -> name.toLowerCase(Locale.ROOT).equals(target));
    }
}
