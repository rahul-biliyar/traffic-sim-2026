package com.trafficgame.engine.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Type-safe publish/subscribe event bus for game events.
 * Thread-safe for listener registration; dispatch is synchronous within the caller's thread.
 */
public final class EventBus {

    private final Map<Class<? extends GameEvent>, List<EventListener<?>>> listeners = new ConcurrentHashMap<>();

    /**
     * Register a listener for a specific event type.
     */
    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void subscribe(Class<T> eventType, EventListener<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Remove a listener for a specific event type.
     */
    public <T extends GameEvent> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        List<EventListener<?>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    /**
     * Publish an event synchronously to all registered listeners for its type.
     */
    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void publish(T event) {
        List<EventListener<?>> list = listeners.get(event.getClass());
        if (list != null) {
            for (EventListener<?> listener : list) {
                ((EventListener<T>) listener).onEvent(event);
            }
        }
    }

    /**
     * Remove all listeners.
     */
    public void clear() {
        listeners.clear();
    }

    /**
     * Returns the number of listeners registered for a given event type.
     */
    public int listenerCount(Class<? extends GameEvent> eventType) {
        List<EventListener<?>> list = listeners.get(eventType);
        return list == null ? 0 : list.size();
    }
}
