package io.laplace.sprite.servers.clienttest;

import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 声明式套件装载（15.7）：从 JSON 文件或类路径资源读套件。
 */
public final class ClientTestSuiteLoader {

    /** 打包进来的示例套件：数据包作者视角的冒烟用例。 */
    public static final String BUNDLED_SMOKE_SUITE = "clienttest/smoke-suite.json";

    public ClientTestSuite load(Path file) {
        return Json.readFile(file, ClientTestSuite.class);
    }

    public ClientTestSuite loadResource(String resource) {
        try (InputStream stream = ClientTestSuiteLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("找不到套件资源: " + resource);
            }
            return Json.read(new String(stream.readAllBytes(), StandardCharsets.UTF_8), ClientTestSuite.class);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public ClientTestSuite loadBundledSmokeSuite() {
        return loadResource(BUNDLED_SMOKE_SUITE);
    }
}
