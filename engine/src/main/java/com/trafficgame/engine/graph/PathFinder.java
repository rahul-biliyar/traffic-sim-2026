package com.trafficgame.engine.graph;

import java.util.*;
import java.util.function.BiFunction;

/**
 * A* pathfinding on a generic Graph.
 * Returns the shortest path as a list of edge IDs.
 */
public final class PathFinder {

    private PathFinder() {}

    /**
     * Find shortest path from startNodeId to goalNodeId using A*.
     *
     * @param graph         the graph to search
     * @param startNodeId   starting node ID
     * @param goalNodeId    goal node ID
     * @param weightFunction optional custom weight function (edge, graph) -> cost.
     *                       If null, uses edge.getWeight(). Return negative to mark impassable.
     * @return list of edge IDs forming the shortest path, or empty if no path exists
     */
    public static <N, E> List<String> findPath(
            Graph<N, E> graph,
            String startNodeId,
            String goalNodeId,
            BiFunction<Edge<E>, Graph<N, E>, Double> weightFunction) {

        Node<N> startNode = graph.getNode(startNodeId);
        Node<N> goalNode = graph.getNode(goalNodeId);
        if (startNode == null || goalNode == null) {
            return List.of();
        }
        if (startNodeId.equals(goalNodeId)) {
            return List.of();
        }

        PriorityQueue<NodeEntry> open = new PriorityQueue<>();
        Map<String, Double> gScore = new HashMap<>();
        Map<String, PathStep> cameFrom = new HashMap<>();
        Set<String> closed = new HashSet<>();

        gScore.put(startNodeId, 0.0);
        open.add(new NodeEntry(startNodeId, heuristic(startNode, goalNode)));

        while (!open.isEmpty()) {
            NodeEntry current = open.poll();
            String currentId = current.nodeId;

            if (currentId.equals(goalNodeId)) {
                return reconstructPath(cameFrom, startNodeId, goalNodeId);
            }

            if (!closed.add(currentId)) continue;

            for (Edge<E> edge : graph.getOutgoingEdges(currentId)) {
                String neighborId = edge.getToNodeId();
                if (closed.contains(neighborId)) continue;

                double edgeWeight = (weightFunction != null)
                        ? weightFunction.apply(edge, graph)
                        : edge.getWeight();

                if (edgeWeight < 0) continue; // impassable

                double tentativeG = gScore.getOrDefault(currentId, Double.MAX_VALUE) + edgeWeight;

                if (tentativeG < gScore.getOrDefault(neighborId, Double.MAX_VALUE)) {
                    gScore.put(neighborId, tentativeG);
                    cameFrom.put(neighborId, new PathStep(currentId, edge.getId()));
                    double f = tentativeG + heuristic(graph.getNode(neighborId), goalNode);
                    open.add(new NodeEntry(neighborId, f));
                }
            }
        }

        return List.of(); // No path found
    }

    /**
     * Convenience method using default edge weights.
     */
    public static <N, E> List<String> findPath(Graph<N, E> graph, String start, String goal) {
        return findPath(graph, start, goal, null);
    }

    private static <N> double heuristic(Node<N> from, Node<N> to) {
        return from.getPosition().distanceTo(to.getPosition());
    }

    private static List<String> reconstructPath(Map<String, PathStep> cameFrom, String start, String goal) {
        List<String> edgeIds = new ArrayList<>();
        String current = goal;
        while (!current.equals(start)) {
            PathStep step = cameFrom.get(current);
            if (step == null) return List.of(); // broken path
            edgeIds.add(step.edgeId);
            current = step.prevNodeId;
        }
        Collections.reverse(edgeIds);
        return edgeIds;
    }

    private record PathStep(String prevNodeId, String edgeId) {}

    private record NodeEntry(String nodeId, double fScore) implements Comparable<NodeEntry> {
        @Override
        public int compareTo(NodeEntry other) {
            return Double.compare(this.fScore, other.fScore);
        }
    }
}
