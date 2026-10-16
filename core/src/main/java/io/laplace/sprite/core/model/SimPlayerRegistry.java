package io.laplace.sprite.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟玩家注册表（11.4 Tab 名单）。
 */
public final class SimPlayerRegistry {

    private final Map<String, SimPlayer> bySimId = new ConcurrentHashMap<>();

    public void register(SimPlayer player) {
        bySimId.put(player.simId(), player);
    }

    public void unregister(String simId) {
        bySimId.remove(simId);
    }

    public Optional<SimPlayer> bySimId(String simId) {
        return Optional.ofNullable(bySimId.get(simId));
    }

    public Optional<SimPlayer> byName(String name) {
        return bySimId.values().stream().filter(p -> p.name().equals(name)).findFirst();
    }

    public List<SimPlayer> byType(PlayerType type) {
        return bySimId.values().stream().filter(p -> p.type() == type).toList();
    }

    public Collection<SimPlayer> all() {
        return List.copyOf(bySimId.values());
    }

    /** 屏幕 / Tab 排序：管理员优先、其后作弊、最后合法，同类按名升序。 */
    public List<SimPlayer> tabOrder() {
        List<SimPlayer> list = new ArrayList<>(bySimId.values());
        list.sort((a, b) -> {
            int byType = Integer.compare(rank(a.type()), rank(b.type()));
            return byType != 0 ? byType : a.name().compareTo(b.name());
        });
        return list;
    }

    private static int rank(PlayerType type) {
        return switch (type) {
            case ADMIN -> 0;
            case CHEAT -> 1;
            case LEGIT -> 2;
        };
    }

    public int size() {
        return bySimId.size();
    }

    public void clear() {
        bySimId.clear();
    }
}
