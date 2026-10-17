package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.SampleTier;

import java.util.List;

/**
 * 样本 YAML 的文档结构。
 *
 * <p>{@code category} / {@code sampleType} 是 cheats 侧的扩展字段，core 的 TestSpec 不感知；
 * 作弊样本继续用 {@code type: SPEED} 声明子类型，避免与既有样本文件分裂成两套写法。</p>
 */
record SampleDocument(
        String id,
        String name,
        SampleCategory category,
        String sampleType,
        String type,
        String target,
        List<SampleTier> tiers,
        String baseline,
        String injector,
        String source,
        String notes) {
}
