package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.engine.entity.Entity;
import com.trafficgame.engine.entity.EntityManager;
import com.trafficgame.engine.graph.Edge;
import com.trafficgame.engine.graph.Node;
import com.trafficgame.engine.graph.PathFinder;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.*;

import java.util.*;

/**
 * Spawns vehicles at entry points and despawns them at exits (route completion).
 */
public final class VehicleSpawnerSystem implements GameSystem {

    private final EntityManager entityManager;
    private final RoadNetwork roadNetwork;
    private final Random random;
    private double spawnTimer;
    private List<String> entryNodeIds;
    private List<String> exitNodeIds;
    private List<String> interiorNodeIds;

    public VehicleSpawnerSystem(EntityManager entityManager, RoadNetwork roadNetwork, int seed) {
        this.entityManager = entityManager;
        this.roadNetwork = roadNetwork;
        this.random = new Random(seed);
        this.spawnTimer = 0;
        this.entryNodeIds = new ArrayList<>();
        this.exitNodeIds = new ArrayList<>();
        this.interiorNodeIds = new ArrayList<>();
        cacheEntryExitNodes();
    }

    private void cacheEntryExitNodes() {
        // Tunnel nodes are the designated entry/exit points
        for (Node<Intersection> node : roadNetwork.getAllNodes()) {
            if (node.getData().getType() == Intersection.IntersectionType.TUNNEL) {
                entryNodeIds.add(node.getId());
                exitNodeIds.add(node.getId());
            }
        }
        // Collect interior nodes (signals and well-connected intersections) as destinations
        for (Node<Intersection> node : roadNetwork.getAllNodes()) {
            Intersection.IntersectionType type = node.getData().getType();
            if (type == Intersection.IntersectionType.SIGNAL ||
                type == Intersection.IntersectionType.STOP ||
                type == Intersection.IntersectionType.YIELD) {
                interiorNodeIds.add(node.getId());
            }
        }
        // Also add well-connected UNCONTROLLED nodes as additional spawn/exit points
        // to ensure vehicles appear across the whole map, not just tunnels
        for (Node<Intersection> node : roadNetwork.getAllNodes()) {
            Intersection.IntersectionType type = node.getData().getType();
            if (type == Intersection.IntersectionType.UNCONTROLLED) {
                int connections = roadNetwork.getOutgoingEdges(node.getId()).size()
                        + roadNetwork.getIncomingEdges(node.getId()).size();
                if (connections >= 6) { // well-connected junction
                    entryNodeIds.add(node.getId());
                    exitNodeIds.add(node.getId());
                }
            }
        }
        // Fallback: if no tunnel nodes, use edge nodes (limited connections)
        if (entryNodeIds.isEmpty()) {
            for (Node<Intersection> node : roadNetwork.getAllNodes()) {
                List<Edge<RoadSegment>> outgoing = roadNetwork.getOutgoingEdges(node.getId());
                List<Edge<RoadSegment>> incoming = roadNetwork.getIncomingEdges(node.getId());
                int totalConnections = outgoing.size() + incoming.size();
                if (totalConnections <= 2) {
                    entryNodeIds.add(node.getId());
                    exitNodeIds.add(node.getId());
                }
            }
        }
        // Last resort fallback
        if (entryNodeIds.isEmpty()) {
            var all = new java.util.ArrayList<>(roadNetwork.getAllNodes());
            if (!all.isEmpty()) {
                entryNodeIds.add(all.get(0).getId());
                exitNodeIds.add(all.get(all.size() - 1).getId());
            }
        }
    }

    @Override
    public void update(GameState state, double dt) {
        // Vehicles that completed their route get a NEW random route (continuous roaming)
        // Only despawn with 20% probability when reaching a tunnel node (natural exit)
        List<Entity> toReroute = new ArrayList<>();
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : entityManager.withComponent(VehicleRoute.class)) {
            VehicleRoute route = entity.get(VehicleRoute.class);
            VehicleMovement movement = entity.get(VehicleMovement.class);
            if (route != null && route.isComplete() && movement != null && movement.getPositionOnEdge() >= 0.99) {
                // Check if the vehicle is at a tunnel node — 20% chance to exit
                Edge<RoadSegment> edge = roadNetwork.getEdge(movement.getCurrentEdgeId());
                if (edge != null) {
                    Node<Intersection> endNode = roadNetwork.getNode(edge.getToNodeId());
                    if (endNode != null && endNode.getData().getType() == Intersection.IntersectionType.TUNNEL
                            && random.nextDouble() < 0.20) {
                        toRemove.add(entity);
                        continue;
                    }
                }
                toReroute.add(entity);
            }
        }
        for (Entity entity : toRemove) {
            entityManager.remove(entity.getId());
        }
        for (Entity entity : toReroute) {
            rerouteVehicle(entity);
        }

        // Spawn new vehicles (try multiple per tick to fill up faster)
        if (entityManager.count() >= GameConfig.MAX_VEHICLES) return;
        if (entryNodeIds.isEmpty() || exitNodeIds.isEmpty()) return;

        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            int spawnsPerTick = Math.min(3, GameConfig.MAX_VEHICLES - (int) entityManager.count());
            for (int i = 0; i < spawnsPerTick; i++) {
                spawnVehicle();
            }
            spawnTimer = GameConfig.VEHICLE_SPAWN_INTERVAL * (0.5 + random.nextDouble());
        }
    }

    /**
     * Give a vehicle that completed its route a new random destination.
     */
    private void rerouteVehicle(Entity entity) {
        VehicleMovement movement = entity.get(VehicleMovement.class);
        if (movement == null) return;

        Edge<RoadSegment> edge = roadNetwork.getEdge(movement.getCurrentEdgeId());
        if (edge == null) { entityManager.remove(entity.getId()); return; }

        String currentNodeId = edge.getToNodeId();

        // Pick a new destination — 70% interior, 30% tunnel exit
        String destination;
        if (!interiorNodeIds.isEmpty() && random.nextDouble() < 0.7) {
            destination = pickFarNode(currentNodeId, interiorNodeIds);
        } else if (!exitNodeIds.isEmpty()) {
            destination = pickFarNode(currentNodeId, exitNodeIds);
        } else {
            entityManager.remove(entity.getId());
            return;
        }

        if (destination == null || destination.equals(currentNodeId)) {
            // Can't route — just pick any interior node
            if (!interiorNodeIds.isEmpty()) {
                destination = interiorNodeIds.get(random.nextInt(interiorNodeIds.size()));
            } else {
                entityManager.remove(entity.getId());
                return;
            }
        }

        List<String> path = PathFinder.findPath(roadNetwork, currentNodeId, destination,
                (e, graph) -> {
                    RoadSegment seg = e.getData();
                    if (seg.isBlocked()) return -1.0;
                    return e.getWeight() / seg.getEffectiveSpeed();
                });

        if (path.isEmpty()) {
            entityManager.remove(entity.getId());
            return;
        }

        // Assign new route
        VehicleRoute route = entity.get(VehicleRoute.class);
        route.setEdgeIds(path);
        movement.setCurrentEdgeId(path.get(0));
        movement.setPositionOnEdge(0.0);
        Edge<RoadSegment> newEdge = roadNetwork.getEdge(path.get(0));
        if (newEdge != null) {
            int lanes = newEdge.getData().getLanes();
            movement.setLaneIndex(Math.min(movement.getLaneIndex(), lanes - 1));
        }
    }

    private void spawnVehicle() {
        // Always spawn at tunnel entry (vehicles enter from outside)
        String origin = entryNodeIds.get(random.nextInt(entryNodeIds.size()));

        // Destination: 60% interior nodes (drive through city), 40% far tunnel exit
        String destination;
        if (!interiorNodeIds.isEmpty() && random.nextDouble() < 0.6) {
            destination = pickFarNode(origin, interiorNodeIds);
        } else {
            destination = pickFarNode(origin, exitNodeIds);
        }

        if (destination == null || destination.equals(origin)) return;

        // Find route
        List<String> path = PathFinder.findPath(roadNetwork, origin, destination,
                (edge, graph) -> {
                    RoadSegment seg = edge.getData();
                    if (seg.isBlocked()) return -1.0;
                    return edge.getWeight() / seg.getEffectiveSpeed();
                });

        if (path.isEmpty()) return;

        // Choose vehicle type
        VehicleType type = chooseVehicleType();

        // Create entity
        Entity vehicle = entityManager.create();
        vehicle.add(new VehicleInfo(type, origin, destination));

        VehicleMovement movement = new VehicleMovement(path.get(0));
        // Assign a random lane within the first edge
        Edge<RoadSegment> firstEdge = roadNetwork.getEdge(path.get(0));
        if (firstEdge != null) {
            int lanes = firstEdge.getData().getLanes();
            movement.setLaneIndex(random.nextInt(lanes));
        }
        vehicle.add(movement);
        vehicle.add(new VehicleRoute(path));
    }

    /**
     * Pick a destination node that is far from the origin for better map coverage.
     */
    private String pickFarNode(String originId, List<String> candidates) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        Node<Intersection> originNode = roadNetwork.getNode(originId);
        if (originNode == null) return candidates.get(random.nextInt(candidates.size()));

        // Sort by distance descending, pick from top half
        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> {
            double da = roadNetwork.getNode(a).getPosition().distanceTo(originNode.getPosition());
            double db = roadNetwork.getNode(b).getPosition().distanceTo(originNode.getPosition());
            return Double.compare(db, da);
        });

        int range = Math.max(1, sorted.size() / 3);
        return sorted.get(random.nextInt(range));
    }

    private VehicleType chooseVehicleType() {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (VehicleType type : VehicleType.values()) {
            cumulative += VehicleConfig.forType(type).spawnWeight();
            if (roll <= cumulative) return type;
        }
        return VehicleType.CAR;
    }

    public int getActiveVehicleCount() {
        return entityManager.withComponent(VehicleMovement.class).size();
    }

    @Override
    public String getName() { return "VehicleSpawner"; }
}
