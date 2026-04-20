package com.trafficgame.engine.terrain;

/**
 * Wrapper around FastNoiseLite for generating terrain noise fields.
 * Provides simplified API for common terrain generation patterns.
 */
public final class NoiseGenerator {

    private final FastNoiseLite noise;

    public NoiseGenerator(int seed) {
        this.noise = new FastNoiseLite(seed);
        this.noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.noise.SetFrequency(0.01f);
        this.noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        this.noise.SetFractalOctaves(6);
    }

    /**
     * Generate a 2D noise value at (x, y), normalized to [0, 1].
     */
    public double get(double x, double y) {
        float raw = noise.GetNoise((float) x, (float) y);
        return (raw + 1.0) / 2.0; // normalize from [-1,1] to [0,1]
    }

    /**
     * Generate a full noise field for a grid.
     *
     * @param width    grid width
     * @param height   grid height
     * @param scale    higher = more zoomed in (larger features)
     * @return 2D array of noise values in [0,1]
     */
    public double[][] generateField(int width, int height, double scale) {
        double[][] field = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                field[x][y] = get(x / scale, y / scale);
            }
        }
        return field;
    }

    /**
     * Set the noise frequency (lower = larger features).
     */
    public NoiseGenerator frequency(float frequency) {
        noise.SetFrequency(frequency);
        return this;
    }

    /**
     * Set the number of fractal octaves (more = more detail).
     */
    public NoiseGenerator octaves(int octaves) {
        noise.SetFractalOctaves(octaves);
        return this;
    }

    /**
     * Create a secondary noise generator with a different seed for multi-layer terrain.
     */
    public static NoiseGenerator withSeed(int seed) {
        return new NoiseGenerator(seed);
    }
}
