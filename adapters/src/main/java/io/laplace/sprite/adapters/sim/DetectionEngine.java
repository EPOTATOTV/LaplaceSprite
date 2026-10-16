package io.laplace.sprite.adapters.sim;

import io.laplace.sprite.core.engine.DetectionContext;
import io.laplace.sprite.core.model.FlagEvent;

import java.util.List;

/**
 * 检测引擎接口（4.4）：进程内快迭代轨的判定入口。
 *
 * <p>真实检测引擎后续直接实现本接口替换启发式版本，Runner 与场景剧本不用改。</p>
 */
public interface DetectionEngine {

    /** 读归一化事件流，产出 flag。 */
    List<FlagEvent> evaluate(DetectionContext ctx);
}
