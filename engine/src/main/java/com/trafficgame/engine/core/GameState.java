package com.trafficgame.engine.core;

/**
 * Marker interface for game state containers.
 * Game-specific implementations hold all mutable simulation state.
 */
public interface GameState {

    /**
     * Returns the current simulation tick number.
     */
    long getTickNumber();

    /**
     * Increments and returns the next tick number.
     */
    long advanceTick();
}
