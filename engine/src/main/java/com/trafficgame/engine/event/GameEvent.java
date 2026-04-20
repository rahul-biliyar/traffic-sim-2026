package com.trafficgame.engine.event;

/**
 * Marker interface for all game events dispatched through the EventBus.
 */
public interface GameEvent {

    /**
     * Timestamp (tick number) when this event was created.
     */
    long tick();
}
