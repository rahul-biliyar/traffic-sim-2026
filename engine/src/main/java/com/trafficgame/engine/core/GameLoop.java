package com.trafficgame.engine.core;

/**
 * Fixed-timestep game loop with accumulator pattern.
 * Decouples simulation rate from wall-clock time.
 *
 * <p>Usage: call {@link #tick()} from your scheduler (e.g., @Scheduled).
 * The loop accumulates elapsed wall-clock time and runs simulation steps
 * at a fixed dt to ensure deterministic behavior.</p>
 */
public final class GameLoop {

    private final double fixedDt;
    private final SystemOrchestrator orchestrator;
    private final GameState state;

    private long lastTimeNanos;
    private double accumulator;
    private boolean started;

    /**
     * @param tickRateMs  desired tick interval in milliseconds (e.g., 100 for 10 Hz)
     * @param orchestrator the system orchestrator to run each tick
     * @param state        the game state to pass to systems
     */
    public GameLoop(long tickRateMs, SystemOrchestrator orchestrator, GameState state) {
        this.fixedDt = tickRateMs / 1000.0;
        this.orchestrator = orchestrator;
        this.state = state;
        this.accumulator = 0;
        this.started = false;
    }

    /**
     * Called by the external scheduler. Accumulates real elapsed time
     * and runs zero or more fixed-timestep simulation steps.
     *
     * @return number of simulation steps executed this call
     */
    public int tick() {
        long now = System.nanoTime();
        if (!started) {
            lastTimeNanos = now;
            started = true;
            return 0;
        }

        double elapsed = (now - lastTimeNanos) / 1_000_000_000.0;
        lastTimeNanos = now;

        // Cap accumulated time to prevent spiral of death
        if (elapsed > 0.25) {
            elapsed = 0.25;
        }
        accumulator += elapsed;

        int steps = 0;
        while (accumulator >= fixedDt) {
            state.advanceTick();
            orchestrator.update(state, fixedDt);
            accumulator -= fixedDt;
            steps++;
        }

        return steps;
    }

    /**
     * Returns the interpolation alpha (0..1) for client-side rendering.
     * Represents how far between the last tick and the next tick we are.
     */
    public double getAlpha() {
        return accumulator / fixedDt;
    }

    public double getFixedDt() {
        return fixedDt;
    }

    /**
     * Force a single simulation step (useful for testing).
     */
    public void stepOnce() {
        state.advanceTick();
        orchestrator.update(state, fixedDt);
    }
}
