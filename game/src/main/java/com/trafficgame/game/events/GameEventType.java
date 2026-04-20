package com.trafficgame.game.events;

/**
 * Types of traffic events with their tier and default duration.
 */
public enum GameEventType {

    RUSH_HOUR(1, 240),           // 4 min
    RAIN(1, 150),                // 2.5 min
    ACCIDENT(1, 90),             // 1.5 min
    HEAVY_FOG(2, 150),           // 2.5 min
    SNOW_ICE(2, 240),            // 4 min
    EMERGENCY_CASCADE(2, 120),   // 2 min
    CONSTRUCTION_ZONE(2, 300),   // 5 min
    FLOODING(3, 240),            // 4 min
    BLIZZARD(3, 360),            // 6 min
    STADIUM_EXODUS(3, 240);      // 4 min

    private final int tier;
    private final double defaultDuration; // seconds

    GameEventType(int tier, double defaultDuration) {
        this.tier = tier;
        this.defaultDuration = defaultDuration;
    }

    public int getTier() { return tier; }
    public double getDefaultDuration() { return defaultDuration; }

    public static GameEventType[] availableForTier(int districtsUnlocked) {
        int maxTier;
        if (districtsUnlocked >= 5) maxTier = 3;
        else if (districtsUnlocked >= 3) maxTier = 2;
        else maxTier = 1;

        return java.util.Arrays.stream(values())
                .filter(e -> e.tier <= maxTier)
                .toArray(GameEventType[]::new);
    }
}
