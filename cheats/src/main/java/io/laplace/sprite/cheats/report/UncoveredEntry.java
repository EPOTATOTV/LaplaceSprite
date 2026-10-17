package io.laplace.sprite.cheats.report;

import io.laplace.sprite.cheats.SampleCategory;
import io.laplace.sprite.core.model.SampleTierName;

import java.util.List;

/**
 * 未覆盖项：样本声明了、但本次运行没有注入的档位。
 *
 * <p>这是矩阵覆盖缺口，不是被测目标的检出缺陷；按类别分组后进入改进 backlog。</p>
 *
 * @param category     样本类别
 * @param sampleId     样本 id
 * @param sampleType   样本类型 id
 * @param missingTiers 本次未注入的档位
 */
public record UncoveredEntry(
        SampleCategory category,
        String sampleId,
        String sampleType,
        List<SampleTierName> missingTiers) {

    public UncoveredEntry {
        missingTiers = List.copyOf(missingTiers);
    }
}
