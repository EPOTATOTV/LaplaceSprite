package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.model.PlatformFamily;

import java.util.List;

/**
 * 适配器聚合视图：一套 SPI、多平台装配（14.1）。内核只面向本接口与五个 SPI。
 *
 * <p>新增平台接入 = 注册一个适配器，无需改动内核、场景剧本与断言。</p>
 */
public interface LaplaceAdapter {

    String id();

    PlatformFamily family();

    SupportRange supportRange();

    ArtifactResolver artifactResolver();

    InstallLayout installLayout();

    LifecycleHook lifecycleHook();

    EventBridge eventBridge();

    /** 断言契约平台无关（14.4 ⑤），默认使用标准断言实现。 */
    default AssertionContract assertionContract() {
        return new PlatformAgnosticAssertionContract();
    }

    /** 冒烟剧本（14.8 六步中的第 3 步：装配成功 / 核心事件归一 / 生命周期全链路）。 */
    default List<String> smokeScenarios() {
        return List.of("assembly", "core-event-bridge", "lifecycle");
    }
}
