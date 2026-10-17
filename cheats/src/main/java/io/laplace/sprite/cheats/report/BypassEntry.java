package io.laplace.sprite.cheats.report;

import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;

import java.util.List;

/**
 * 一条绕过记录：某作弊类型在某个本应被检出的档位上 0 命中。
 *
 * @param cheatType      作弊类型
 * @param sources        该类型在样本库中出现的客户端出处标签
 * @param tier           档位
 * @param samples        该档位的声明样本数
 * @param hits           该档位的命中数（按定义恒为 0）
 * @param neverDetected  该类型在所有预期档位是否都未命中
 * @param recommendation 中文改进建议
 */
public record BypassEntry(
        CheatType cheatType,
        List<String> sources,
        SampleTierName tier,
        int samples,
        int hits,
        boolean neverDetected,
        String recommendation) {

    public BypassEntry {
        sources = List.copyOf(sources);
    }

    /** 严重度标签：全部档位未命中比部分档位漏检更严重。 */
    public String severity() {
        return neverDetected ? "从未检出" : "部分档位漏检";
    }
}
