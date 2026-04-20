package com.trafficgame.game.mapgen;

import com.trafficgame.engine.terrain.NoiseGenerator;
import com.trafficgame.engine.terrain.Tile;
import com.trafficgame.engine.terrain.TileMap;

/**
 * Generates terrain using dual noise layers (elevation + moisture) to classify zones.
 */
public final class TerrainGenerator {

    private final int seed;

    public TerrainGenerator(int seed) {
        this.seed = seed;
    }

    public TileMap generate(int width, int height) {
        NoiseGenerator elevationNoise = new NoiseGenerator(seed).frequency(0.008f).octaves(6);
        NoiseGenerator moistureNoise = new NoiseGenerator(seed + 1000).frequency(0.012f).octaves(4);

        final double halfW = width / 2.0;
        final double halfH = height / 2.0;

        TileMap map = new TileMap(width, height);
        map.fill((x, y) -> {
            double elevation = elevationNoise.get(x, y);
            double moisture = moistureNoise.get(x, y);

            // Radial bias: boost centre toward city, depress edges toward water
            double dx = (x - halfW) / halfW;
            double dy = (y - halfH) / halfH;
            double dist = Math.sqrt(dx * dx + dy * dy);
            elevation += (1.0 - Math.min(1.0, dist)) * 0.15;
            if (dist > 0.85) elevation -= (dist - 0.85) * 0.6;
            elevation = Math.max(0, Math.min(1.0, elevation));

            ZoneType zone = classifyZone(elevation, moisture);
            int type = zone.ordinal();
            boolean passable = zone != ZoneType.WATER && zone != ZoneType.MOUNTAIN;
            return new Tile(type, elevation, moisture, passable);
        });

        return map;
    }

    static ZoneType classifyZone(double elevation, double moisture) {
        if (elevation < 0.3) return ZoneType.WATER;
        if (elevation < 0.35) return ZoneType.COAST;
        if (elevation > 0.85) return ZoneType.MOUNTAIN;
        if (elevation > 0.7 && moisture > 0.5) return ZoneType.FOREST;
        if (elevation > 0.4 && elevation < 0.6 && moisture > 0.3) return ZoneType.CITY;
        if (elevation >= 0.35 && elevation <= 0.65) return ZoneType.SUBURB;
        return ZoneType.COUNTRYSIDE;
    }
}
