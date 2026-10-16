package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.artifact.ArtifactResolverFactory;
import io.laplace.sprite.adapters.event.EventBridges;
import io.laplace.sprite.adapters.lifecycle.SimulatedLifecycleHook;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.ArtifactResolver;
import io.laplace.sprite.core.spi.EventBridge;
import io.laplace.sprite.core.spi.InstallLayout;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import io.laplace.sprite.core.spi.LifecycleHook;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;
import java.util.Objects;

/**
 * 适配器公共骨架（14.4）：把 id / 家族 / 支持区间 / 平台事实交给子类声明，其余 SPI 走共享实现。
 *
 * <p>这样每个具体适配器只剩「我是谁、支持哪个版本区间、平台表格里写了什么」三件事。</p>
 */
public abstract class AbstractLaplaceAdapter implements LaplaceAdapter {

    private final String id;
    private final PlatformFamily family;
    private final SupportRange supportRange;
    private final PlatformFacts facts;

    protected AbstractLaplaceAdapter(String id, PlatformFamily family, SupportRange supportRange,
                                     PlatformFacts facts) {
        this.id = Objects.requireNonNull(id, "id");
        this.family = Objects.requireNonNull(family, "family");
        this.supportRange = supportRange == null ? SupportRange.any() : supportRange;
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public PlatformFamily family() {
        return family;
    }

    @Override
    public SupportRange supportRange() {
        return supportRange;
    }

    public String assemblyDescription() {
        return facts.assemblyDescription();
    }

    public String descriptorFile() {
        return facts.descriptorFile();
    }

    public String schedulerModel() {
        return facts.schedulerModel();
    }

    public AdapterStatus adapterStatus() {
        return facts.status();
    }

    public List<String> testAdaptationNotes() {
        return facts.testAdaptationNotes();
    }

    @Override
    public ArtifactResolver artifactResolver() {
        return ArtifactResolverFactory.defaultResolver();
    }

    @Override
    public InstallLayout installLayout() {
        return InstallLayouts.forFamily(family);
    }

    @Override
    public LifecycleHook lifecycleHook() {
        return new SimulatedLifecycleHook();
    }

    @Override
    public EventBridge eventBridge() {
        return EventBridges.of(family);
    }

    @Override
    public String toString() {
        return id;
    }
}
