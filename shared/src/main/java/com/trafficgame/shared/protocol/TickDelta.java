package com.trafficgame.shared.protocol;

import java.util.List;
import java.util.Map;

/**
 * Incremental state update sent from server to client each tick.
 * Contains only changed entities to minimize bandwidth.
 */
public final class TickDelta implements com.trafficgame.engine.protocol.StateDelta {

    private long tickNumber;
    private List<VehicleUpdate> vehicleUpdates;
    private List<Long> removedVehicleIds;
    private List<SignalUpdate> signalUpdates;
    private RatingUpdate ratingUpdate;
    private List<EventUpdate> eventUpdates;
    private WeatherUpdate weatherUpdate;

    public TickDelta() {}

    public TickDelta(long tickNumber) {
        this.tickNumber = tickNumber;
    }

    @Override
    public long tickNumber() { return tickNumber; }

    public List<VehicleUpdate> getVehicleUpdates() { return vehicleUpdates; }
    public void setVehicleUpdates(List<VehicleUpdate> v) { this.vehicleUpdates = v; }

    public List<Long> getRemovedVehicleIds() { return removedVehicleIds; }
    public void setRemovedVehicleIds(List<Long> v) { this.removedVehicleIds = v; }

    public List<SignalUpdate> getSignalUpdates() { return signalUpdates; }
    public void setSignalUpdates(List<SignalUpdate> v) { this.signalUpdates = v; }

    public RatingUpdate getRatingUpdate() { return ratingUpdate; }
    public void setRatingUpdate(RatingUpdate v) { this.ratingUpdate = v; }

    public List<EventUpdate> getEventUpdates() { return eventUpdates; }
    public void setEventUpdates(List<EventUpdate> v) { this.eventUpdates = v; }

    public WeatherUpdate getWeatherUpdate() { return weatherUpdate; }
    public void setWeatherUpdate(WeatherUpdate v) { this.weatherUpdate = v; }

    public record VehicleUpdate(long id, double x, double y, double speed, double angle, String type, int laneIndex, double elevation) {}
    public record SignalUpdate(String intersectionId, String state, double timeRemaining) {}
    public record RatingUpdate(double score, String grade, Map<String, Double> breakdown) {}
    public record EventUpdate(String eventId, String type, String phase, double timeRemaining) {}
    public record WeatherUpdate(String weather, String season, double intensity) {}
}
