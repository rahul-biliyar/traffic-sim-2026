package com.trafficgame.game.mapgen;

import com.trafficgame.engine.graph.Node;
import com.trafficgame.engine.util.Vec2;
import com.trafficgame.engine.terrain.TileMap;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.Intersection;
import com.trafficgame.game.model.RoadNetwork;
import com.trafficgame.game.model.RoadSegment;

import java.util.*;

/**
 * Generates a road network on top of a terrain tile map.
 * <p>
 * Rules enforced:
 * <ul>
 *   <li>Intersections placed only on passable terrain at grid intervals</li>
 *   <li>Roads only connect adjacent grid nodes with clear (passable) terrain between them</li>
 *   <li>Disconnected sub-graphs are pruned — only the largest component is kept</li>
 *   <li>Duplicate edges are prevented via {@code hasEdge} checks</li>
 *   <li>Entry/exit points are identified on map edges for vehicle spawning</li>
 * </ul>
 */
public final class RoadNetworkGenerator {

    private static final int GRID_SPACING = 8;

    private final Random random;
    private final double tileSize;

    public RoadNetworkGenerator(int seed) {
        this.random = new Random(seed);
        this.tileSize = GameConfig.TILE_SIZE;
    }

    public RoadNetwork generate(TileMap terrain) {
        RoadNetwork network = new RoadNetwork();
        int width  = terrain.getWidth();
        int height = terrain.getHeight();

        int gridW = width / GRID_SPACING + 1;
        int gridH = height / GRID_SPACING + 1;
        String[][] nodeGrid = new String[gridW][gridH];

        /* ── Phase 1: place intersection nodes ───────────── */
        for (int gx = 0; gx < gridW; gx++) {
            for (int gy = 0; gy < gridH; gy++) {
                int tx = Math.min(gx * GRID_SPACING, width - 1);
                int ty = Math.min(gy * GRID_SPACING, height - 1);

                if (!terrain.get(tx, ty).isPassable()) continue;

                ZoneType zone = ZoneType.values()[terrain.get(tx, ty).getType()];

                // Skip some nodes randomly in sparse zones
                if (zone == ZoneType.COUNTRYSIDE && random.nextDouble() > 0.4) continue;
                if (zone == ZoneType.FOREST     && random.nextDouble() > 0.2) continue;

                String nodeId = "n_" + gx + "_" + gy;
                double worldX = tx * tileSize;
                double worldY = ty * tileSize;

                Intersection.IntersectionType iType = (zone == ZoneType.CITY)
                        ? Intersection.IntersectionType.SIGNAL
                        : Intersection.IntersectionType.UNCONTROLLED;

                network.addNode(nodeId, new Vec2(worldX, worldY), new Intersection(iType));
                nodeGrid[gx][gy] = nodeId;
            }
        }

        /* ── Phase 2: connect adjacent nodes with terrain validation ── */
        for (int gx = 0; gx < gridW; gx++) {
            for (int gy = 0; gy < gridH; gy++) {
                if (nodeGrid[gx][gy] == null) continue;

                // East neighbour
                if (gx + 1 < gridW && nodeGrid[gx + 1][gy] != null) {
                    if (isPathClear(terrain, gx, gy, gx + 1, gy)) {
                        connectNodes(network, nodeGrid[gx][gy], nodeGrid[gx + 1][gy], terrain, gx, gy);
                    }
                }
                // South neighbour
                if (gy + 1 < gridH && nodeGrid[gx][gy + 1] != null) {
                    if (isPathClear(terrain, gx, gy, gx, gy + 1)) {
                        connectNodes(network, nodeGrid[gx][gy], nodeGrid[gx][gy + 1], terrain, gx, gy);
                    }
                }
            }
        }

        /* ── Phase 3: keep only largest connected component ── */
        pruneDisconnectedComponents(network);

        /* ── Phase 4: identify entry/exit nodes on map edges ── */
        List<String> edgeNodes = findEdgeNodes(network, width, height);
        network.setEntryPointCount(edgeNodes.size());
        network.setExitPointCount(edgeNodes.size());

        return network;
    }

    /* ──────────────────────────────────────────────────── */
    /*  Terrain path validation                              */
    /* ──────────────────────────────────────────────────── */

    /**
     * Checks every tile between two grid nodes; returns false if any tile
     * is impassable (water, mountain, …).
     */
    private boolean isPathClear(TileMap terrain, int fromGx, int fromGy, int toGx, int toGy) {
        int w = terrain.getWidth();
        int h = terrain.getHeight();
        int fromTx = Math.min(fromGx * GRID_SPACING, w - 1);
        int fromTy = Math.min(fromGy * GRID_SPACING, h - 1);
        int toTx   = Math.min(toGx * GRID_SPACING, w - 1);
        int toTy   = Math.min(toGy * GRID_SPACING, h - 1);

        int steps = Math.max(Math.abs(toTx - fromTx), Math.abs(toTy - fromTy));
        if (steps == 0) return true;

        for (int i = 1; i < steps; i++) {
            double t  = (double) i / steps;
            int tx = Math.max(0, Math.min((int) Math.round(fromTx + t * (toTx - fromTx)), w - 1));
            int ty = Math.max(0, Math.min((int) Math.round(fromTy + t * (toTy - fromTy)), h - 1));
            if (!terrain.get(tx, ty).isPassable()) return false;
        }
        return true;
    }

    /* ──────────────────────────────────────────────────── */
    /*  Edge creation with duplicate prevention              */
    /* ──────────────────────────────────────────────────── */

    private void connectNodes(RoadNetwork network, String fromId, String toId,
                              TileMap terrain, int gx, int gy) {

        String edgeId    = fromId + "->" + toId;
        String reverseId = toId + "->" + fromId;
        if (network.hasEdge(edgeId)) return; // already exists

        Node<Intersection> from = network.getNode(fromId);
        Node<Intersection> to   = network.getNode(toId);
        if (from == null || to == null) return;

        double distance = from.getPosition().distanceTo(to.getPosition());

        int tx = Math.min(gx * GRID_SPACING, terrain.getWidth() - 1);
        int ty = Math.min(gy * GRID_SPACING, terrain.getHeight() - 1);
        ZoneType zone = ZoneType.values()[terrain.get(tx, ty).getType()];

        RoadSegment.RoadType roadType;
        int    lanes;
        double speedLimit;

        switch (zone) {
            case CITY        -> { roadType = RoadSegment.RoadType.ARTERIAL;  lanes = 2; speedLimit = 50; }
            case SUBURB      -> { roadType = RoadSegment.RoadType.LOCAL;     lanes = 1; speedLimit = 40; }
            case COUNTRYSIDE -> { roadType = RoadSegment.RoadType.COLLECTOR; lanes = 1; speedLimit = 70; }
            default          -> { roadType = RoadSegment.RoadType.LOCAL;     lanes = 1; speedLimit = 40; }
        }

        RoadSegment segment = new RoadSegment(lanes, speedLimit, roadType);
        RoadSegment reverse = new RoadSegment(lanes, speedLimit, roadType);

        network.addEdge(edgeId, fromId, toId, distance, segment);
        network.addEdge(reverseId, toId, fromId, distance, reverse);
    }

    /* ──────────────────────────────────────────────────── */
    /*  Connectivity: largest-component pruning              */
    /* ──────────────────────────────────────────────────── */

    private void pruneDisconnectedComponents(RoadNetwork network) {
        var allNodes = new ArrayList<>(network.getAllNodes());
        if (allNodes.size() <= 1) return;

        Set<String> visited = new HashSet<>();
        List<Set<String>> components = new ArrayList<>();

        for (var node : allNodes) {
            if (visited.contains(node.getId())) continue;

            Set<String> component = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(node.getId());

            while (!stack.isEmpty()) {
                String current = stack.pop();
                if (!component.add(current)) continue;
                visited.add(current);

                for (var edge : network.getOutgoingEdges(current)) {
                    if (!component.contains(edge.getToNodeId())) {
                        stack.push(edge.getToNodeId());
                    }
                }
                for (var edge : network.getIncomingEdges(current)) {
                    if (!component.contains(edge.getFromNodeId())) {
                        stack.push(edge.getFromNodeId());
                    }
                }
            }
            components.add(component);
        }

        if (components.size() <= 1) return;

        // Identify largest component
        Set<String> largest = components.stream()
                .max(Comparator.comparingInt(Set::size))
                .orElse(Set.of());

        // Remove every node not in the largest component (edges removed automatically)
        for (var node : allNodes) {
            if (!largest.contains(node.getId())) {
                network.removeNode(node.getId());
            }
        }
    }

    /* ──────────────────────────────────────────────────── */
    /*  Edge-of-map detection for entry/exit spawning        */
    /* ──────────────────────────────────────────────────── */

    private List<String> findEdgeNodes(RoadNetwork network, int mapWidth, int mapHeight) {
        List<String> edgeNodes = new ArrayList<>();
        double mapWorldW = mapWidth * tileSize;
        double mapWorldH = mapHeight * tileSize;
        double threshold = GRID_SPACING * tileSize * 1.5;

        for (var node : network.getAllNodes()) {
            Vec2 pos = node.getPosition();
            if (pos.x() < threshold || pos.x() > mapWorldW - threshold
                    || pos.y() < threshold || pos.y() > mapWorldH - threshold) {
                edgeNodes.add(node.getId());
            }
        }
        return edgeNodes;
    }
}
