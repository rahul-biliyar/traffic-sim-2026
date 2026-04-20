package com.trafficgame.game.model;

/**
 * Definition of a city district with unlock conditions and configuration.
 */
public final class District {

    private final String id;
    private final String name;
    private final int number;        // 1-7
    private boolean unlocked;
    private int tier;                // progression within district
    private int vehiclesRequired;    // unlock condition
    private String ratingRequired;   // minimum rating to unlock (null if none)
    private String description;

    public District(String id, String name, int number, int vehiclesRequired, String ratingRequired) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.vehiclesRequired = vehiclesRequired;
        this.ratingRequired = ratingRequired;
        this.unlocked = number == 1; // Downtown Core always unlocked
        this.tier = 0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getNumber() { return number; }
    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }
    public int getVehiclesRequired() { return vehiclesRequired; }
    public String getRatingRequired() { return ratingRequired; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
