package com.trafficgame.engine.terrain;

/**
 * A single tile in the tile map with a type and properties.
 */
public final class Tile {

    private int type;
    private double elevation;
    private double moisture;
    private boolean passable;

    public Tile(int type, double elevation, double moisture, boolean passable) {
        this.type = type;
        this.elevation = elevation;
        this.moisture = moisture;
        this.passable = passable;
    }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public double getElevation() { return elevation; }
    public void setElevation(double elevation) { this.elevation = elevation; }
    public double getMoisture() { return moisture; }
    public void setMoisture(double moisture) { this.moisture = moisture; }
    public boolean isPassable() { return passable; }
    public void setPassable(boolean passable) { this.passable = passable; }
}
