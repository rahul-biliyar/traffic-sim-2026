package com.trafficgame.game.mapgen;

/**
 * Cell types for the grid-based city map.
 * Each cell in the map grid is exactly one type.
 */
public enum CellType {
    EMPTY,          // Grass / undeveloped land
    ROAD,           // Road surface (any tier)
    BUILDING,       // Occupied by a building
    INTERSECTION,   // Road crossing point
    PARK,           // Green space / playground
    FARM,           // Farmland / crops
    WATER,          // River / pond
    FOREST          // Dense trees (impassable to roads)
}
