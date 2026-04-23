package com.trafficgame.game.model;

/**
 * Data associated with an intersection (graph node).
 */
public final class Intersection {

    private IntersectionType type;
    private SignalState signalState;
    private double signalTimer;       // seconds until next state change
    private double greenDuration;     // configurable green phase length
    private int tier;                 // upgrade tier (1-4 for signals)
    private int districtNumber;       // Voronoi district this intersection belongs to (1-7)

    public Intersection(IntersectionType type) {
        this.type = type;
        this.signalState = SignalState.GREEN_NS;
        this.signalTimer = 30.0;
        this.greenDuration = 30.0;
        this.tier = 1;
        this.districtNumber = 0;
    }

    public IntersectionType getType() { return type; }
    public void setType(IntersectionType type) { this.type = type; }

    public SignalState getSignalState() { return signalState; }
    public void setSignalState(SignalState state) { this.signalState = state; }

    public double getSignalTimer() { return signalTimer; }
    public void setSignalTimer(double timer) { this.signalTimer = timer; }

    public double getGreenDuration() { return greenDuration; }
    public void setGreenDuration(double duration) { this.greenDuration = duration; }

    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }

    public int getDistrictNumber() { return districtNumber; }
    public void setDistrictNumber(int districtNumber) { this.districtNumber = districtNumber; }

    public enum IntersectionType {
        UNCONTROLLED,
        YIELD,
        STOP,
        SIGNAL,
        ROUNDABOUT,
        TUNNEL
    }

    public enum SignalState {
        GREEN_NS,
        YELLOW_NS,
        RED_ALL_1,
        GREEN_EW,
        YELLOW_EW,
        RED_ALL_2;

        public SignalState next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean allowsNS() {
            return this == GREEN_NS;
        }

        public boolean allowsEW() {
            return this == GREEN_EW;
        }
    }
}
