package io.laplace.sprite.adapters.bukkit;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Paper（14.2）：一级接入目标，plugin.yml 与 paper-plugin.yml 两套描述符都收。 */
public final class PaperAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Paper",
            "构建 plugins/ 目录；paper-plugin.yml 的 dependencies 分 bootstrap/server 两段",
            "paper-plugin.yml（fallback: plugin.yml）",
            "Bukkit Scheduler + Paper 异步任务；folia-supported 为真时按区域调度",
            AdapterStatus.IMPLEMENTED_TIER1,
            List.of("paper-plugin.yml 的 folia-supported 决定是否走 Folia 并发变体",
                    "dependencies.<section>.<plugin>.required 映射为硬/软依赖",
                    "插件加载顺序影响事件桥注册时机，冷启动序列必须按 onLoad → onEnable 验证"));

    public PaperAdapter() {
        super("paper", PlatformFamily.PAPER, SupportRange.minecraft("1.8", null), FACTS);
    }
}
