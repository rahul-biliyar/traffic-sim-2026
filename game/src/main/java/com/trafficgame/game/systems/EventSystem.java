package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.engine.event.EventBus;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.events.GameEventType;
import com.trafficgame.game.events.TrafficEvent;

import java.util.*;

/**
 * Manages the event lifecycle: scheduling, forecasting, activating, and completing events.
 */
public final class EventSystem implements GameSystem {

    private final EventBus eventBus;
    private final Random random;
    private final List<TrafficEvent> activeEvents = new ArrayList<>();
    private final List<TrafficEvent> forecastedEvents = new ArrayList<>();
    private double nextEventTimer;
    private int districtsUnlocked;

    public EventSystem(EventBus eventBus, int seed) {
        this.eventBus = eventBus;
        this.random = new Random(seed);
        this.nextEventTimer = GameConfig.EVENT_MIN_INTERVAL + random.nextDouble() * 60;
        this.districtsUnlocked = 1;
    }

    @Override
    public void update(GameState state, double dt) {
        // Update active events
        Iterator<TrafficEvent> activeIt = activeEvents.iterator();
        while (activeIt.hasNext()) {
            TrafficEvent event = activeIt.next();
            event.update(dt);
            if (event.isComplete()) {
                activeIt.remove();
            }
        }

        // Update forecasted events (move to active when timer expires)
        Iterator<TrafficEvent> forecastIt = forecastedEvents.iterator();
        while (forecastIt.hasNext()) {
            TrafficEvent event = forecastIt.next();
            event.reduceForecastTime(dt);
            if (event.getForecastTimeRemaining() <= 0) {
                forecastIt.remove();
                event.activate();
                activeEvents.add(event);
            }
        }

        // Schedule new events
        nextEventTimer -= dt;
        if (nextEventTimer <= 0 && activeEvents.size() < maxSimultaneousEvents()) {
            scheduleNextEvent();
            nextEventTimer = GameConfig.EVENT_MIN_INTERVAL
                    + random.nextDouble() * (GameConfig.EVENT_MAX_INTERVAL - GameConfig.EVENT_MIN_INTERVAL);
        }
    }

    private void scheduleNextEvent() {
        GameEventType type = chooseEventType();
        TrafficEvent event = new TrafficEvent(
                UUID.randomUUID().toString(),
                type,
                GameConfig.EVENT_FORECAST_DURATION,
                type.getDefaultDuration()
        );
        forecastedEvents.add(event);
    }

    private GameEventType chooseEventType() {
        GameEventType[] available = GameEventType.availableForTier(districtsUnlocked);
        return available[random.nextInt(available.length)];
    }

    private int maxSimultaneousEvents() {
        if (districtsUnlocked >= 6) return 3;
        if (districtsUnlocked >= 4) return 2;
        return 1;
    }

    public void setDistrictsUnlocked(int count) {
        this.districtsUnlocked = count;
    }

    public List<TrafficEvent> getActiveEvents() {
        return Collections.unmodifiableList(activeEvents);
    }

    public List<TrafficEvent> getForecastedEvents() {
        return Collections.unmodifiableList(forecastedEvents);
    }

    @Override
    public String getName() { return "Event"; }
}
