package io.laplace.sprite.adapters.bedrock;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Allay（14.2）：状态「适配中」，描述符是 plugin.json，版本允许三方区间写法。 */
public final class AllayAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Allay",
            "构建 plugins/ 目录，plugin.json 声明入口类与依赖",
            "plugin.json",
            "Allay 主线程 tick",
            AdapterStatus.IN_PROGRESS,
            List.of("适配中：默认只做装配与事件归一冒烟，不开放行为矩阵判定",
                    "版本字段允许 NPM / CocoaPods / IVY 风格区间，平台侧不做求解，原样归档",
                    "optional 依赖只影响排序，缺失不失败"));

    public AllayAdapter() {
        super("allay", PlatformFamily.ALLAY, SupportRange.any(), FACTS);
    }
}
