package io.laplace.sprite.adapters.bedrock;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Geyser（14.2）：本身是挂载在宿主服务端上的插件，用来让基岩客户端接入 Java 服。 */
public final class GeyserAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Geyser",
            "作为插件装进宿主服务端的 plugins/ 目录，随宿主生命周期起停",
            "plugin.yml",
            "宿主服务端调度器（Geyser 不自建线程模型）",
            AdapterStatus.HOST_MOUNTED,
            List.of("宿主生命周期决定 Geyser 的可测窗口，测试以宿主实例为准",
                    "基岩客户端接入产生 ADDON 事件，Java 侧事件照常归一",
                    "宿主矩阵不匹配时按兼容链回退宿主适配器（Geyser → Paper）"));

    public GeyserAdapter() {
        super("geyser", PlatformFamily.GEYSER, SupportRange.any(), FACTS);
    }
}
