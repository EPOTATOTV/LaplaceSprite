package io.laplace.sprite.adapters.bedrock;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** PowerNukkitX（14.2）：Nukkit 的活跃分支，运行时要求 JDK 21。 */
public final class PowerNukkitXAdapter extends AbstractLaplaceAdapter {

    /** 运行前提：JDK 21（服务端与测试进程同一版本）。 */
    public static final int REQUIRED_JDK = 21;

    private static final PlatformFacts FACTS = new PlatformFacts(
            "PowerNukkitX",
            "构建 plugins/ 目录，运行前提是 JDK 21 的服务端",
            "plugin.yml（api 为版本列表）",
            "PNX 主线程 tick + 异步区块任务",
            AdapterStatus.IMPLEMENTED,
            List.of("服务端与测试进程都必须跑在 JDK 21 上，版本不符直接跳过该矩阵项",
                    "忽略区间时按兼容链回退 Nukkit 并输出降级告警",
                    "PNX 的方块更新事件比 Nukkit 更细，BlockChange 去重口径需按平台标注"));

    public PowerNukkitXAdapter() {
        super("powernukkitx", PlatformFamily.POWER_NUKKIT_X, SupportRange.minecraft("1.21.0", null), FACTS);
    }

    public int requiredJdk() {
        return REQUIRED_JDK;
    }
}
