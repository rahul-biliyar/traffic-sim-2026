package com.trafficgame.engine.terrain;

/**
 * 2D grid of tiles representing the game world terrain.
 */
public final class TileMap {

    private final int width;
    private final int height;
    private final Tile[][] tiles;

    public TileMap(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Dimensions must be positive");
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width][height];
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public Tile get(int x, int y) {
        checkBounds(x, y);
        return tiles[x][y];
    }

    public void set(int x, int y, Tile tile) {
        checkBounds(x, y);
        tiles[x][y] = tile;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    private void checkBounds(int x, int y) {
        if (!inBounds(x, y)) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") out of bounds [" + width + "x" + height + "]");
        }
    }

    /**
     * Fill all tiles using a generator function.
     */
    public void fill(TileGenerator generator) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = generator.generate(x, y);
            }
        }
    }

    @FunctionalInterface
    public interface TileGenerator {
        Tile generate(int x, int y);
    }
}
