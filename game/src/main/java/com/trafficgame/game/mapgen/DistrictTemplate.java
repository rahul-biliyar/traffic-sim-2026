package com.trafficgame.game.mapgen;

import com.trafficgame.game.model.RoadSegment;

/**
 * Template for each district type.  Drives road layout, building density,
 * road tier and building style during procedural map generation.
 */
public enum DistrictTemplate {

    TOWN_CENTER(
            1,
            "Town Center",
            /* roadSpacing */ 8,
            /* buildingChance */ 0.55,
            /* minBuildingH */ 10, /* maxBuildingH */ 18,
            /* minBuildingW */ 8, /* maxBuildingW */ 12,
            RoadSegment.RoadType.COLLECTOR,
            /* lanes */ 2,
            /* speedLimit */ 40,
            /* signalChance */ 0.6,
            BuildingStyle.SHOP
    ),

    RESIDENTIAL(
            2,
            "Residential",
            /* roadSpacing */ 8,
            /* buildingChance */ 0.40,
            /* minBuildingH */ 6, /* maxBuildingH */ 10,
            /* minBuildingW */ 8, /* maxBuildingW */ 11,
            RoadSegment.RoadType.LOCAL,
            /* lanes */ 1,
            /* speedLimit */ 30,
            /* signalChance */ 0.15,
            BuildingStyle.HOUSE
    ),

    FARMLAND(
            3,
            "Farmland",
            /* roadSpacing */ 20,
            /* buildingChance */ 0.12,
            /* minBuildingH */ 6, /* maxBuildingH */ 10,
            /* minBuildingW */ 10, /* maxBuildingW */ 14,
            RoadSegment.RoadType.PATH,
            /* lanes */ 1,
            /* speedLimit */ 50,
            /* signalChance */ 0.0,
            BuildingStyle.FARM
    ),

    COMMERCIAL(
            4,
            "Commercial",
            /* roadSpacing */ 8,
            /* buildingChance */ 0.60,
            /* minBuildingH */ 16, /* maxBuildingH */ 35,
            /* minBuildingW */ 9, /* maxBuildingW */ 13,
            RoadSegment.RoadType.ARTERIAL,
            /* lanes */ 2,
            /* speedLimit */ 50,
            /* signalChance */ 0.7,
            BuildingStyle.OFFICE
    ),

    INDUSTRIAL(
            5,
            "Industrial",
            /* roadSpacing */ 12,
            /* buildingChance */ 0.45,
            /* minBuildingH */ 6, /* maxBuildingH */ 12,
            /* minBuildingW */ 10, /* maxBuildingW */ 14,
            RoadSegment.RoadType.COLLECTOR,
            /* lanes */ 2,
            /* speedLimit */ 40,
            /* signalChance */ 0.3,
            BuildingStyle.WAREHOUSE
    ),

    HIGHWAY_CORRIDOR(
            6,
            "Highway Corridor",
            /* roadSpacing */ 20,
            /* buildingChance */ 0.08,
            /* minBuildingH */ 5, /* maxBuildingH */ 8,
            /* minBuildingW */ 8, /* maxBuildingW */ 12,
            RoadSegment.RoadType.HIGHWAY,
            /* lanes */ 3,
            /* speedLimit */ 100,
            /* signalChance */ 0.0,
            BuildingStyle.SERVICE
    ),

    DOWNTOWN(
            7,
            "Downtown",
            /* roadSpacing */ 5,
            /* buildingChance */ 0.90,
            /* minBuildingH */ 25, /* maxBuildingH */ 55,
            /* minBuildingW */ 9, /* maxBuildingW */ 12,
            RoadSegment.RoadType.ARTERIAL,
            /* lanes */ 3,
            /* speedLimit */ 40,
            /* signalChance */ 0.85,
            BuildingStyle.TOWER
    );

    public enum BuildingStyle { SHOP, HOUSE, FARM, OFFICE, WAREHOUSE, SERVICE, TOWER }

    private final int number;
    private final String name;
    private final int roadSpacing;
    private final double buildingChance;
    private final int minBuildingH;
    private final int maxBuildingH;
    private final int minBuildingW;
    private final int maxBuildingW;
    private final RoadSegment.RoadType defaultRoadType;
    private final int defaultLanes;
    private final double defaultSpeedLimit;
    private final double signalChance;
    private final BuildingStyle buildingStyle;

    DistrictTemplate(int number, String name, int roadSpacing,
                     double buildingChance, int minBuildingH, int maxBuildingH,
                     int minBuildingW, int maxBuildingW,
                     RoadSegment.RoadType defaultRoadType, int defaultLanes,
                     double defaultSpeedLimit, double signalChance,
                     BuildingStyle buildingStyle) {
        this.number = number;
        this.name = name;
        this.roadSpacing = roadSpacing;
        this.buildingChance = buildingChance;
        this.minBuildingH = minBuildingH;
        this.maxBuildingH = maxBuildingH;
        this.minBuildingW = minBuildingW;
        this.maxBuildingW = maxBuildingW;
        this.defaultRoadType = defaultRoadType;
        this.defaultLanes = defaultLanes;
        this.defaultSpeedLimit = defaultSpeedLimit;
        this.signalChance = signalChance;
        this.buildingStyle = buildingStyle;
    }

    public int getNumber()                    { return number; }
    public String getName()                   { return name; }
    public int getRoadSpacing()               { return roadSpacing; }
    public double getBuildingChance()          { return buildingChance; }
    public int getMinBuildingH()              { return minBuildingH; }
    public int getMaxBuildingH()              { return maxBuildingH; }
    public int getMinBuildingW()              { return minBuildingW; }
    public int getMaxBuildingW()              { return maxBuildingW; }
    public RoadSegment.RoadType getDefaultRoadType() { return defaultRoadType; }
    public int getDefaultLanes()              { return defaultLanes; }
    public double getDefaultSpeedLimit()      { return defaultSpeedLimit; }
    public double getSignalChance()           { return signalChance; }
    public BuildingStyle getBuildingStyle()    { return buildingStyle; }
}
