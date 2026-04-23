package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.PlayerProfile;

/**
 * Manages Road Points and Blueprint Token earning and spending.
 */
public final class CurrencySystem implements GameSystem {

    private final PlayerProfile profile;
    private double rpAccumulator;

    public CurrencySystem(PlayerProfile profile) {
        this.profile = profile;
        this.rpAccumulator = 0;
    }

    @Override
    public void update(GameState state, double dt) {
        // Passive USD earning (base rate modified by rating)
        double ratingMultiplier = switch (profile.getRatingGrade()) {
            case "S" -> GameConfig.USD_RATING_MULTIPLIER_S;
            case "A" -> GameConfig.USD_RATING_MULTIPLIER_A;
            case "B" -> 1.0;
            case "C" -> 0.75;
            case "D" -> 0.5;
            default -> 0.25; // F
        };

        rpAccumulator += dt * 2.0 * ratingMultiplier; // ~2 USD/sec base
        if (rpAccumulator >= 1.0) {
            long earned = (long) rpAccumulator;
            profile.addRoadPoints(earned);
            rpAccumulator -= earned;
        }
    }

    public void awardDelivery() {
        profile.addRoadPoints(GameConfig.USD_PER_DELIVERY);
        profile.incrementVehiclesDelivered();
    }

    public void awardEventCompletion() {
        profile.addRoadPoints(GameConfig.USD_EVENT_BONUS);
        profile.incrementEventsCompleted();
    }

    public void awardBlueprintToken(long amount) {
        profile.addBlueprintTokens(amount);
    }

    @Override
    public String getName() { return "Currency"; }
}
