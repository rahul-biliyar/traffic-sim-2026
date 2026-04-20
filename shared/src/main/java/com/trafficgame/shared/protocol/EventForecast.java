package com.trafficgame.shared.protocol;

/**
 * Forecast of an upcoming event, shown to the player before it starts.
 */
public record EventForecast(
    String eventId,
    String eventType,
    double timeUntilStart,
    String severity,
    String description,
    String[] choices
) {}
