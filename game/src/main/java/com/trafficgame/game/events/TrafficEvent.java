package com.trafficgame.game.events;

/**
 * Runtime instance of a traffic event with lifecycle management.
 */
public final class TrafficEvent {

    public enum Phase { FORECAST, ACTIVE, RESOLVING, COMPLETE }

    private final String id;
    private final GameEventType type;
    private Phase phase;
    private double forecastTimeRemaining;
    private double activeTimeRemaining;
    private final double totalDuration;

    public TrafficEvent(String id, GameEventType type, double forecastDuration, double activeDuration) {
        this.id = id;
        this.type = type;
        this.phase = Phase.FORECAST;
        this.forecastTimeRemaining = forecastDuration;
        this.activeTimeRemaining = activeDuration;
        this.totalDuration = activeDuration;
    }

    public String getId() { return id; }
    public GameEventType getType() { return type; }
    public Phase getPhase() { return phase; }
    public double getForecastTimeRemaining() { return forecastTimeRemaining; }
    public double getActiveTimeRemaining() { return activeTimeRemaining; }
    public double getTotalDuration() { return totalDuration; }

    public void reduceForecastTime(double dt) {
        forecastTimeRemaining -= dt;
    }

    public void activate() {
        this.phase = Phase.ACTIVE;
    }

    public void update(double dt) {
        if (phase == Phase.ACTIVE) {
            activeTimeRemaining -= dt;
            if (activeTimeRemaining <= 0) {
                phase = Phase.RESOLVING;
            }
        } else if (phase == Phase.RESOLVING) {
            phase = Phase.COMPLETE;
        }
    }

    public boolean isComplete() {
        return phase == Phase.COMPLETE;
    }
}
