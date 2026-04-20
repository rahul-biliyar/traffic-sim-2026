package com.trafficgame.engine.spatial;

import com.trafficgame.engine.util.Vec2;

import java.util.*;

/**
 * Grid-based spatial hash for fast 2D proximity queries.
 * Divides the world into uniform cells and maps items to their cell.
 */
public final class GridSpatialIndex<T> implements SpatialIndex<T> {

    private final double cellSize;
    private final Map<Long, List<T>> cells = new HashMap<>();
    private int totalItems = 0;

    public GridSpatialIndex(double cellSize) {
        if (cellSize <= 0) throw new IllegalArgumentException("cellSize must be positive");
        this.cellSize = cellSize;
    }

    @Override
    public void insert(T item, Vec2 position) {
        long key = cellKey(position.x(), position.y());
        cells.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        totalItems++;
    }

    @Override
    public void remove(T item, Vec2 position) {
        long key = cellKey(position.x(), position.y());
        List<T> cell = cells.get(key);
        if (cell != null && cell.remove(item)) {
            totalItems--;
            if (cell.isEmpty()) cells.remove(key);
        }
    }

    @Override
    public void update(T item, Vec2 oldPosition, Vec2 newPosition) {
        long oldKey = cellKey(oldPosition.x(), oldPosition.y());
        long newKey = cellKey(newPosition.x(), newPosition.y());
        if (oldKey != newKey) {
            remove(item, oldPosition);
            insert(item, newPosition);
        }
    }

    @Override
    public List<T> queryRadius(Vec2 center, double radius) {
        double r2 = radius * radius;
        List<T> results = new ArrayList<>();
        int minCx = (int) Math.floor((center.x() - radius) / cellSize);
        int maxCx = (int) Math.floor((center.x() + radius) / cellSize);
        int minCy = (int) Math.floor((center.y() - radius) / cellSize);
        int maxCy = (int) Math.floor((center.y() + radius) / cellSize);

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                List<T> cell = cells.get(packKey(cx, cy));
                if (cell != null) {
                    results.addAll(cell);
                }
            }
        }
        return results;
    }

    @Override
    public List<T> queryRect(double minX, double minY, double maxX, double maxY) {
        List<T> results = new ArrayList<>();
        int minCx = (int) Math.floor(minX / cellSize);
        int maxCx = (int) Math.floor(maxX / cellSize);
        int minCy = (int) Math.floor(minY / cellSize);
        int maxCy = (int) Math.floor(maxY / cellSize);

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cy = minCy; cy <= maxCy; cy++) {
                List<T> cell = cells.get(packKey(cx, cy));
                if (cell != null) {
                    results.addAll(cell);
                }
            }
        }
        return results;
    }

    @Override
    public void clear() {
        cells.clear();
        totalItems = 0;
    }

    @Override
    public int size() {
        return totalItems;
    }

    private long cellKey(double x, double y) {
        int cx = (int) Math.floor(x / cellSize);
        int cy = (int) Math.floor(y / cellSize);
        return packKey(cx, cy);
    }

    private static long packKey(int cx, int cy) {
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }
}
