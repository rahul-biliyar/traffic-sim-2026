package com.trafficgame.game.model;

import com.trafficgame.engine.entity.Component;

import java.util.List;

/**
 * Component representing a vehicle's planned route through the road network.
 */
public final class VehicleRoute implements Component {

    private List<String> edgeIds;  // ordered edge IDs from start to destination
    private int currentIndex;      // index into edgeIds

    public VehicleRoute(List<String> edgeIds) {
        this.edgeIds = edgeIds;
        this.currentIndex = 0;
    }

    public List<String> getEdgeIds() { return edgeIds; }
    public void setEdgeIds(List<String> edgeIds) { this.edgeIds = edgeIds; this.currentIndex = 0; }

    public int getCurrentIndex() { return currentIndex; }

    public String getCurrentEdgeId() {
        if (currentIndex < edgeIds.size()) return edgeIds.get(currentIndex);
        return null;
    }

    public String advanceToNextEdge() {
        currentIndex++;
        return getCurrentEdgeId();
    }

    public boolean isComplete() {
        return currentIndex >= edgeIds.size();
    }

    public int remainingEdges() {
        return edgeIds.size() - currentIndex;
    }
}
