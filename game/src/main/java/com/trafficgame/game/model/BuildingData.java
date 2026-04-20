package com.trafficgame.game.model;

import com.trafficgame.game.mapgen.DistrictTemplate;

/**
 * Immutable data describing a placed building.
 * Generated server-side and sent to the client in the snapshot.
 */
public final class BuildingData {

    private final String id;
    private final double x;          // world X centre
    private final double z;          // world Z centre (server Y = client Z)
    private final double width;      // X extent
    private final double depth;      // Z extent
    private final double height;     // Y extent (how tall)
    private final String style;      // matches DistrictTemplate.BuildingStyle name
    private final int districtNumber;
    private final int colorIndex;    // deterministic palette index

    public BuildingData(String id, double x, double z,
                        double width, double depth, double height,
                        DistrictTemplate.BuildingStyle style,
                        int districtNumber, int colorIndex) {
        this.id = id;
        this.x = x;
        this.z = z;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.style = style.name();
        this.districtNumber = districtNumber;
        this.colorIndex = colorIndex;
    }

    public String getId()          { return id; }
    public double getX()           { return x; }
    public double getZ()           { return z; }
    public double getWidth()       { return width; }
    public double getDepth()       { return depth; }
    public double getHeight()      { return height; }
    public String getStyle()       { return style; }
    public int getDistrictNumber() { return districtNumber; }
    public int getColorIndex()     { return colorIndex; }
}
