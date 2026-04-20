package com.trafficgame.engine.graph;

import java.util.*;

/**
 * Generic directed weighted graph using adjacency lists.
 *
 * @param <N> node data type
 * @param <E> edge data type
 */
public class Graph<N, E> {

    private final Map<String, Node<N>> nodes = new LinkedHashMap<>();
    private final Map<String, Edge<E>> edges = new LinkedHashMap<>();
    private final Map<String, List<String>> outgoing = new HashMap<>(); // nodeId -> list of edgeIds
    private final Map<String, List<String>> incoming = new HashMap<>(); // nodeId -> list of edgeIds

    public Node<N> addNode(String id, com.trafficgame.engine.util.Vec2 position, N data) {
        Node<N> node = new Node<>(id, position, data);
        nodes.put(id, node);
        outgoing.putIfAbsent(id, new ArrayList<>());
        incoming.putIfAbsent(id, new ArrayList<>());
        return node;
    }

    public Edge<E> addEdge(String id, String fromNodeId, String toNodeId, double weight, E data) {
        if (!nodes.containsKey(fromNodeId) || !nodes.containsKey(toNodeId)) {
            throw new IllegalArgumentException("Both nodes must exist: " + fromNodeId + ", " + toNodeId);
        }
        Edge<E> edge = new Edge<>(id, fromNodeId, toNodeId, weight, data);
        edges.put(id, edge);
        outgoing.get(fromNodeId).add(id);
        incoming.get(toNodeId).add(id);
        return edge;
    }

    public Node<N> getNode(String id) {
        return nodes.get(id);
    }

    public Edge<E> getEdge(String id) {
        return edges.get(id);
    }

    public void removeNode(String id) {
        // Remove all edges connected to this node
        List<String> out = outgoing.getOrDefault(id, List.of());
        List<String> in = incoming.getOrDefault(id, List.of());
        for (String edgeId : new ArrayList<>(out)) removeEdge(edgeId);
        for (String edgeId : new ArrayList<>(in)) removeEdge(edgeId);
        nodes.remove(id);
        outgoing.remove(id);
        incoming.remove(id);
    }

    public void removeEdge(String id) {
        Edge<E> edge = edges.remove(id);
        if (edge != null) {
            outgoing.getOrDefault(edge.getFromNodeId(), List.of()).remove(id);
            incoming.getOrDefault(edge.getToNodeId(), List.of()).remove(id);
        }
    }

    /**
     * Returns all outgoing edges from a node.
     */
    public List<Edge<E>> getOutgoingEdges(String nodeId) {
        return outgoing.getOrDefault(nodeId, List.of()).stream()
                .map(edges::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Returns all incoming edges to a node.
     */
    public List<Edge<E>> getIncomingEdges(String nodeId) {
        return incoming.getOrDefault(nodeId, List.of()).stream()
                .map(edges::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Returns neighbor nodes reachable from the given node via outgoing edges.
     */
    public List<Node<N>> getNeighbors(String nodeId) {
        return getOutgoingEdges(nodeId).stream()
                .map(e -> nodes.get(e.getToNodeId()))
                .filter(Objects::nonNull)
                .toList();
    }

    public Collection<Node<N>> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Collection<Edge<E>> getAllEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }

    public boolean hasNode(String id) { return nodes.containsKey(id); }
    public boolean hasEdge(String id) { return edges.containsKey(id); }
}
