package io.laplace.sprite.crashdiag;

import io.laplace.sprite.crashdiag.FrameAttributor.Suspect;

import java.time.Instant;
import java.util.List;

/**
 * 结构化崩溃诊断（15.5），可作为测试报告的一节。
 *
 * @param id                诊断 id
 * @param instanceId        实例 id
 * @param category          分类
 * @param signature         命中签名的 id；未命中为 unmatched
 * @param suspects          嫌疑 mod，按帧命中数降序
 * @param classIndexCoverage 类索引覆盖率
 * @param humanAdvice       人话建议（改哪个文件、升哪个依赖）
 * @param artifacts         现场采集结果
 * @param collectedAt       诊断时间
 */
public record CrashDiagnosis(String id, String instanceId, CrashCategory category, String signature,
                             List<Suspect> suspects, double classIndexCoverage, List<String> humanAdvice,
                             CrashArtifacts artifacts, Instant collectedAt) {

    public CrashDiagnosis {
        suspects = suspects == null ? List.of() : List.copyOf(suspects);
        humanAdvice = humanAdvice == null ? List.of() : List.copyOf(humanAdvice);
    }

    /** 覆盖率偏低时报告要显式提示不确定。 */
    public boolean lowCoverage() {
        return classIndexCoverage < FrameAttributor.LOW_COVERAGE_THRESHOLD;
    }
}
