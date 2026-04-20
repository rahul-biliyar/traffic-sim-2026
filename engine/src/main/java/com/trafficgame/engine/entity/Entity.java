package com.trafficgame.engine.entity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight entity — an ID with a map of components.
 * Components are pure data; behavior lives in GameSystems.
 */
public final class Entity {

    private final long id;
    private final Map<Class<? extends Component>, Component> components = new HashMap<>();

    public Entity(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public <T extends Component> Entity add(T component) {
        components.put(component.getClass(), component);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T get(Class<T> type) {
        return (T) components.get(type);
    }

    public <T extends Component> boolean has(Class<T> type) {
        return components.containsKey(type);
    }

    public <T extends Component> Entity remove(Class<T> type) {
        components.remove(type);
        return this;
    }

    public Map<Class<? extends Component>, Component> getComponents() {
        return Collections.unmodifiableMap(components);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Entity{id=" + id + ", components=" + components.keySet() + "}";
    }
}
