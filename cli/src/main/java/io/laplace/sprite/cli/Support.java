package io.laplace.sprite.cli;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CLI 共享工具：平台解析、工作目录约定。
 */
final class Support {

    static final String DEFAULT_MC = "1.21.11";
    static final String DEFAULT_WORKSPACE = "build/laplace";

    /** 进程内仿真轨的取值：无平台标签。 */
    private static final String IN_PROCESS = "in-process";

    private static final Map<String, PlatformFamily> FAMILIES = families();

    private Support() {
    }

    /**
     * 解析平台。{@code in-process} 等取值表示进程内仿真轨，返回 {@code null}（无平台标签）。
     */
    static PlatformKey resolvePlatform(CommandSpec spec, String platform, String minecraftVersion,
                                       String loaderVersion) {
        if (platform == null || platform.isBlank() || isInProcess(platform)) {
            return null;
        }
        PlatformFamily family = FAMILIES.get(normalize(platform));
        if (family == null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "未知平台: " + platform + "（可选: " + String.join(", ", FAMILIES.keySet())
                            + "，或 " + IN_PROCESS + "）");
        }
        return new PlatformKey(family, minecraftVersion, blankToNull(loaderVersion));
    }

    /** 需要真实平台的场景：进程内取值直接报错，避免下游拿到 null 才炸。 */
    static PlatformKey requirePlatform(CommandSpec spec, String platform, String minecraftVersion,
                                       String loaderVersion) {
        PlatformKey key = resolvePlatform(spec, platform, minecraftVersion, loaderVersion);
        if (key == null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "该命令需要一个真实平台，" + IN_PROCESS + " 不可用");
        }
        return key;
    }

    static Map<String, PlatformFamily> families() {
        Map<String, PlatformFamily> map = new LinkedHashMap<>();
        map.put("bukkit", PlatformFamily.BUKKIT);
        map.put("spigot", PlatformFamily.SPIGOT);
        map.put("paper", PlatformFamily.PAPER);
        map.put("purpur", PlatformFamily.PURPUR);
        map.put("pufferfish", PlatformFamily.PUFFERFISH);
        map.put("folia", PlatformFamily.FOLIA);
        map.put("bungeecord", PlatformFamily.BUNGEECORD);
        map.put("waterfall", PlatformFamily.WATERFALL);
        map.put("velocity", PlatformFamily.VELOCITY);
        map.put("nukkit", PlatformFamily.NUKKIT);
        map.put("powernukkitx", PlatformFamily.POWER_NUKKIT_X);
        map.put("pnx", PlatformFamily.POWER_NUKKIT_X);
        map.put("allay", PlatformFamily.ALLAY);
        map.put("geyser", PlatformFamily.GEYSER);
        map.put("forge", PlatformFamily.FORGE);
        map.put("neoforge", PlatformFamily.NEOFORGE);
        map.put("fabric", PlatformFamily.FABRIC);
        map.put("quilt", PlatformFamily.QUILT);
        map.put("spongevanilla", PlatformFamily.SPONGE_VANILLA);
        map.put("spongeneo", PlatformFamily.SPONGE_NEO);
        map.put("spongeforge", PlatformFamily.SPONGE_FORGE);
        map.put("arclight", PlatformFamily.ARCLIGHT);
        map.put("youer", PlatformFamily.YOUER);
        map.put("magmaneo", PlatformFamily.MAGMA_NEO);
        map.put("cardboard", PlatformFamily.CARDBOARD);
        map.put("datapack", PlatformFamily.VANILLA_DATAPACK);
        map.put("modpack", PlatformFamily.MODPACK);
        return map;
    }

    static Path workspace(Path override) {
        return override != null ? override : Path.of(DEFAULT_WORKSPACE);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isInProcess(String platform) {
        String key = normalize(platform);
        return key.equals("inprocess") || key.equals("inproc") || key.equals("none")
                || key.equals("servercore") || key.equals("detectionengine");
    }

    private static String normalize(String platform) {
        return platform.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }
}
