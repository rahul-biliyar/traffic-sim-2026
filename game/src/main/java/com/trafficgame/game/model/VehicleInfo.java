package com.trafficgame.game.model;

import com.trafficgame.engine.entity.Component;

/**
 * Component holding vehicle identity and type information.
 */
public final class VehicleInfo implements Component {

    private final VehicleType type;
    private final VehicleConfig config;
    private final String originNodeId;
    private final String destinationNodeId;
    private double waitTime; // accumulated time spent waiting

    public VehicleInfo(VehicleType type, String originNodeId, String destinationNodeId) {
        this.type = type;
        this.config = VehicleConfig.forType(type);
        this.originNodeId = originNodeId;
        this.destinationNodeId = destinationNodeId;
        this.waitTime = 0;
    }

    public VehicleType getType() { return type; }
    public VehicleConfig getConfig() { return config; }
    public String getOriginNodeId() { return originNodeId; }
    public String getDestinationNodeId() { return destinationNodeId; }
    public double getWaitTime() { return waitTime; }
    public void addWaitTime(double dt) { this.waitTime += dt; }
}
