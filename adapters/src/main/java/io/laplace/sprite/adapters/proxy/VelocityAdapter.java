package io.laplace.sprite.adapters.proxy;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Velocity（14.2）：描述符由注解处理器生成，事件桥要处理异步挂起。 */
public final class VelocityAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Velocity",
            "构建 plugins/ 目录，velocity-plugin.json 由 @Plugin 注解生成",
            "velocity-plugin.json",
            "Velocity 事件循环 + 异步任务（EventTask / Continuation）",
            AdapterStatus.IMPLEMENTED,
            List.of("EventTask.async 提交后事件不落流，Continuation 恢复才补进流",
                    "dependencies[].optional 映射软依赖，不影响装配顺序",
                    "登录阶段事件必须在会话状态就绪后断言，避免半完成状态误判"));

    public VelocityAdapter() {
        super("velocity", PlatformFamily.VELOCITY, SupportRange.any(), FACTS);
    }
}
