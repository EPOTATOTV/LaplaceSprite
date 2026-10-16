package io.laplace.sprite.sandbox;

import io.laplace.sprite.core.model.RunStatus;

import java.util.List;

/**
 * 沙箱用例结果（15.4）。
 *
 * <p>OOM 自愈跑通也照样带 {@code oomKilled=true} 且状态为 FAILED——「曾被 OOM 杀掉」本身就是
 * 内存不足 bug 的一类结果，不能因为重试成功就静默改判。</p>
 *
 * @param caseId        用例 id
 * @param status        判定
 * @param oomKilled     本轮是否出现过 OOM 杀容器
 * @param restarts      实际退避重启次数
 * @param worldRestored 场景结束后是否成功回灌基线快照
 * @param auditFindings 启动前的敏感路径审计条目（含未命中的）
 * @param logs          容器输出与调度日志
 */
public record SandboxRunResult(String caseId, RunStatus status, boolean oomKilled, int restarts,
                               boolean worldRestored, List<SandboxAudit> auditFindings, List<String> logs) {

    public SandboxRunResult {
        auditFindings = auditFindings == null ? List.of() : List.copyOf(auditFindings);
        logs = logs == null ? List.of() : List.copyOf(logs);
    }

    public boolean passed() {
        return status == RunStatus.PASSED;
    }

    public List<SandboxAudit> sensitiveFindings() {
        return auditFindings.stream().filter(SandboxAudit::sensitive).toList();
    }
}
