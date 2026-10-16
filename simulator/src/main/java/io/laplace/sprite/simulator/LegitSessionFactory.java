package io.laplace.sprite.simulator;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.engine.SimulatedSession;
import io.laplace.sprite.core.model.LegitTrajectory;

import java.util.ArrayList;
import java.util.List;

/** 把合法轨迹转成待执行的模拟会话（输入层出口）。 */
public final class LegitSessionFactory {

    public List<SimulatedSession> toSessions(List<LegitTrajectory> trajectories) {
        List<SimulatedSession> sessions = new ArrayList<>(trajectories.size());
        for (LegitTrajectory trajectory : trajectories) {
            sessions.add(SimulatedSession.legit(trajectory.id(), trajectory.scenario(),
                    new InMemoryEventStream(trajectory.events()), trajectory.id()));
        }
        return sessions;
    }
}
