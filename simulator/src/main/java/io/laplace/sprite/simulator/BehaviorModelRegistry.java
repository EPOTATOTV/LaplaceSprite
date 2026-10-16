package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 行为模型库注册表（4.1）：把每个 {@link BehaviorProfile} 映射到对应的合法行为模型。
 *
 * <p>构造时校验覆盖完整，缺一个 profile 直接失败，避免运行期才暴露。</p>
 */
public final class BehaviorModelRegistry {

    private final Map<BehaviorProfile, BehaviorModel> models = new EnumMap<>(BehaviorProfile.class);

    public BehaviorModelRegistry() {
        register(new WalkModel());
        register(new RunModel());
        register(new SprintModel());
        register(new JumpModel());
        register(new SneakModel());
        register(new SwimModel());
        register(new ElytraModel());
        register(new BoatModel());
        register(new RideModel());
        register(new GodBridgeModel());
        register(new TellyModel());
        register(new NinjaModel());
        register(new MlgModel());
        register(new PearlModel());
        register(new KnockbackResponseModel());
        register(new ClickingModel());
        register(new TargetSwitchModel());
        register(new MultiEntityModel());
        register(new InventorySortModel());
        register(new PlaceModel());
        register(new MineModel());
        if (models.size() != BehaviorProfile.values().length) {
            throw new IllegalStateException("行为模型注册不完整: " + models.size()
                    + "/" + BehaviorProfile.values().length);
        }
    }

    private void register(BehaviorModel model) {
        models.put(model.profile(), model);
    }

    public BehaviorModel of(BehaviorProfile profile) {
        BehaviorModel model = models.get(profile);
        if (model == null) {
            throw new IllegalArgumentException("未注册的行为模型: " + profile);
        }
        return model;
    }

    public Set<BehaviorProfile> supported() {
        return Set.copyOf(models.keySet());
    }
}
