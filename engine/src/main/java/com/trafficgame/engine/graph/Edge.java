package com.trafficgame.engine.graph;

/**
 * A directed edge in a weighted graph.
 *
 * @param <D> type of domain-specific data stored on this edge
 */
public final class Edge<D> {

    private final String id;
    private final String fromNodeId;
    private final String toNodeId;
    private double weight;
    private D data;

    public Edge(String id, String fromNodeId, String toNodeId, double weight, D data) {
        this.id = id;
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
        this.weight = weight;
        this.data = data;
    }

    public String getId() { return id; }
    public String getFromNodeId() { return fromNodeId; }
    public String getToNodeId() { return toNodeId; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public D getData() { return data; }
    public void setData(D data) { this.data = data; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge<?> other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Edge{" + id + ": " + fromNodeId + " → " + toNodeId + " w=" + weight + "}";
    }
}
