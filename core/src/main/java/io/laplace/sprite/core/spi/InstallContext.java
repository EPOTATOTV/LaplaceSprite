package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunTarget;
import io.laplace.sprite.core.model.ServerPluginConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 安装/运行上下文（14.4 ③ LifecycleHook 入参）。
 *
 * @param platform       目标平台
 * @param target         检测目标
 * @param serverDirectory 服务端工作目录
 * @param artifacts      已解析并装配的产物
 * @param pluginConfig   受测插件配置
 * @param attributes     附加属性
 * @param eulaAccepted   EULA 是否已接受
 * @param headless       是否无头运行
 */
public record InstallContext(PlatformKey platform, RunTarget target, Path serverDirectory,
                             List<Artifact> artifacts, ServerPluginConfig pluginConfig,
                             Map<String, Object> attributes, boolean eulaAccepted, boolean headless) {

    public InstallContext {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
