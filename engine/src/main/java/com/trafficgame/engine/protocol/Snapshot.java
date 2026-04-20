package com.trafficgame.engine.protocol;

/**
 * Base interface for full game state snapshots sent to the client on initial connect.
 */
public interface Snapshot {

    long tickNumber();
}
