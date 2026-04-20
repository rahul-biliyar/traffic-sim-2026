package com.trafficgame.engine.graph;

import com.trafficgame.engine.util.Vec2;

/**
 * A node in a directed weighted graph.
 *
 * @param <D> type of domain-specific data stored on this node
 */
public final class Node<D> {

    private final String id;
    private final Vec2 position;
    private D data;

    public Node(String id, Vec2 position, D data) {
        this.id = id;
        this.position = position;
        this.data = data;
    }

    public String getId() { return id; }
    public Vec2 getPosition() { return position; }
    public D getData() { return data; }
    public void setData(D data) { this.data = data; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node<?> other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Node{" + id + " @ " + position + "}";
    }
}
