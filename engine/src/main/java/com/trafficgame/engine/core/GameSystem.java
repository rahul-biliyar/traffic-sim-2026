package com.trafficgame.engine.core;

/**
 * A system that processes game state each tick.
 * Systems are executed in registration order by the SystemOrchestrator.
 */
public interface GameSystem {

    /**
     * Update this system for one tick.
     *
     * @param state the current game state
     * @param dt    delta time in seconds for this tick
     */
    void update(GameState state, double dt);

    /**
     * Returns the display name of this system (for logging/debugging).
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
