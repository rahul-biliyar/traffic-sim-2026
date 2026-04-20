package com.trafficgame.engine.protocol;

/**
 * Base interface for incremental state updates sent each tick.
 */
public interface StateDelta {

    long tickNumber();
}
