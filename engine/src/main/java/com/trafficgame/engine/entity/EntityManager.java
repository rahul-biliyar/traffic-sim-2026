package com.trafficgame.engine.entity;

import com.trafficgame.engine.util.IdGenerator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of entities. Provides creation, retrieval,
 * querying by component type, and destruction.
 */
public final class EntityManager {

    private final IdGenerator idGenerator = new IdGenerator();
    private final Map<Long, Entity> entities = new ConcurrentHashMap<>();

    public Entity create() {
        Entity entity = new Entity(idGenerator.next());
        entities.put(entity.getId(), entity);
        return entity;
    }

    public Entity get(long id) {
        return entities.get(id);
    }

    public Entity remove(long id) {
        return entities.remove(id);
    }

    public boolean exists(long id) {
        return entities.containsKey(id);
    }

    public int count() {
        return entities.size();
    }

    /**
     * Returns all entities that have the specified component type.
     */
    public List<Entity> withComponent(Class<? extends Component> componentType) {
        return entities.values().stream()
                .filter(e -> e.has(componentType))
                .collect(Collectors.toList());
    }

    /**
     * Returns all entities that have ALL specified component types.
     */
    @SafeVarargs
    public final List<Entity> withComponents(Class<? extends Component>... componentTypes) {
        return entities.values().stream()
                .filter(e -> {
                    for (Class<? extends Component> type : componentTypes) {
                        if (!e.has(type)) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    public Collection<Entity> all() {
        return Collections.unmodifiableCollection(entities.values());
    }

    public void clear() {
        entities.clear();
    }
}
