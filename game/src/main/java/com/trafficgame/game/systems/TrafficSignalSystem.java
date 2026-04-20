package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.engine.graph.Node;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.Intersection;
import com.trafficgame.game.model.Intersection.IntersectionType;
import com.trafficgame.game.model.Intersection.SignalState;
import com.trafficgame.game.model.RoadNetwork;

/**
 * Updates traffic signal state machines at signalized intersections.
 */
public final class TrafficSignalSystem implements GameSystem {

    private final RoadNetwork roadNetwork;

    public TrafficSignalSystem(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
    }

    @Override
    public void update(GameState state, double dt) {
        for (Node<Intersection> node : roadNetwork.getAllNodes()) {
            Intersection intersection = node.getData();
            if (intersection.getType() != IntersectionType.SIGNAL) continue;

            double timer = intersection.getSignalTimer() - dt;
            if (timer <= 0) {
                SignalState current = intersection.getSignalState();
                SignalState next = current.next();
                intersection.setSignalState(next);

                // Determine duration based on state type
                double duration = switch (next) {
                    case GREEN_NS, GREEN_EW -> intersection.getGreenDuration();
                    case YELLOW_NS, YELLOW_EW -> GameConfig.YELLOW_DURATION;
                    case RED_ALL_1, RED_ALL_2 -> GameConfig.ALL_RED_DURATION;
                };

                intersection.setSignalTimer(duration);
            } else {
                intersection.setSignalTimer(timer);
            }
        }
    }

    @Override
    public String getName() { return "TrafficSignal"; }
}
