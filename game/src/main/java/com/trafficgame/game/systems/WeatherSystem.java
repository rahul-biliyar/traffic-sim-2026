package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.RoadNetwork;
import com.trafficgame.game.model.RoadSegment;
import com.trafficgame.engine.graph.Edge;

/**
 * Manages the seasonal weather cycle and its effects on road conditions.
 */
public final class WeatherSystem implements GameSystem {

    public enum Season { SPRING, SUMMER, FALL, WINTER }
    public enum Weather { CLEAR, RAIN, HEAVY_RAIN, FOG, SNOW, ICE, BLIZZARD, THUNDERSTORM }

    private final RoadNetwork roadNetwork;
    private double seasonTimer; // seconds into current season
    private Season currentSeason;
    private Weather currentWeather;
    private double weatherTimer;

    public WeatherSystem(RoadNetwork roadNetwork) {
        this.roadNetwork = roadNetwork;
        this.currentSeason = Season.SPRING;
        this.currentWeather = Weather.CLEAR;
        this.seasonTimer = 0;
        this.weatherTimer = 0;
    }

    @Override
    public void update(GameState state, double dt) {
        seasonTimer += dt;
        if (seasonTimer >= GameConfig.SEASON_DURATION_SECONDS) {
            seasonTimer = 0;
            currentSeason = Season.values()[(currentSeason.ordinal() + 1) % Season.values().length];
        }

        weatherTimer -= dt;
        if (weatherTimer <= 0) {
            // Weather changes periodically
            weatherTimer = 120 + Math.random() * 300; // 2-7 minutes
            currentWeather = Weather.CLEAR; // default, events override
        }

        applyWeatherEffects();
    }

    private void applyWeatherEffects() {
        double speedFactor = switch (currentWeather) {
            case RAIN -> GameConfig.RAIN_SPEED_FACTOR;
            case HEAVY_RAIN -> GameConfig.RAIN_SPEED_FACTOR * 0.8;
            case SNOW -> GameConfig.SNOW_SPEED_FACTOR;
            case ICE -> GameConfig.SNOW_SPEED_FACTOR * 0.6;
            case FOG -> GameConfig.FOG_SPEED_FACTOR;
            case BLIZZARD -> 0.3;
            default -> 1.0;
        };

        // Apply speed factor to all road segments
        for (Edge<RoadSegment> edge : roadNetwork.getAllEdges()) {
            RoadSegment seg = edge.getData();
            double baseSpeed = switch (seg.getRoadType()) {
                case PATH -> 20;
                case LOCAL -> 40;
                case COLLECTOR -> 50;
                case ARTERIAL -> 60;
                case HIGHWAY -> 100;
            };
            seg.setSpeedLimit(baseSpeed * speedFactor);
        }
    }

    public void setWeather(Weather weather) {
        this.currentWeather = weather;
    }

    public Weather getCurrentWeather() { return currentWeather; }
    public Season getCurrentSeason() { return currentSeason; }
    public double getSeasonProgress() { return seasonTimer / GameConfig.SEASON_DURATION_SECONDS; }

    @Override
    public String getName() { return "Weather"; }
}
