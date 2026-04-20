package com.trafficgame.game.model;

import com.trafficgame.engine.entity.Component;

/**
 * Component representing a vehicle's movement state on the road network.
 */
public final class VehicleMovement implements Component {

    private String currentEdgeId;
    private double positionOnEdge; // 0.0 (start) to 1.0 (end)
    private double speed;
    private double targetSpeed;
    private int laneIndex;          // 0-based lane within the edge
    private double laneOffset;      // lateral offset in world units from road centre

    public VehicleMovement(String currentEdgeId) {
        this.currentEdgeId = currentEdgeId;
        this.positionOnEdge = 0.0;
        this.speed = 0.0;
        this.targetSpeed = 0.0;
        this.laneIndex = 0;
        this.laneOffset = 0.0;
    }

    public String getCurrentEdgeId() { return currentEdgeId; }
    public void setCurrentEdgeId(String edgeId) { this.currentEdgeId = edgeId; }

    public double getPositionOnEdge() { return positionOnEdge; }
    public void setPositionOnEdge(double pos) { this.positionOnEdge = pos; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getTargetSpeed() { return targetSpeed; }
    public void setTargetSpeed(double targetSpeed) { this.targetSpeed = targetSpeed; }

    public int getLaneIndex() { return laneIndex; }
    public void setLaneIndex(int laneIndex) { this.laneIndex = laneIndex; }

    public double getLaneOffset() { return laneOffset; }
    public void setLaneOffset(double laneOffset) { this.laneOffset = laneOffset; }
}
