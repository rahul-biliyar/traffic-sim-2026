package com.trafficgame.game.config;

/**
 * Central game configuration — all tunable constants.
 */
public final class GameConfig {

    // Map
    public static final int DEFAULT_MAP_WIDTH = 64;
    public static final int DEFAULT_MAP_HEIGHT = 64;
    public static final double TILE_SIZE = 16.0;

    // Simulation
    public static final double TICK_RATE_HZ = 10.0;
    public static final double TICK_INTERVAL = 1.0 / TICK_RATE_HZ;
    public static final int MAX_VEHICLES = 800;
    public static final double VEHICLE_SPAWN_INTERVAL = 0.3; // seconds between spawns

    // Traffic
    public static final double DEFAULT_SPEED_LIMIT = 50.0;
    public static final double HIGHWAY_SPEED_LIMIT = 100.0;
    public static final double MIN_VEHICLE_GAP = 2.0;
    public static final double IDM_DESIRED_TIME_HEADWAY = 1.5;
    public static final double IDM_MIN_GAP = 2.0;
    public static final double IDM_ACCELERATION_EXPONENT = 4.0;

    // Signals
    public static final double DEFAULT_GREEN_DURATION = 30.0;
    public static final double YELLOW_DURATION = 3.0;
    public static final double ALL_RED_DURATION = 3.0;

    // Rating
    public static final double RATING_WEIGHT_WAIT_TIME = 0.4;
    public static final double RATING_WEIGHT_EMERGENCY = 0.25;
    public static final double RATING_WEIGHT_ACCIDENTS = 0.15;
    public static final double RATING_WEIGHT_THROUGHPUT = 0.2;
    public static final double RATING_S_THRESHOLD = 0.95;
    public static final double RATING_A_THRESHOLD = 0.80;
    public static final double RATING_B_THRESHOLD = 0.65;
    public static final double RATING_C_THRESHOLD = 0.50;
    public static final double RATING_D_THRESHOLD = 0.35;

    // Currency
    public static final long RP_PER_DELIVERY = 10;
    public static final long RP_EVENT_BONUS = 100;
    public static final double RP_RATING_MULTIPLIER_S = 2.0;
    public static final double RP_RATING_MULTIPLIER_A = 1.5;
    public static final long BT_DISTRICT_UNLOCK = 5;
    public static final long BT_ACHIEVEMENT = 1;

    // Weather
    public static final double SEASON_DURATION_SECONDS = 86400.0; // 1 real day = 1 season
    public static final double RAIN_SPEED_FACTOR = 0.7;
    public static final double SNOW_SPEED_FACTOR = 0.5;
    public static final double ICE_ACCIDENT_PROBABILITY = 0.01; // per tick per vehicle
    public static final double FOG_SPEED_FACTOR = 0.6;

    // Events
    public static final double EVENT_FORECAST_DURATION = 45.0;
    public static final double EVENT_MIN_INTERVAL = 60.0; // seconds between events
    public static final double EVENT_MAX_INTERVAL = 180.0;

    // Road costs (in Road Points)
    public static final long COST_ROAD_PATH = 50;
    public static final long COST_ROAD_LOCAL = 100;
    public static final long COST_ROAD_COLLECTOR = 200;
    public static final long COST_ROAD_ARTERIAL = 500;
    public static final long COST_ROAD_HIGHWAY = 1000;
    public static final long COST_SIGNAL = 300;
    public static final long COST_ROUNDABOUT = 750;

    private GameConfig() {}
}
