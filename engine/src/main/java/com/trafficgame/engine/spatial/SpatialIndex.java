package com.trafficgame.engine.spatial;

import com.trafficgame.engine.util.Vec2;
import java.util.List;

/**
 * Interface for spatial queries on 2D entities.
 */
public interface SpatialIndex<T> {

    void insert(T item, Vec2 position);

    void remove(T item, Vec2 position);

    void update(T item, Vec2 oldPosition, Vec2 newPosition);

    /**
     * Returns all items within the given radius of the center point.
     */
    List<T> queryRadius(Vec2 center, double radius);

    /**
     * Returns all items within the axis-aligned bounding box.
     */
    List<T> queryRect(double minX, double minY, double maxX, double maxY);

    void clear();

    int size();
}
