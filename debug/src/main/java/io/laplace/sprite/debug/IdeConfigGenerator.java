package io.laplace.sprite.debug;

import io.laplace.sprite.adapters.InstallLayouts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * IDE 配置生成器（15.1）：按被测产物类型产出 IntelliJ Remote 调试配置（{@code .run/*.xml}）。
 *
 * <p>模块名取产物名，工作目录取适配器装配目录所在的服务端根（mods/、plugins/ 的上一级），
 * 这样模组端、插件端、代理端各得到一份对得上装配位置的配置。</p>
 */
public final class IdeConfigGenerator {

    public static final String RUN_DIR = ".run";

    private static final String LOCAL_HOST = "localhost";

    private static final String MODS_DIR_NAME = "mods";

    private static final String PLUGINS_DIR_NAME = "plugins";

    public String remoteRunConfig(String configName, String moduleName, JdwpOptions options,
                                  String workingDirectory) {
        requireText(configName, "配置名");
        requireText(moduleName, "模块名");
        requireText(workingDirectory, "工作目录");
        String host = options.hostAddress().isEmpty() ? LOCAL_HOST : options.hostAddress();
        return """
                <component name="ProjectRunConfigurationManager">
                  <configuration default="false" name="%s" type="Remote" factoryName="Remote">
                    <module name="%s" />
                    <option name="USE_SOCKET_TRANSPORT" value="true" />
                    <option name="SERVER_MODE" value="false" />
                    <option name="HOST" value="%s" />
                    <option name="PORT" value="%d" />
                    <option name="AUTO_RESTART" value="false" />
                    <option name="WORKING_DIRECTORY" value="%s" />
                    <method v="2" />
                  </configuration>
                </component>
                """.formatted(escape(configName), escape(moduleName), escape(host), options.port(),
                escape(workingDirectory));
    }

    public Path write(Path projectRoot, String configName, String moduleName, JdwpOptions options,
                      String workingDirectory) {
        Path runDir = projectRoot.resolve(RUN_DIR);
        Path target = runDir.resolve(sanitizeFileName(configName) + ".xml");
        try {
            Files.createDirectories(runDir);
            Files.writeString(target, remoteRunConfig(configName, moduleName, options, workingDirectory),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入 IDE 运行配置失败: " + target, ex);
        }
        return target;
    }

    /** 一步到位：平台决定装配目录与配置名，产物名同时作为模块名。 */
    public String remoteRunConfigFor(PlatformKey platform, String artifactName, JdwpOptions options) {
        Path layoutDir = InstallLayouts.forFamily(platform.family()).layoutDirectory(platform);
        return remoteRunConfig(configNameFor(platform, artifactName), artifactName, options,
                serverRoot(layoutDir));
    }

    public String configNameFor(PlatformKey platform, String artifactName) {
        PlatformFamily family = platform.family();
        if (family.isModLoader()) {
            return artifactName + "（模组调试）";
        }
        if (family.isProxy()) {
            return artifactName + "（代理调试）";
        }
        if (family.isPluginPlatform() || family == PlatformFamily.GEYSER) {
            return artifactName + "（插件调试）";
        }
        return artifactName + "（远程调试）";
    }

    /** mods/ plugins/ 的上一级是服务端根；其他布局本身就是根。 */
    private static String serverRoot(Path layoutDir) {
        Path root = layoutDir;
        Path name = layoutDir.getFileName();
        if (name != null
                && (MODS_DIR_NAME.equals(name.toString()) || PLUGINS_DIR_NAME.equals(name.toString()))) {
            Path parent = layoutDir.getParent();
            if (parent != null) {
                root = parent;
            }
        }
        return "$PROJECT_DIR$/" + root.toString().replace('\\', '/');
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
