package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;

import java.time.Instant;
import java.util.List;

/**
 * CONTENT：注册表 / 配方 / 标签的内容一致性异常。
 *
 * <p>异常事件追加在基线之后，基线本身不改写。参数只放大异常规模。</p>
 */
public final class ContentAnomalyInjector extends AbstractSampleInjector {

    @Override
    public String id() {
        return "content";
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long tick = lastTick(baseline) + 1;
        String player = request.playerId();
        switch (request.sample().sampleType()) {
            case "DUPLICATE_REGISTRY_ENTRY" -> duplicateRegistry(out, request, tier, tick, origin, player);
            case "MISSING_RECIPE_REFERENCE" -> missingRecipe(out, request, tier, tick, origin, player);
            case "DANGLING_TAG_REFERENCE" -> danglingTag(out, request, tier, tick, origin, player);
            default -> emitUnknown(out, request, tick, origin, player);
        }
    }

    private static void duplicateRegistry(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                          long tick, Instant origin, String player) {
        String registry = paramString(tier, "registry", "minecraft:item");
        String entry = paramString(tier, "entry", "minecraft:stone");
        int duplicates = paramInt(tier, "duplicateCount", 2);
        for (int i = 0; i < duplicates; i++) {
            out.emit(new Event.Registry(player, tick, timestampAt(tick, origin), request.platform(),
                    registry, entry, "REGISTER"));
        }
    }

    private static void missingRecipe(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                      long tick, Instant origin, String player) {
        String recipe = paramString(tier, "recipe", "minecraft:crafting_table");
        int missing = paramInt(tier, "missingRefs", 2);
        for (int i = 0; i < missing; i++) {
            out.emit(new Event.Registry(player, tick, timestampAt(tick, origin), request.platform(),
                    "minecraft:recipe", "laplace:missing_ingredient_" + i, "RESOLVE"));
        }
        out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin), request.platform(),
                "recipe.output:" + recipe, recipe, "minecraft:air"));
    }

    private static void danglingTag(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                    long tick, Instant origin, String player) {
        String tag = paramString(tier, "tag", "laplace:missing_tag");
        int missing = paramInt(tier, "missingTags", 2);
        for (int i = 0; i < missing; i++) {
            out.emit(new Event.Registry(player, tick, timestampAt(tick, origin), request.platform(),
                    "minecraft:item_tag", tag + "_" + i, "RESOLVE"));
        }
        out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin), request.platform(),
                "tag.member:" + tag, tag + "_0", "absent"));
    }

    private static void emitUnknown(InMemoryEventStream out, CheatInjectionRequest request, long tick,
                                    Instant origin, String player) {
        out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin), request.platform(),
                "content.sample", request.sample().sampleType(), "unhandled"));
    }
}
