package com.trafficgame.game;

import com.trafficgame.engine.core.GameLoop;
import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.SystemOrchestrator;
import com.trafficgame.engine.entity.EntityManager;
import com.trafficgame.engine.event.EventBus;
import com.trafficgame.engine.terrain.TileMap;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.mapgen.CityMapGenerator;
import com.trafficgame.game.mapgen.DistrictTemplate;
import com.trafficgame.game.model.*;
import com.trafficgame.game.systems.*;

import java.util.*;

/**
 * Main game orchestrator. Wires together all systems, manages game state,
 * and drives the simulation loop.
 */
public final class TrafficGame implements GameState {

    private final EntityManager entityManager;
    private final EventBus eventBus;
    private final SystemOrchestrator systems;
    private final GameLoop gameLoop;

    private final TileMap terrain;
    private final RoadNetwork roadNetwork;
    private final List<BuildingData> buildings;
    private final PlayerProfile playerProfile;
    private final List<District> districts;

    // Systems (exposed for server to query)
    private final VehicleSpawnerSystem spawnerSystem;
    private final WeatherSystem weatherSystem;
    private final EventSystem eventSystem;
    private final RatingSystem ratingSystem;
    private final CurrencySystem currencySystem;

    private long tickNumber;
    private final int seed;

    public TrafficGame(int seed) {
        this.seed = seed;
        this.entityManager = new EntityManager();
        this.eventBus = new EventBus();
        this.systems = new SystemOrchestrator();
        this.playerProfile = new PlayerProfile();
        this.tickNumber = 0;

        // Generate map
        CityMapGenerator mapGen = new CityMapGenerator(seed);
        CityMapGenerator.GeneratedMap map = mapGen.generate(GameConfig.DEFAULT_MAP_WIDTH, GameConfig.DEFAULT_MAP_HEIGHT);
        this.terrain = map.terrain();
        this.roadNetwork = map.roads();
        this.buildings = map.buildings();

        // Create districts from templates
        this.districts = createDistricts();

        // Set district center coordinates from map generation seeds
        int[][] seeds = map.districtSeeds();
        if (seeds != null) {
            for (int i = 0; i < districts.size() && i < seeds.length; i++) {
                districts.get(i).setCenterX(seeds[i][0]);
                districts.get(i).setCenterY(seeds[i][1]);
            }
        }

        // Unlock first district by default so player starts with something to do
        if (!districts.isEmpty()) {
            districts.get(0).setUnlocked(true);
        }

        // Initialize systems in update order
        this.spawnerSystem = new VehicleSpawnerSystem(entityManager, roadNetwork, seed, this);
        TrafficSignalSystem signalSystem = new TrafficSignalSystem(roadNetwork);
        VehicleMovementSystem movementSystem = new VehicleMovementSystem(entityManager, roadNetwork);
        this.weatherSystem = new WeatherSystem(roadNetwork);
        this.eventSystem = new EventSystem(eventBus, seed);
        this.ratingSystem = new RatingSystem(entityManager, playerProfile);
        this.currencySystem = new CurrencySystem(playerProfile);

        systems.register(spawnerSystem);
        systems.register(signalSystem);
        systems.register(movementSystem);
        systems.register(weatherSystem);
        systems.register(eventSystem);
        systems.register(ratingSystem);
        systems.register(currencySystem);

        // Game loop (100ms = 10 ticks/sec)
        this.gameLoop = new GameLoop(100, systems, this);
    }

    /**
     * Advance the simulation by one tick.
     */
    public void tick() {
        systems.update(this, GameConfig.TICK_INTERVAL);
        tickNumber++;
    }

    @Override
    public long getTickNumber() { return tickNumber; }

    @Override
    public long advanceTick() { tick(); return tickNumber; }

    public EntityManager getEntityManager() { return entityManager; }
    public EventBus getEventBus() { return eventBus; }
    public TileMap getTerrain() { return terrain; }
    public RoadNetwork getRoadNetwork() { return roadNetwork; }
    public List<BuildingData> getBuildings() { return buildings; }
    public PlayerProfile getPlayerProfile() { return playerProfile; }
    public List<District> getDistricts() { return districts; }
    public GameLoop getGameLoop() { return gameLoop; }
    public WeatherSystem getWeatherSystem() { return weatherSystem; }
    public EventSystem getEventSystem() { return eventSystem; }
    public RatingSystem getRatingSystem() { return ratingSystem; }
    public CurrencySystem getCurrencySystem() { return currencySystem; }
    public VehicleSpawnerSystem getSpawnerSystem() { return spawnerSystem; }
    public int getSeed() { return seed; }

    private List<District> createDistricts() {
        List<District> d = new ArrayList<>();
        for (DistrictTemplate t : DistrictTemplate.values()) {
            String ratingReq = null;
            int vehiclesReq = 0;
            switch (t) {
                case TOWN_CENTER -> { vehiclesReq = 0; }
                case RESIDENTIAL -> { vehiclesReq = 0; }
                case FARMLAND -> { vehiclesReq = 50; }
                case COMMERCIAL -> { vehiclesReq = 100; ratingReq = "B"; }
                case INDUSTRIAL -> { vehiclesReq = 200; }
                case HIGHWAY_CORRIDOR -> { vehiclesReq = 500; ratingReq = "A"; }
                case DOWNTOWN -> { vehiclesReq = 1000; ratingReq = "S"; }
            }
            d.add(new District(t.name().toLowerCase(), t.getName(), t.getNumber(), vehiclesReq, ratingReq));
        }
        return d;
    }
}
