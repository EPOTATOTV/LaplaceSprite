package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 各行为模型共用的稀疏事件：客户端品牌、上行包、tick 指标与数据一致性核对。
 * 合法玩家的绝大部分 tick 只有 Move，其余事件按小概率出现。
 */
final class SparseEvents {

    /** 模拟时间起点，固定值保证同 seed 可复现。 */
    private static final long EPOCH_MILLIS = 1_700_000_000_000L;

    /** 一个 tick 的毫秒数，20 TPS。 */
    private static final int TICK_MILLIS = 50;

    private static final String[] UPSTREAM_PACKETS = {
            "player_position", "player_look", "player_digging", "player_block_placement",
            "use_item", "swing_arm", "keep_alive", "client_settings"};

    private SparseEvents() {
    }

    static Instant timestamp(long originMillis, int offsetTicks) {
        return Instant.ofEpochMilli(originMillis + offsetTicks * (long) TICK_MILLIS);
    }

    static long epochMillis() {
        return EPOCH_MILLIS;
    }

    static String upstreamPacket(Random rng) {
        return UPSTREAM_PACKETS[rng.nextInt(UPSTREAM_PACKETS.length)];
    }

    static void brand(List<Event> out, String playerId, long tick, Instant timestamp, PlatformKey platform) {
        out.add(new Event.Brand(playerId, tick, timestamp, platform, "vanilla"));
    }

    /** 约 4% 的 tick 带一个上行包；每 20 tick 一个 tick 指标；偶发数据一致性核对。 */
    static void noise(List<Event> out, String playerId, long tick, Instant timestamp, PlatformKey platform,
                      Random rng) {
        if (rng.nextDouble() < 0.04) {
            out.add(new Event.Packet(playerId, tick, timestamp, platform, "client->server",
                    upstreamPacket(rng), 16 + rng.nextInt(96)));
        }
        if (tick % 20 == 0) {
            out.add(new Event.Tick(playerId, tick, timestamp, platform, 19.8 + rng.nextDouble() * 0.2,
                    1.0 + rng.nextDouble() * 7.0));
        }
        if (rng.nextDouble() < 0.01) {
            String value = String.format(Locale.ROOT, "%.3f", rng.nextDouble() * 512.0);
            out.add(new Event.DataConsistency(playerId, tick, timestamp, platform, "position_x", value, value));
        }
    }
}
