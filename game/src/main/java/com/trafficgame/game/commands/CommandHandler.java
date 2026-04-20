package com.trafficgame.game.commands;

import com.trafficgame.game.TrafficGame;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.*;
import com.trafficgame.engine.graph.Edge;
import com.trafficgame.engine.graph.Node;
import com.trafficgame.engine.util.Vec2;

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

    public CommandResult handlePlaceSignal(String intersectionId) {
        Node<Intersection> node = game.getRoadNetwork().getNode(intersectionId);
        if (node == null) return CommandResult.error("Intersection not found");

        if (!game.getPlayerProfile().spendRoadPoints(GameConfig.COST_SIGNAL)) {
            return CommandResult.error("Not enough Road Points");
        }

        node.getData().setType(Intersection.IntersectionType.SIGNAL);
        return CommandResult.success("Signal placed");
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

    private long roadCost(RoadSegment.RoadType type) {
        return switch (type) {
            case PATH -> GameConfig.COST_ROAD_PATH;
            case LOCAL -> GameConfig.COST_ROAD_LOCAL;
            case COLLECTOR -> GameConfig.COST_ROAD_COLLECTOR;
            case ARTERIAL -> GameConfig.COST_ROAD_ARTERIAL;
            case HIGHWAY -> GameConfig.COST_ROAD_HIGHWAY;
        };
    }

    public record CommandResult(boolean success, String message) {
        public static CommandResult success(String msg) { return new CommandResult(true, msg); }
        public static CommandResult error(String msg) { return new CommandResult(false, msg); }
    }
}
