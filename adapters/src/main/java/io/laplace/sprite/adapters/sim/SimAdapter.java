package io.laplace.sprite.adapters.sim;

import io.laplace.sprite.core.engine.DetectionContext;
import io.laplace.sprite.core.engine.DetectionTarget;
import io.laplace.sprite.core.model.FlagEvent;

import java.util.List;

/**
 * SimAdapter（4.4）：进程内快迭代轨的检测目标。
 *
 * <p>只做「把归一化事件流交给检测引擎」这一件事；引擎可替换，真实检测引擎落地时直接换实现。</p>
 */
public final class SimAdapter implements DetectionTarget {

    private final DetectionEngine engine;

    public SimAdapter() {
        this(new HeuristicDetectionEngine());
    }

    public SimAdapter(DetectionEngine engine) {
        this.engine = engine;
    }

    @Override
    public String id() {
        return "sim-adapter";
    }

    @Override
    public List<FlagEvent> detect(DetectionContext ctx) {
        return engine.evaluate(ctx);
    }
}
