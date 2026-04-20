package com.trafficgame.engine.event;

/**
 * Listener for a specific type of game event.
 *
 * @param <T> the event type this listener handles
 */
@FunctionalInterface
public interface EventListener<T extends GameEvent> {

    void onEvent(T event);
}
