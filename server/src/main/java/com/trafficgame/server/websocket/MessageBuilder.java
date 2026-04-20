package com.trafficgame.server.websocket;

import com.trafficgame.engine.entity.Entity;
import com.trafficgame.engine.graph.Edge;
import com.trafficgame.engine.graph.Node;
import com.trafficgame.engine.util.Vec2;
import com.trafficgame.game.TrafficGame;
import com.trafficgame.game.model.*;
import com.trafficgame.game.systems.WeatherSystem;
import com.trafficgame.shared.protocol.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds protocol messages from game state for client transmission.
 */
public final class MessageBuilder {

    private MessageBuilder() {}

    public static TickDelta buildTickDelta(TrafficGame game) {
        TickDelta delta = new TickDelta(game.getTickNumber());

        // Vehicle updates
        List<TickDelta.VehicleUpdate> vehicleUpdates = new ArrayList<>();
        List<Long> removedIds = new ArrayList<>();

        for (Entity entity : game.getEntityManager().withComponent(VehicleMovement.class)) {
            VehicleMovement movement = entity.get(VehicleMovement.class);
            VehicleInfo info = entity.get(VehicleInfo.class);
            if (movement == null || info == null) continue;

            Edge<RoadSegment> edge = game.getRoadNetwork().getEdge(movement.getCurrentEdgeId());
            if (edge == null) continue;

            // Interpolate world position along edge
            Node<Intersection> fromNode = game.getRoadNetwork().getNode(edge.getFromNodeId());
            Node<Intersection> toNode = game.getRoadNetwork().getNode(edge.getToNodeId());
            if (fromNode == null || toNode == null) continue;

            Vec2 pos = fromNode.getPosition().lerp(toNode.getPosition(), movement.getPositionOnEdge());
            Vec2 dir = toNode.getPosition().subtract(fromNode.getPosition());
            double angle = Math.atan2(dir.x(), dir.y()); // Three.js rotation.y = atan2(dx, dz)

            // Apply lane offset perpendicular to road direction
            double laneOff = movement.getLaneOffset();
            if (laneOff != 0 && dir.length() > 0.001) {
                double nx = -dir.y() / dir.length();
                double ny = dir.x() / dir.length();
                pos = new Vec2(pos.x() + nx * laneOff, pos.y() + ny * laneOff);
            }

            // Compute elevation for bridge edges (ramp up at start/end, elevated in middle)
            double elevation = 0.0;
            if (edge.getData().isBridge()) {
                double t = movement.getPositionOnEdge();
                double bridgeHeight = 8.0;
                double rampFraction = 0.2; // 20% of edge is ramp at each end
                if (t < rampFraction) {
                    elevation = bridgeHeight * (t / rampFraction);
                } else if (t > 1.0 - rampFraction) {
                    elevation = bridgeHeight * ((1.0 - t) / rampFraction);
                } else {
                    elevation = bridgeHeight;
                }
            }

            vehicleUpdates.add(new TickDelta.VehicleUpdate(
                    entity.getId(), pos.x(), pos.y(), movement.getSpeed(), angle,
                    info.getType().name(), movement.getLaneIndex(), elevation));
        }
        delta.setVehicleUpdates(vehicleUpdates);
        delta.setRemovedVehicleIds(removedIds);

        // Signal updates
        List<TickDelta.SignalUpdate> signalUpdates = new ArrayList<>();
        for (Node<Intersection> node : game.getRoadNetwork().getAllNodes()) {
            Intersection inter = node.getData();
            if (inter.getType() == Intersection.IntersectionType.SIGNAL) {
                signalUpdates.add(new TickDelta.SignalUpdate(
                        node.getId(), inter.getSignalState().name(), inter.getSignalTimer()));
            }
        }
        delta.setSignalUpdates(signalUpdates);

        // Rating
        PlayerProfile profile = game.getPlayerProfile();
        delta.setRatingUpdate(new TickDelta.RatingUpdate(
                profile.getRatingScore(), profile.getRatingGrade(),
                game.getRatingSystem().getBreakdown()));

        // Weather
        WeatherSystem ws = game.getWeatherSystem();
        delta.setWeatherUpdate(new TickDelta.WeatherUpdate(
                ws.getCurrentWeather().name(), ws.getCurrentSeason().name(), 1.0));

        // Events
        List<TickDelta.EventUpdate> eventUpdates = game.getEventSystem().getActiveEvents().stream()
                .map(e -> new TickDelta.EventUpdate(
                        e.getId(), e.getType().name(), e.getPhase().name(), e.getActiveTimeRemaining()))
                .collect(Collectors.toList());
        delta.setEventUpdates(eventUpdates);

        return delta;
    }

    public static GameStateSnapshot buildSnapshot(TrafficGame game) {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        snapshot.setTickNumber(game.getTickNumber());
        snapshot.setMapWidth(game.getTerrain().getWidth());
        snapshot.setMapHeight(game.getTerrain().getHeight());

        // Terrain as type array
        int w = game.getTerrain().getWidth();
        int h = game.getTerrain().getHeight();
        int[][] terrain = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                terrain[x][y] = game.getTerrain().get(x, y).getType();
            }
        }
        snapshot.setTerrain(terrain);

        // Elevation (world-space Y per cell)
        double[][] elevData = new double[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                elevData[x][y] = game.getTerrain().get(x, y).getElevation();
            }
        }
        snapshot.setElevation(elevData);

        // Roads
        List<GameStateSnapshot.RoadSegmentData> roads = game.getRoadNetwork().getAllEdges().stream()
                .map(e -> new GameStateSnapshot.RoadSegmentData(
                        e.getId(), e.getFromNodeId(), e.getToNodeId(),
                        e.getData().getLanes(), e.getData().getSpeedLimit(),
                        e.getData().getRoadType().name(), e.getData().getCondition(),
                        e.getData().getCongestion()))
                .collect(Collectors.toList());
        snapshot.setRoads(roads);

        // Intersections
        List<GameStateSnapshot.IntersectionData> intersections = game.getRoadNetwork().getAllNodes().stream()
                .map(n -> new GameStateSnapshot.IntersectionData(
                        n.getId(), n.getPosition().x(), n.getPosition().y(),
                        n.getData().getType().name(),
                        n.getData().getSignalState().name()))
                .collect(Collectors.toList());
        snapshot.setIntersections(intersections);

        // Buildings
        List<GameStateSnapshot.BuildingSnapshotData> buildingData = game.getBuildings().stream()
                .map(b -> new GameStateSnapshot.BuildingSnapshotData(
                        b.getId(), b.getX(), b.getZ(), b.getWidth(), b.getDepth(),
                        b.getHeight(), b.getStyle(), b.getDistrictNumber(), b.getColorIndex()))
                .collect(Collectors.toList());
        snapshot.setBuildings(buildingData);

        // Districts
        List<GameStateSnapshot.DistrictData> districts = game.getDistricts().stream()
                .map(d -> new GameStateSnapshot.DistrictData(
                        d.getId(), d.getName(), d.isUnlocked(), d.getTier(), 0))
                .collect(Collectors.toList());
        snapshot.setDistricts(districts);

        // Player
        PlayerProfile p = game.getPlayerProfile();
        snapshot.setPlayer(new GameStateSnapshot.PlayerData(
                p.getRoadPoints(), p.getBlueprintTokens(), p.getRatingGrade(),
                p.getRatingScore(), p.getCityTier(), p.getVehiclesDelivered()));

        // Weather
        snapshot.setCurrentSeason(game.getWeatherSystem().getCurrentSeason().name());
        snapshot.setCurrentWeather(game.getWeatherSystem().getCurrentWeather().name());

        return snapshot;
    }
}
