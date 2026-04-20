package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.engine.entity.Entity;
import com.trafficgame.engine.entity.EntityManager;
import com.trafficgame.engine.graph.Edge;
import com.trafficgame.engine.graph.Node;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.*;

import java.util.*;

/**
 * Lane-aware Intelligent Driver Model (IDM) for vehicle movement.
 * Vehicles follow lanes, respect traffic signals, and avoid collisions with
 * vehicles in the same lane.
 */
public final class VehicleMovementSystem implements GameSystem {

    private static final double LANE_WIDTH = 3.7; // world units per lane (matches visual lane width)

    private final EntityManager entityManager;
    private final RoadNetwork roadNetwork;

    public VehicleMovementSystem(EntityManager entityManager, RoadNetwork roadNetwork) {
        this.entityManager = entityManager;
        this.roadNetwork = roadNetwork;
    }

    @Override
    public void update(GameState state, double dt) {
        // Build a map of vehicles per edge+lane for gap calculation
        Map<String, List<Entity>> vehiclesByEdge = new HashMap<>();
        for (Entity entity : entityManager.withComponent(VehicleMovement.class)) {
            VehicleMovement m = entity.get(VehicleMovement.class);
            if (m == null) continue;
            vehiclesByEdge.computeIfAbsent(m.getCurrentEdgeId(), k -> new ArrayList<>()).add(entity);
        }
        // Sort vehicles on each edge by position (furthest ahead first)
        for (List<Entity> list : vehiclesByEdge.values()) {
            list.sort((a, b) -> Double.compare(
                    b.get(VehicleMovement.class).getPositionOnEdge(),
                    a.get(VehicleMovement.class).getPositionOnEdge()));
        }

        for (Entity entity : entityManager.withComponent(VehicleMovement.class)) {
            VehicleMovement movement = entity.get(VehicleMovement.class);
            VehicleInfo info = entity.get(VehicleInfo.class);
            VehicleRoute route = entity.get(VehicleRoute.class);

            if (movement == null || info == null || route == null) continue;

            Edge<RoadSegment> edge = roadNetwork.getEdge(movement.getCurrentEdgeId());
            if (edge == null) continue;

            RoadSegment segment = edge.getData();
            double effectiveSpeed = segment.getEffectiveSpeed();
            double maxSpeed = Math.min(info.getConfig().maxSpeed(), effectiveSpeed);

            // ── Signal compliance ──
            // Check if the next intersection (edge destination) has a red/yellow signal
            if (movement.getPositionOnEdge() > 0.85) {
                Node<Intersection> toNode = roadNetwork.getNode(edge.getToNodeId());
                if (toNode != null) {
                    Intersection.IntersectionType iType = toNode.getData().getType();

                    if (iType == Intersection.IntersectionType.SIGNAL) {
                        Intersection.SignalState sig = toNode.getData().getSignalState();
                        // Stop on any red or yellow phase
                        boolean shouldStop = sig.name().startsWith("RED") || sig.name().startsWith("YELLOW");
                        if (shouldStop) {
                            double distToStop = (1.0 - movement.getPositionOnEdge()) * edge.getWeight();
                            double decelNeeded = (movement.getSpeed() * movement.getSpeed()) / (2 * Math.max(distToStop, 0.5));
                            double newSpeed2 = Math.max(0, movement.getSpeed() - decelNeeded * dt);
                            movement.setSpeed(newSpeed2);
                            movement.setTargetSpeed(0);
                            computeLaneOffset(movement, segment);
                            if (movement.getSpeed() < 0.5) info.addWaitTime(dt);
                            continue;
                        }
                    } else if (iType == Intersection.IntersectionType.STOP) {
                        // Full stop before entering intersection, then creep through
                        double distToStop = (1.0 - movement.getPositionOnEdge()) * edge.getWeight();
                        if (distToStop < 8.0 && movement.getSpeed() > 1.0) {
                            double decelNeeded = (movement.getSpeed() * movement.getSpeed()) / (2 * Math.max(distToStop, 0.5));
                            double newSpeed2 = Math.max(0, movement.getSpeed() - decelNeeded * dt);
                            movement.setSpeed(newSpeed2);
                            movement.setTargetSpeed(maxSpeed * 0.15);
                            computeLaneOffset(movement, segment);
                            if (movement.getSpeed() < 0.5) info.addWaitTime(dt);
                            continue;
                        }
                    } else if (iType == Intersection.IntersectionType.YIELD) {
                        // Slow down to yield speed (30% of max) approaching intersection
                        double distToInter = (1.0 - movement.getPositionOnEdge()) * edge.getWeight();
                        if (distToInter < 12.0) {
                            double yieldSpeed = maxSpeed * 0.3;
                            if (movement.getSpeed() > yieldSpeed) {
                                double decelNeeded = (movement.getSpeed() - yieldSpeed) / Math.max(dt, 0.01);
                                double newSpeed2 = Math.max(yieldSpeed, movement.getSpeed() - decelNeeded * dt * 0.5);
                                movement.setSpeed(newSpeed2);
                                movement.setTargetSpeed(yieldSpeed);
                            }
                        }
                    }

                    // Cross-edge collision avoidance at intersections:
                    // If another vehicle on a different incoming edge is also close to this
                    // intersection, slow down to avoid collision in the intersection area.
                    if (iType == Intersection.IntersectionType.UNCONTROLLED ||
                        iType == Intersection.IntersectionType.YIELD) {
                        boolean crossingConflict = false;
                        List<Edge<RoadSegment>> incomingEdges = roadNetwork.getIncomingEdges(edge.getToNodeId());
                        for (Edge<RoadSegment> otherEdge : incomingEdges) {
                            if (otherEdge.getId().equals(edge.getId())) continue;
                            List<Entity> otherVehicles = vehiclesByEdge.get(otherEdge.getId());
                            if (otherVehicles == null) continue;
                            for (Entity otherEntity : otherVehicles) {
                                VehicleMovement om = otherEntity.get(VehicleMovement.class);
                                if (om.getPositionOnEdge() > 0.80 && om.getSpeed() > 2.0) {
                                    crossingConflict = true;
                                    break;
                                }
                            }
                            if (crossingConflict) break;
                        }
                        if (crossingConflict) {
                            double distToInter = (1.0 - movement.getPositionOnEdge()) * edge.getWeight();
                            if (distToInter < 15.0) {
                                double yieldSpeed = maxSpeed * 0.2;
                                if (movement.getSpeed() > yieldSpeed) {
                                    movement.setSpeed(Math.max(yieldSpeed, movement.getSpeed() - info.getConfig().acceleration() * dt * 2));
                                }
                            }
                        }
                    }
                }
            }

            // ── Gap to leading vehicle (same edge, same lane) ──
            double gap = Double.MAX_VALUE;
            List<Entity> edgeVehicles = vehiclesByEdge.get(movement.getCurrentEdgeId());
            if (edgeVehicles != null) {
                for (Entity other : edgeVehicles) {
                    if (other.getId() == entity.getId()) continue;
                    VehicleMovement om = other.get(VehicleMovement.class);
                    if (om.getLaneIndex() != movement.getLaneIndex()) continue;
                    if (om.getPositionOnEdge() > movement.getPositionOnEdge()) {
                        double g = (om.getPositionOnEdge() - movement.getPositionOnEdge()) * edge.getWeight();
                        gap = Math.min(gap, g);
                    }
                }
            }

            // IDM acceleration
            double accel = info.getConfig().acceleration();
            double sstar = GameConfig.IDM_MIN_GAP + movement.getSpeed() * GameConfig.IDM_DESIRED_TIME_HEADWAY;
            double desiredAccel = accel * (1.0 - Math.pow(movement.getSpeed() / Math.max(maxSpeed, 0.1),
                    GameConfig.IDM_ACCELERATION_EXPONENT));
            if (gap < Double.MAX_VALUE) {
                double interactionTerm = -accel * (sstar * sstar) / (gap * gap);
                desiredAccel += interactionTerm;
            }

            double newSpeed = Math.max(0, movement.getSpeed() + desiredAccel * dt);
            movement.setSpeed(newSpeed);
            movement.setTargetSpeed(maxSpeed);

            // Advance position on edge
            double edgeLength = edge.getWeight();
            if (edgeLength > 0) {
                double posAdvance = (newSpeed * dt) / edgeLength;
                double newPos = movement.getPositionOnEdge() + posAdvance;

                if (newPos >= 1.0) {
                    String nextEdgeId = route.advanceToNextEdge();
                    if (nextEdgeId != null) {
                        movement.setCurrentEdgeId(nextEdgeId);
                        movement.setPositionOnEdge(newPos - 1.0);
                        // Reassign lane on new edge
                        Edge<RoadSegment> nextEdge = roadNetwork.getEdge(nextEdgeId);
                        if (nextEdge != null) {
                            int lanes = nextEdge.getData().getLanes();
                            movement.setLaneIndex(Math.min(movement.getLaneIndex(), lanes - 1));
                        }
                    } else {
                        movement.setPositionOnEdge(1.0);
                        movement.setSpeed(0);
                    }
                } else {
                    movement.setPositionOnEdge(newPos);
                }
            }

            // Compute lane offset from road centre
            computeLaneOffset(movement, segment);

            // Track wait time
            if (movement.getSpeed() < 0.5) {
                info.addWaitTime(dt);
            }
        }
    }

    /**
     * Compute the lateral offset from road centre for the vehicle's current lane.
     * Right-hand traffic: all lanes are offset to the right (negative perp direction).
     * Lane 0 is outermost right, higher indices closer to centre.
     */
    private void computeLaneOffset(VehicleMovement movement, RoadSegment segment) {
        int lanes = segment.getLanes();
        // All forward-direction lanes sit on the right side of road centre.
        // Offset = -(laneIndex + 0.5) * LANE_WIDTH
        // The reverse edge has a flipped perpendicular, so these automatically
        // end up on the correct side.
        double offset = -(movement.getLaneIndex() + 0.5) * LANE_WIDTH;
        movement.setLaneOffset(offset);
    }

    @Override
    public String getName() { return "VehicleMovement"; }
}
