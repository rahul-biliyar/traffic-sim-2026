package com.trafficgame.engine.protocol;

/**
 * Base interface for player commands sent from client to server.
 */
public interface Command {

    /**
     * The command type identifier.
     */
    String type();
}
