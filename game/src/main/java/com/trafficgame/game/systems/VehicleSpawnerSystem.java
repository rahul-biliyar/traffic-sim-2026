package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.engine.entity.Entity;
import com.trafficgame.engine.entity.EntityManager;
import com.trafficgame.engine.graph.Edge;
import com.trafficgame.engine.graph.Node;
import com.trafficgame.engine.graph.PathFinder;
import com.trafficgame.game.TrafficGame;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.*;

import java.util.*;

/**
 * Spawns vehicles at entry points and despawns them at exits (route completion).
 * Vehicles only spawn/travel within UNLOCKED districts.
 */
public final class VehicleSpawnerSystem implements GameSystem {

    private final EntityManager entityManager;
    private final RoadNetwork roadNetwork;
    private final TrafficGame game;
    private final Random random;
    private double spawnTimer;
    private double elapsedTime;
    private static final double RAMP_DURATION = 300.0; // 5 minutes to reach full capacity
    private static final double INITIAL_CAPACITY_FRACTION = 0.05; // start at 5% of max
    private List<String> entryNodeIds;
    private List<String> exitNodeIds;
    private List<String> interiorNodeIds;
    // Track which districts were unlocked last time we cached, to detect changes
    private Set<Integer> cachedUnlockedDistricts = new HashSet<>();

    public VehicleSpawnerSystem(EntityManager entityManager, RoadNetwork roadNetwork, int seed, TrafficGame game) {
        this.entityManager = entityManager;
        this.roadNetwork = roadNetwork;
        this.game = game;
        this.random = new Random(seed);
        this.spawnTimer = 0;
        this.elapsedTime = 0;
        this.entryNodeIds = new ArrayList<>();
        this.exitNodeIds = new ArrayList<>();
        this.interiorNodeIds = new ArrayList<>();
        cacheEntryExitNodes();
    }

    private void cacheEntryExitNodes() {
        entryNodeIds = new ArrayList<>();
        exitNodeIds = new ArrayList<>();
        interiorNodeIds = new ArrayList<>();

        // Record which districts are currently unlocked for change detection
        cachedUnlockedDistricts = new HashSet<>();
        for (District d : game.getDistricts()) {
            if (d.isUnlocked()) cachedUnlockedDistricts.add(d.getNumber());
        }

        // Tunnel nodes are the designated entry/exit points — include all tunnels
        // (tunnels are the "border" nodes and we want vehicles to route through unlocked areas)
        for (Node<Intersection> node : roadNetwork.getAllNodes()) {
            if (node.getData().getType() == Intersection.IntersectionType.TUNNEL) {
                entryNodeIds.add(node.getId());
                exitNodeIds.add(node.getId());
            }
        }
        // Collect interior nodes only from unlocked districts
        for (Node<Intersection> node : roadNetwork.getAllNodes()) {
            if (!isNodeInUnlockedDistrict(node)) continue;
            Intersection.IntersectionType type = node.getData().getType();
            if (type == Intersection.IntersectionType.SIGNAL ||
                type == Intersection.IntersectionType.STOP ||
                type == Intersection.IntersectionType.YIELD) {
                interiorNodeIds.add(node.getId());
            }
        }
        // Also add well-connected UNCONTROLLED nodes in unlocked districts as additional spawn/exit points
        // to ensure vehicles appear across the whole map, not just tunnels
        for (Node<Intersection> node : roadNetwork.getAllNodes()) {
            if (!isNodeInUnlockedDistrict(node)) continue;
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

    /** Call this after a district is unlocked to refresh spawn/route node caches. */
    public void refreshNodeCache() {
        cacheEntryExitNodes();
    }

    /** Check whether a node belongs to an unlocked district (or is a tunnel/border node). */
    private boolean isNodeInUnlockedDistrict(Node<Intersection> node) {
        int dn = node.getData().getDistrictNumber();
        if (dn <= 0) return true; // tunnel or unmapped — always allow
        for (District d : game.getDistricts()) {
            if (d.getNumber() == dn) return d.isUnlocked();
        }
        return false;
    }

    @Override
    public void update(GameState state, double dt) {
        elapsedTime += dt;

        // Refresh node cache when districts are unlocked without an explicit call
        Set<Integer> currentUnlocked = new HashSet<>();
        for (District d : game.getDistricts()) {
            if (d.isUnlocked()) currentUnlocked.add(d.getNumber());
        }
        if (!currentUnlocked.equals(cachedUnlockedDistricts)) {
            cacheEntryExitNodes();
        }

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

        // Spawn new vehicles — capacity ramps up VERY gradually over 5 minutes
        // Start at 5% of max to avoid 300+ spawn in the first minute
        double rampFraction = Math.min(1.0, INITIAL_CAPACITY_FRACTION + (1.0 - INITIAL_CAPACITY_FRACTION) * Math.pow(elapsedTime / RAMP_DURATION, 2.0));
        int currentMaxVehicles = (int) (GameConfig.MAX_VEHICLES * rampFraction);
        if (entityManager.count() >= currentMaxVehicles) return;
        if (entryNodeIds.isEmpty() || exitNodeIds.isEmpty()) return;

        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            // Only spawn 1 vehicle per interval — prevents burst spawning
            if (entityManager.count() < currentMaxVehicles) {
                spawnVehicle();
            }
            spawnTimer = GameConfig.VEHICLE_SPAWN_INTERVAL * (0.8 + random.nextDouble() * 0.4);
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
            movement.setLaneIndex(random.nextInt(lanes)); // randomize lane for distribution
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
