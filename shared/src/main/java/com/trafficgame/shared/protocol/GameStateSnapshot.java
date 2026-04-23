package com.trafficgame.shared.protocol;

import java.util.List;
import java.util.Map;

/**
 * Full game state snapshot sent to client on initial connection.
 */
public final class GameStateSnapshot implements com.trafficgame.engine.protocol.Snapshot {

    private long tickNumber;
    private int mapWidth;
    private int mapHeight;
    private int[][] terrain;
    private double[][] elevation;
    private List<RoadSegmentData> roads;
    private List<IntersectionData> intersections;
    private List<VehicleData> vehicles;
    private List<DistrictData> districts;
    private List<BuildingSnapshotData> buildings;
    private PlayerData player;
    private String currentSeason;
    private String currentWeather;
    private List<EventForecast> forecasts;

    public GameStateSnapshot() {}

    @Override
    public long tickNumber() { return tickNumber; }

    public long getTickNumber() { return tickNumber; }
    public void setTickNumber(long tickNumber) { this.tickNumber = tickNumber; }

    public int getMapWidth() { return mapWidth; }
    public void setMapWidth(int mapWidth) { this.mapWidth = mapWidth; }

    public int getMapHeight() { return mapHeight; }
    public void setMapHeight(int mapHeight) { this.mapHeight = mapHeight; }

    public int[][] getTerrain() { return terrain; }
    public void setTerrain(int[][] terrain) { this.terrain = terrain; }

    public double[][] getElevation() { return elevation; }
    public void setElevation(double[][] elevation) { this.elevation = elevation; }

    public List<RoadSegmentData> getRoads() { return roads; }
    public void setRoads(List<RoadSegmentData> roads) { this.roads = roads; }

    public List<IntersectionData> getIntersections() { return intersections; }
    public void setIntersections(List<IntersectionData> intersections) { this.intersections = intersections; }

    public List<VehicleData> getVehicles() { return vehicles; }
    public void setVehicles(List<VehicleData> vehicles) { this.vehicles = vehicles; }

    public List<DistrictData> getDistricts() { return districts; }
    public void setDistricts(List<DistrictData> districts) { this.districts = districts; }

    public List<BuildingSnapshotData> getBuildings() { return buildings; }
    public void setBuildings(List<BuildingSnapshotData> buildings) { this.buildings = buildings; }

    public PlayerData getPlayer() { return player; }
    public void setPlayer(PlayerData player) { this.player = player; }

    public String getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(String currentSeason) { this.currentSeason = currentSeason; }

    public String getCurrentWeather() { return currentWeather; }
    public void setCurrentWeather(String currentWeather) { this.currentWeather = currentWeather; }

    public List<EventForecast> getForecasts() { return forecasts; }
    public void setForecasts(List<EventForecast> forecasts) { this.forecasts = forecasts; }

    public record RoadSegmentData(String id, String fromId, String toId, int lanes,
                                   double speedLimit, String roadType, double condition,
                                   double congestion) {}

    public record IntersectionData(String id, double x, double y, String type, String signalState) {}

    public record VehicleData(long id, double x, double y, double speed, double angle, String type, int laneIndex) {}

    public record BuildingSnapshotData(String id, double x, double z, double width, double depth,
                                        double height, String style, int districtNumber, int colorIndex) {}

    public record DistrictData(String id, String name, int number, boolean unlocked, int tier,
                                double unlockProgress, int vehiclesRequired, String ratingRequired,
                                double centerX, double centerY) {}

    public record PlayerData(long roadPoints, long blueprintTokens, String ratingGrade,
                              double ratingScore, int cityTier, int vehiclesDelivered) {}
}
