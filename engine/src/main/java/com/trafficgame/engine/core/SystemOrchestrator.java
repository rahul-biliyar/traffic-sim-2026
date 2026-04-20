package com.trafficgame.engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes registered GameSystems in order each tick.
 * Systems are run sequentially — order matters (e.g., movement before collision).
 */
public final class SystemOrchestrator {

    private final List<GameSystem> systems = new ArrayList<>();

    public void register(GameSystem system) {
        systems.add(system);
    }

    public void update(GameState state, double dt) {
        for (GameSystem system : systems) {
            system.update(state, dt);
        }
    }

    public List<GameSystem> getSystems() {
        return Collections.unmodifiableList(systems);
    }

    public void clear() {
        systems.clear();
    }
}
