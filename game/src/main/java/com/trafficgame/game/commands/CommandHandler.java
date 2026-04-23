package com.trafficgame.game.commands;

import com.trafficgame.game.TrafficGame;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.*;
import com.trafficgame.engine.graph.Edge;
import com.trafficgame.engine.graph.Node;
import com.trafficgame.engine.util.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles player commands with validation and application to game state.
 */
public final class CommandHandler {

    private final TrafficGame game;

    public CommandHandler(TrafficGame game) {
        this.game = game;
    }

    public CommandResult handlePlaceRoad(double x1, double y1, double x2, double y2, String roadTypeStr) {
        RoadSegment.RoadType roadType;
        try {
            roadType = RoadSegment.RoadType.valueOf(roadTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Invalid road type: " + roadTypeStr);
        }

        long cost = roadCost(roadType);
        if (!game.getPlayerProfile().spendRoadPoints(cost)) {
            return CommandResult.error("Not enough Road Points (need " + cost + ")");
        }

        // Create intersection nodes if they don't exist
        String fromId = "player_" + (int) x1 + "_" + (int) y1;
        String toId = "player_" + (int) x2 + "_" + (int) y2;

        if (game.getRoadNetwork().getNode(fromId) == null) {
            game.getRoadNetwork().addNode(fromId, new Vec2(x1, y1),
                    new Intersection(Intersection.IntersectionType.UNCONTROLLED));
        }
        if (game.getRoadNetwork().getNode(toId) == null) {
            game.getRoadNetwork().addNode(toId, new Vec2(x2, y2),
                    new Intersection(Intersection.IntersectionType.UNCONTROLLED));
        }

        double distance = new Vec2(x1, y1).distanceTo(new Vec2(x2, y2));
        int lanes = (roadType == RoadSegment.RoadType.ARTERIAL || roadType == RoadSegment.RoadType.HIGHWAY) ? 2 : 1;
        double speedLimit = switch (roadType) {
            case PATH -> 20;
            case LOCAL -> 40;
            case COLLECTOR -> 50;
            case ARTERIAL -> 60;
            case HIGHWAY -> 100;
        };

        String edgeId = fromId + "->" + toId;
        String reverseId = toId + "->" + fromId;
        game.getRoadNetwork().addEdge(edgeId, fromId, toId, distance,
                new RoadSegment(lanes, speedLimit, roadType));
        game.getRoadNetwork().addEdge(reverseId, toId, fromId, distance,
                new RoadSegment(lanes, speedLimit, roadType));

        return CommandResult.success("Road placed");
    }

    public CommandResult handlePlaceSignal(String intersectionId, String signalTypeStr) {
        // If intersectionId is a coordinate pair like "player_X_Y", find nearest intersection
        Node<Intersection> node = game.getRoadNetwork().getNode(intersectionId);
        
        // If not found, try to parse as coordinates
        if (node == null && intersectionId.startsWith("player_")) {
            String[] parts = intersectionId.split("_");
            if (parts.length >= 3) {
                try {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    
                    // Find nearest intersection — large threshold since click can be anywhere on map
                    double threshold = 120.0;
                    double nearestDist = threshold;
                    for (Node<Intersection> n : game.getRoadNetwork().getAllNodes()) {
                        double dist = n.getPosition().distanceTo(new com.trafficgame.engine.util.Vec2(x, y));
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            node = n;
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        
        if (node == null) return CommandResult.error("Intersection not found");

        // Reject if intersection is in a locked district
        if (!isDistrictUnlocked(node.getData().getDistrictNumber())) {
            return CommandResult.error("District is locked — unlock it first");
        }

        Intersection.IntersectionType signalType;
        try {
            signalType = Intersection.IntersectionType.valueOf(signalTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Invalid signal type: " + signalTypeStr);
        }

        long cost = signalCost(signalType);
        if (!game.getPlayerProfile().spendRoadPoints(cost)) {
            return CommandResult.error("Not enough budget (need $" + formatUSD(cost) + ")");
        }

        node.getData().setType(signalType);
        return CommandResult.success("Intersection upgraded");
    }

    public CommandResult handleDemolish(String targetId) {
        // Try as edge first
        Edge<RoadSegment> edge = game.getRoadNetwork().getEdge(targetId);
        if (edge != null) {
            game.getRoadNetwork().removeEdge(targetId);
            game.getPlayerProfile().addRoadPoints(20); // partial refund
            return CommandResult.success("Road removed");
        }
        return CommandResult.error("Target not found");
    }

    public CommandResult handleUpgradeRoad(double x, double y, String roadTypeStr) {
        RoadSegment.RoadType roadType;
        try {
            roadType = RoadSegment.RoadType.valueOf(roadTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CommandResult.error("Invalid road type: " + roadTypeStr);
        }

        // Find nearest intersection — large radius so the player can click anywhere on a road
        double threshold = 120.0; // world units (~7.5 tiles at TILE_SIZE=16)
        Node<Intersection> nearest = null;
        double nearestDist = threshold;
        for (Node<Intersection> node : game.getRoadNetwork().getAllNodes()) {
            double dist = node.getPosition().distanceTo(new com.trafficgame.engine.util.Vec2(x, y));
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = node;
            }
        }

        if (nearest == null) return CommandResult.error("No road near click point");

        // Reject if intersection is in a locked district
        if (!isDistrictUnlocked(nearest.getData().getDistrictNumber())) {
            return CommandResult.error("District is locked — unlock it first");
        }

        // Upgrade ALL edges touching this intersection (both outgoing and incoming)
        // so both directions of every connected road get updated. This fixes the bug
        // where only one direction was upgraded and the renderer showed the stale one.
        List<Edge<RoadSegment>> allEdges = new ArrayList<>();
        allEdges.addAll(game.getRoadNetwork().getOutgoingEdges(nearest.getId()));
        allEdges.addAll(game.getRoadNetwork().getIncomingEdges(nearest.getId()));

        if (allEdges.isEmpty()) return CommandResult.error("No roads to upgrade");

        // Charge once per unique road pair (not per direction)
        Set<String> paidPairs = new java.util.HashSet<>();
        for (Edge<RoadSegment> edge : allEdges) {
            String pairKey = edge.getFromNodeId().compareTo(edge.getToNodeId()) < 0
                    ? edge.getFromNodeId() + "|" + edge.getToNodeId()
                    : edge.getToNodeId() + "|" + edge.getFromNodeId();
            if (paidPairs.contains(pairKey)) {
                // Already paid for this pair — just upgrade the reverse direction free
                edge.getData().setType(roadType);
                edge.getData().setLanes(lanesForType(roadType));
                edge.getData().setSpeedLimit(speedForType(roadType));
                continue;
            }
            paidPairs.add(pairKey);
            long cost = roadCost(roadType);
            if (!game.getPlayerProfile().spendRoadPoints(cost)) {
                return CommandResult.error("Not enough budget (need $" + formatUSD(cost) + ")");
            }
            edge.getData().setType(roadType);
            edge.getData().setLanes(lanesForType(roadType));
            edge.getData().setSpeedLimit(speedForType(roadType));
        }

        return CommandResult.success("Road upgraded to " + roadTypeStr);
    }

    private boolean isDistrictUnlocked(int districtNumber) {
        if (districtNumber <= 0) return true; // tunnel/unknown nodes always allowed
        for (District d : game.getDistricts()) {
            if (d.getNumber() == districtNumber) return d.isUnlocked();
        }
        return false; // unknown district → locked by default
    }

    private int lanesForType(RoadSegment.RoadType type) {
        return switch (type) {
            case PATH -> 1;
            case LOCAL -> 1;
            case COLLECTOR -> 2;
            case ARTERIAL -> 2;
            case HIGHWAY -> 3;
        };
    }

    private double speedForType(RoadSegment.RoadType type) {
        return switch (type) {
            case PATH -> 20;
            case LOCAL -> 40;
            case COLLECTOR -> 50;
            case ARTERIAL -> 70;
            case HIGHWAY -> 110;
        };
    }

    public CommandResult handleUnlockDistrict(String districtId) {
        if (districtId == null) return CommandResult.error("No district specified");
        for (District d : game.getDistricts()) {
            if (d.getId().equals(districtId)) {
                if (d.isUnlocked()) return CommandResult.error("Already unlocked");
                // Check vehicle requirement
                if (game.getPlayerProfile().getVehiclesDelivered() < d.getVehiclesRequired()) {
                    return CommandResult.error("Need " + d.getVehiclesRequired() + " vehicles delivered");
                }
                // Check rating requirement
                if (d.getRatingRequired() != null) {
                    if (!meetsRating(game.getPlayerProfile().getRatingGrade(), d.getRatingRequired())) {
                        return CommandResult.error("Need rating " + d.getRatingRequired());
                    }
                }
                d.setUnlocked(true);
                game.getSpawnerSystem().refreshNodeCache();
                return CommandResult.success("District unlocked: " + d.getName());
            }
        }
        return CommandResult.error("District not found");
    }

    private boolean meetsRating(String current, String required) {
        String order = "FABCDS";
        return order.indexOf(current) >= order.indexOf(required);
    }

    private long roadCost(RoadSegment.RoadType type) {
        return switch (type) {
            case PATH -> GameConfig.COST_ROAD_LOCAL; // Local as minimum
            case LOCAL -> GameConfig.COST_ROAD_LOCAL;
            case COLLECTOR -> GameConfig.COST_ROAD_COLLECTOR;
            case ARTERIAL -> GameConfig.COST_ROAD_ARTERIAL;
            case HIGHWAY -> GameConfig.COST_ROAD_HIGHWAY;
        };
    }

    private long signalCost(Intersection.IntersectionType type) {
        return switch (type) {
            case STOP -> GameConfig.COST_SIGNAL_STOP;
            case YIELD -> GameConfig.COST_SIGNAL_YIELD;
            case SIGNAL -> GameConfig.COST_SIGNAL_SIGNAL;
            case ROUNDABOUT -> GameConfig.COST_SIGNAL_ROUNDABOUT;
            default -> 0; // UNCONTROLLED costs nothing
        };
    }

    private String formatUSD(long cents) {
        long dollars = cents / 100;
        if (dollars >= 1_000_000) return String.format("%.1fM", dollars / 1_000_000.0);
        if (dollars >= 1_000) return String.format("%.0fK", dollars / 1_000.0);
        return String.format("%.0f", (double) dollars);
    }

    public record CommandResult(boolean success, String message) {
        public static CommandResult success(String msg) { return new CommandResult(true, msg); }
        public static CommandResult error(String msg) { return new CommandResult(false, msg); }
    }
}
