package com.trafficgame.game.systems;

import com.trafficgame.engine.core.GameState;
import com.trafficgame.engine.core.GameSystem;
import com.trafficgame.engine.entity.Entity;
import com.trafficgame.engine.entity.EntityManager;
import com.trafficgame.game.config.GameConfig;
import com.trafficgame.game.model.PlayerProfile;
import com.trafficgame.game.model.VehicleInfo;
import com.trafficgame.game.model.VehicleMovement;

import java.util.Map;

/**
 * Calculates real-time traffic rating based on wait times, throughput, accidents, and emergency response.
 */
public final class RatingSystem implements GameSystem {

    private final EntityManager entityManager;
    private final PlayerProfile profile;
    private double avgWaitTime;
    private double throughputRate;
    private double accidentRate;
    private double emergencyResponseScore;
    private int totalDeliveries;
    private double timeSinceLastCalc;

    public RatingSystem(EntityManager entityManager, PlayerProfile profile) {
        this.entityManager = entityManager;
        this.profile = profile;
        this.avgWaitTime = 0;
        this.throughputRate = 0;
        this.accidentRate = 0;
        this.emergencyResponseScore = 1.0;
        this.totalDeliveries = 0;
        this.timeSinceLastCalc = 0;
    }

    @Override
    public void update(GameState state, double dt) {
        timeSinceLastCalc += dt;
        if (timeSinceLastCalc < 1.0) return; // recalculate every second
        timeSinceLastCalc = 0;

        // Calculate avg wait time score (lower is better)
        double totalWait = 0;
        int vehicleCount = 0;
        for (Entity e : entityManager.withComponent(VehicleInfo.class)) {
            VehicleInfo info = e.get(VehicleInfo.class);
            totalWait += info.getWaitTime();
            vehicleCount++;
        }
        if (vehicleCount > 0) {
            avgWaitTime = totalWait / vehicleCount;
        }
        double waitScore = Math.max(0, 1.0 - (avgWaitTime / 60.0)); // 60s wait = 0 score

        // Throughput score
        double throughputScore = Math.min(1.0, throughputRate / Math.max(vehicleCount * 0.1, 1));

        // Combine scores
        double rating = waitScore * GameConfig.RATING_WEIGHT_WAIT_TIME
                + emergencyResponseScore * GameConfig.RATING_WEIGHT_EMERGENCY
                + (1.0 - accidentRate) * GameConfig.RATING_WEIGHT_ACCIDENTS
                + throughputScore * GameConfig.RATING_WEIGHT_THROUGHPUT;

        profile.setRatingScore(rating);
        profile.setRatingGrade(calculateGrade(rating));
    }

    public void recordDelivery() {
        totalDeliveries++;
        throughputRate = totalDeliveries;
    }

    public void recordAccident() {
        accidentRate = Math.min(1.0, accidentRate + 0.05);
    }

    public void recordEmergencyResponse(double responseTime) {
        // Fast response (< 30s) gives high score
        double score = Math.max(0, 1.0 - (responseTime / 60.0));
        emergencyResponseScore = emergencyResponseScore * 0.9 + score * 0.1; // EMA
    }

    private String calculateGrade(double score) {
        if (score >= GameConfig.RATING_S_THRESHOLD) return "S";
        if (score >= GameConfig.RATING_A_THRESHOLD) return "A";
        if (score >= GameConfig.RATING_B_THRESHOLD) return "B";
        if (score >= GameConfig.RATING_C_THRESHOLD) return "C";
        if (score >= GameConfig.RATING_D_THRESHOLD) return "D";
        return "F";
    }

    public Map<String, Double> getBreakdown() {
        return Map.of(
                "waitTime", Math.max(0, 1.0 - (avgWaitTime / 60.0)),
                "emergency", emergencyResponseScore,
                "accidents", 1.0 - accidentRate,
                "throughput", Math.min(1.0, throughputRate / 10.0)
        );
    }

    @Override
    public String getName() { return "Rating"; }
}
