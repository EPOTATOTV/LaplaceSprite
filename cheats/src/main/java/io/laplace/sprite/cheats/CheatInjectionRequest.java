package io.laplace.sprite.cheats;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.core.model.TestSpec;

/**
 * 一次样本注入所需的全部输入（第五章 AnomalyMatrix）。
 *
 * @param sample   样本条目（含类别与样本类型）
 * @param tier     生效档位
 * @param playerId 模拟玩家 id
 * @param platform 平台标签
 * @param baseline 合法基线事件流；调用方无基线时可传 null，由注入器自行降级
 * @param scenario 场景剧本
 * @param network  网络条件模型
 * @param seed     随机种子，保证注入结果可复现
 */
public record CheatInjectionRequest(
        SampleSpec sample,
        SampleTierName tier,
        String playerId,
        PlatformKey platform,
        EventStream baseline,
        Scenario scenario,
        NetworkModel network,
        long seed) {

    /** 兼容构造：只给 core 的 TestSpec 时按作弊样本处理。 */
    public CheatInjectionRequest(TestSpec spec, SampleTierName tier, String playerId, PlatformKey platform,
                                 EventStream baseline, Scenario scenario, NetworkModel network, long seed) {
        this(SampleSpec.of(spec), tier, playerId, platform, baseline, scenario, network, seed);
    }

    /** 作弊注入器仍按 TestSpec 读取参数与出处。 */
    public TestSpec spec() {
        return sample.spec();
    }
}
