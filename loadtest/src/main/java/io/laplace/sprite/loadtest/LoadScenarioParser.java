package io.laplace.sprite.loadtest;

import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * 压测剧本装载（15.9）：从 JSON 文本、文件或类路径资源读取。
 *
 * <p>资源里每个后端各带一份示例剧本，测试与演示都不依赖外部文件。</p>
 */
public final class LoadScenarioParser {

    public static final String MINEFLAYER_RESOURCE = "loadtest/scenarios/mineflayer.json";
    public static final String CARPET_RESOURCE = "loadtest/scenarios/carpet.json";

    public LoadScenario parse(String json) {
        return Json.read(json, LoadScenario.class);
    }

    public LoadScenario parse(Path file) {
        return Json.readFile(file, LoadScenario.class);
    }

    public LoadScenario parseResource(String resource) {
        try (InputStream stream = LoadScenarioParser.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("找不到压测剧本资源: " + resource);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取压测剧本资源失败: " + resource, ex);
        }
    }

    /** 每个后端一份内置示例。 */
    public List<LoadScenario> bundled() {
        return List.of(parseResource(MINEFLAYER_RESOURCE), parseResource(CARPET_RESOURCE));
    }

    public LoadScenario loadBundled(BotBackend backend) {
        return switch (backend) {
            case MINEFLAYER -> parseResource(MINEFLAYER_RESOURCE);
            case CARPET -> parseResource(CARPET_RESOURCE);
        };
    }
}
