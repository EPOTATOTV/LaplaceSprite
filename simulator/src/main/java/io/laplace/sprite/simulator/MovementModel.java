package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 位移类行为的公共骨架：逐 tick 把行为模型的意图喂给 {@link PhysicsPredictor}，
 * 由原版物理算出应到位置，再叠加 {@link SubToleranceNoise} 的亚容差噪声产出 Move，
 * 并按稀疏比例叠加上行包、tick 指标与越区块 ChunkLoad。
 *
 * <p>模型只表达意图：期望速度（{@link #speed()}）、是否疾跑/潜行、是否起跳或受冲量；
 * 逐 tick 位移不再由模型给定，而是原版摩擦/阻力/重力的结果，所以相邻 Move 之间不会瞬移。
 * 转向每 tick 限幅并叠加亚容差抖动，合计不超过 6°；俯仰角围绕水平有界波动，
 * 保证搭桥/放置这类"必须低头"的行为之外，合法玩家的平均俯仰贴近 0。</p>
 */
abstract class MovementModel implements BehaviorModel {

    /** 单 tick 基础转角上限（度）；叠加亚容差抖动后不超过 6.0°。 */
    private static final double BASE_TURN_DEGREES = 4.5;

    /** 俯仰角向水平回拉的比例，避免无界漂移把平均俯仰抬进低头区间。 */
    private static final double PITCH_RECENTER = 0.9;

    private final BehaviorProfile profile;

    protected MovementModel(BehaviorProfile profile) {
        this.profile = profile;
    }

    @Override
    public final BehaviorProfile profile() {
        return profile;
    }

    /** 期望稳态水平速度（格/tick），作为物理预测器的输入意图。 */
    protected abstract double speed();

    /** 允许逐 tick 变化的期望水平速度，默认恒定。 */
    protected double speed(int tick) {
        return speed();
    }

    protected boolean sprinting() {
        return false;
    }

    protected boolean sneaking() {
        return false;
    }

    /** 运动介质，默认陆地；游泳、鞘翅、载具覆写。 */
    protected MotionMode motionMode() {
        return MotionMode.GROUND;
    }

    /** 本 tick 的竖直冲量（格/tick），默认不起跳；只有落地时预测器才会采纳。 */
    protected double jumpImpulse(int tick) {
        return 0.0;
    }

    /** 本 tick 沿视线的水平冲量（格/tick），默认无；击退与珍珠推进用它。 */
    protected double horizontalImpulse(int tick) {
        return 0.0;
    }

    protected double startY() {
        return 64.0;
    }

    /** 该行为脚下的地面高度，默认与起点等高；下坠类行为把地面放到起点下方。 */
    protected double groundY() {
        return startY();
    }

    /** 行为专属事件（放置方块、挥砍、整理背包等），默认不产出。 */
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
    }

    /**
     * 固定视角俯仰角（度，正值为低头）；NaN 表示俯仰角按随机游走。
     * 持续在脚下放置方块的行为必须低头看向放置面，否则原版准星取不到方块面。
     */
    protected float viewPitch() {
        return Float.NaN;
    }

    @Override
    public final List<Event> generate(String playerId, PlatformKey platform, long startTick, Random rng, int ticks) {
        List<Event> out = new ArrayList<>();
        PredictionState state = new PredictionState(rng.nextDouble() * 512.0, startY(),
                rng.nextDouble() * 512.0, (float) (rng.nextDouble() * 360.0),
                (float) ((rng.nextDouble() - 0.5) * 20.0), 0.0, 0.0, 0.0, groundY(),
                startY() <= groundY());
        Instant first = SparseEvents.timestamp(SparseEvents.epochMillis(), 0);
        SparseEvents.brand(out, playerId, startTick, first, platform);
        for (int index = 0; index < ticks; index++) {
            long tick = startTick + index;
            Instant timestamp = SparseEvents.timestamp(SparseEvents.epochMillis(), index);
            int chunkX = (int) Math.floor(state.x()) >> 4;
            int chunkZ = (int) Math.floor(state.z()) >> 4;
            state = turn(state, rng);
            float lockedPitch = viewPitch();
            if (!Float.isNaN(lockedPitch)) {
                state = state.withYawPitch(state.yaw(), lockedPitch);
            }
            MovementInput input = new MovementInput(motionMode(), speed(index), sprinting(), sneaking(),
                    jumpImpulse(index), horizontalImpulse(index));
            state = PhysicsPredictor.predict(state, input, rng);
            // 噪声只落在上报的事件上，不回灌物理状态，避免逐 tick 累积漂移
            Position position = new Position(state.x() + SubToleranceNoise.position(rng), state.y(),
                    state.z() + SubToleranceNoise.position(rng), state.yaw(), state.pitch());
            out.add(new Event.Move(playerId, tick, timestamp, platform, position.x(), position.y(),
                    position.z(), position.yaw(), position.pitch(), state.onGround(), sprinting(), sneaking()));
            SparseEvents.noise(out, playerId, tick, timestamp, platform, rng);
            int nextChunkX = (int) Math.floor(state.x()) >> 4;
            int nextChunkZ = (int) Math.floor(state.z()) >> 4;
            if (nextChunkX != chunkX || nextChunkZ != chunkZ) {
                out.add(new Event.ChunkLoad(playerId, tick, timestamp, platform, nextChunkX, nextChunkZ));
            }
            decorate(playerId, tick, timestamp, platform, position, rng, out);
        }
        return out;
    }

    /** 在脚下放置方块：产出 Interact(place) 与对应的 BlockChange，坐标由位置取整得到。 */
    protected static void placeBlock(String playerId, long tick, Instant timestamp, PlatformKey platform,
                                     Position position, String blockType, List<Event> out) {
        int x = position.blockX();
        int y = position.blockY() - 1;
        int z = position.blockZ();
        out.add(new Event.Interact(playerId, tick, timestamp, platform, blockType, x, y, z, "place"));
        out.add(new Event.BlockChange(playerId, tick, timestamp, platform, x, y, z, "air", blockType));
    }

    /** 朝向按限幅角度游走；俯仰角向水平回拉，保持有界。 */
    private static PredictionState turn(PredictionState state, Random rng) {
        double yawStep = turnStep(rng);
        float yaw = (float) (((state.yaw() + yawStep) % 360.0 + 360.0) % 360.0);
        double pitchStep = turnStep(rng);
        float pitch = (float) Math.max(-90.0, Math.min(90.0, state.pitch() * PITCH_RECENTER + pitchStep));
        return state.withYawPitch(yaw, pitch);
    }

    /** 单 tick 转角：基础限幅加亚容差抖动。 */
    private static double turnStep(Random rng) {
        return (rng.nextDouble() * 2 - 1) * BASE_TURN_DEGREES + SubToleranceNoise.turn(rng);
    }

    /** 上报位置快照（含亚容差噪声），供事件发射与行为装饰读取。 */
    protected record Position(double x, double y, double z, float yaw, float pitch) {

        int blockX() {
            return (int) Math.floor(x);
        }

        int blockY() {
            return (int) Math.floor(y);
        }

        int blockZ() {
            return (int) Math.floor(z);
        }
    }
}
