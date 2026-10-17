package io.laplace.sprite.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * 注入计数门禁（15.11 P0）。
 *
 * <p>require/expect 的取舍：require 是 Mixin 的硬校验，数量不符必须让实例起不来，所以这里
 * 记为硬失败；expect 在 Mixin 里只是「期望」、本身不会失败，这里保持软语义只出告警。
 * 若把 expect 也判失败，会让大量使用 expect 的可选兼容 mixin 无法进 CI。</p>
 */
public final class InjectionGate {

    private InjectionGate() {
    }

    /**
     * 用实际观测到的注入点数量核对声明。
     *
     * @param declarations 声明列表
     * @param observed     按声明取实际注入点数量；真实运行由 Mixin 日志/计数器提供，离线测试直接注入
     */
    public static InjectionGateResult evaluate(List<InjectionDeclaration> declarations,
                                               ToIntFunction<InjectionDeclaration> observed) {
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(observed, "observed");
        List<InjectionFailure> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (InjectionDeclaration declaration : declarations) {
            int actual = observed.applyAsInt(declaration);
            if (actual == declaration.declaredCount()) {
                continue;
            }
            String detail = "声明 %d 个，实际 %d 个".formatted(declaration.declaredCount(), actual);
            if (declaration.requireMode().soft()) {
                warnings.add("%s 的 %s 上 %s 注入点数量不符（%s）：expect 为软约束，仅告警"
                        .formatted(declaration.modId(), declaration.targetClass(),
                                declaration.kind().annotation(), detail));
                continue;
            }
            failures.add(new InjectionFailure(declaration.modId(), declaration.targetClass(),
                    declaration.kind(), "",
                    "注入点数量与声明不符：%s 的 %s 上 %s %s（%s）；require 要求精确匹配，启动期即失败"
                            .formatted(declaration.modId(), declaration.targetClass(),
                                    declaration.kind().annotation(),
                                    declaration.method() == null ? "" : declaration.method(), detail)));
        }
        return new InjectionGateResult(failures.isEmpty(), failures, warnings);
    }

    /** 启动日志里的注入异常全部视为硬失败：任一条都阻塞 CI 门禁。 */
    public static InjectionGateResult fromLaunchLog(String launchLog) {
        List<InjectionFailure> failures = LaunchLogScanner.scan(launchLog);
        return new InjectionGateResult(failures.isEmpty(), failures, List.of());
    }
}
