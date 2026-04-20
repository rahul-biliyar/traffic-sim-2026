package com.trafficgame.game.model;

import com.trafficgame.engine.graph.Graph;

/**
 * Traffic-specific road network built on the engine's generic Graph.
 * Nodes are Intersections, edges are RoadSegments.
 */
public final class RoadNetwork extends Graph<Intersection, RoadSegment> {

    private int entryPointCount;
    private int exitPointCount;

    public int getEntryPointCount() { return entryPointCount; }
    public void setEntryPointCount(int count) { this.entryPointCount = count; }

    public int getExitPointCount() { return exitPointCount; }
    public void setExitPointCount(int count) { this.exitPointCount = count; }
}
