package com.trafficgame.game.model;

/**
 * Player profile tracking progression, currencies, and stats.
 */
public final class PlayerProfile {

    private long roadPoints;
    private long blueprintTokens;
    private int vehiclesDelivered;
    private int eventsCompleted;
    private int eventsFailed;
    private double ratingScore;
    private String ratingGrade;
    private int cityTier;
    private long playTimeSeconds;

    public PlayerProfile() {
        this.roadPoints = 0;
        this.blueprintTokens = 0;
        this.vehiclesDelivered = 0;
        this.eventsCompleted = 0;
        this.eventsFailed = 0;
        this.ratingScore = 0.8; // start at B
        this.ratingGrade = "B";
        this.cityTier = 1;
        this.playTimeSeconds = 0;
    }

    public long getRoadPoints() { return roadPoints; }
    public void addRoadPoints(long amount) { this.roadPoints += amount; }
    public boolean spendRoadPoints(long amount) {
        if (roadPoints >= amount) { roadPoints -= amount; return true; }
        return false;
    }

    public long getBlueprintTokens() { return blueprintTokens; }
    public void addBlueprintTokens(long amount) { this.blueprintTokens += amount; }
    public boolean spendBlueprintTokens(long amount) {
        if (blueprintTokens >= amount) { blueprintTokens -= amount; return true; }
        return false;
    }

    public int getVehiclesDelivered() { return vehiclesDelivered; }
    public void incrementVehiclesDelivered() { vehiclesDelivered++; }

    public int getEventsCompleted() { return eventsCompleted; }
    public void incrementEventsCompleted() { eventsCompleted++; }

    public int getEventsFailed() { return eventsFailed; }
    public void incrementEventsFailed() { eventsFailed++; }

    public double getRatingScore() { return ratingScore; }
    public void setRatingScore(double score) { this.ratingScore = Math.max(0, Math.min(1, score)); }

    public String getRatingGrade() { return ratingGrade; }
    public void setRatingGrade(String grade) { this.ratingGrade = grade; }

    public int getCityTier() { return cityTier; }
    public void setCityTier(int tier) { this.cityTier = tier; }

    public long getPlayTimeSeconds() { return playTimeSeconds; }
    public void addPlayTime(long seconds) { this.playTimeSeconds += seconds; }
}
