package com.trafficgame.game.model;

/**
 * Data associated with a road segment (graph edge).
 */
public final class RoadSegment {

    private int lanes;
    private double speedLimit;
    private RoadType roadType;
    private double condition;     // 1.0 = perfect, 0.0 = destroyed
    private double congestion;    // 0.0 = free flow, 1.0 = gridlock
    private int vehicleCount;
    private boolean blocked;
    private boolean bridge;

    public RoadSegment(int lanes, double speedLimit, RoadType roadType) {
        this.lanes = lanes;
        this.speedLimit = speedLimit;
        this.roadType = roadType;
        this.condition = 1.0;
        this.congestion = 0.0;
        this.vehicleCount = 0;
        this.blocked = false;
        this.bridge = false;
    }

    public int getLanes() { return lanes; }
    public void setLanes(int lanes) { this.lanes = lanes; }

    public double getSpeedLimit() { return speedLimit; }
    public void setSpeedLimit(double speedLimit) { this.speedLimit = speedLimit; }

    public RoadType getRoadType() { return roadType; }
    public void setRoadType(RoadType roadType) { this.roadType = roadType; }

    public double getCondition() { return condition; }
    public void setCondition(double condition) { this.condition = Math.max(0, Math.min(1, condition)); }

    public double getCongestion() { return congestion; }
    public void setCongestion(double congestion) { this.congestion = Math.max(0, Math.min(1, congestion)); }

    public int getVehicleCount() { return vehicleCount; }
    public void setVehicleCount(int count) { this.vehicleCount = count; }

    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public boolean isBridge() { return bridge; }
    public void setBridge(boolean bridge) { this.bridge = bridge; }

    /**
     * Effective speed considering condition and congestion.
     */
    public double getEffectiveSpeed() {
        double conditionFactor = 0.5 + 0.5 * condition;
        double congestionFactor = 1.0 - congestion * 0.8;
        return speedLimit * conditionFactor * congestionFactor;
    }

    /**
     * Capacity in vehicles based on lanes.
     */
    public int getCapacity() {
        return lanes * 8; // approx vehicles per lane
    }

    public enum RoadType {
        PATH,
        LOCAL,        // 2-lane
        COLLECTOR,    // 2-lane wider
        ARTERIAL,     // 4-lane
        HIGHWAY       // high speed, high capacity
    }
}
