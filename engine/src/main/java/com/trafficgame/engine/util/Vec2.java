package com.trafficgame.engine.util;

/**
 * Immutable 2D vector using double precision.
 */
public record Vec2(double x, double y) {

    public static final Vec2 ZERO = new Vec2(0, 0);

    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    public Vec2 subtract(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    public Vec2 scale(double factor) {
        return new Vec2(x * factor, y * factor);
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public double lengthSquared() {
        return x * x + y * y;
    }

    public double distanceTo(Vec2 other) {
        return subtract(other).length();
    }

    public Vec2 normalize() {
        double len = length();
        if (len < 1e-10) return ZERO;
        return scale(1.0 / len);
    }

    public Vec2 lerp(Vec2 target, double alpha) {
        return new Vec2(
            x + (target.x - x) * alpha,
            y + (target.y - y) * alpha
        );
    }

    public double dot(Vec2 other) {
        return x * other.x + y * other.y;
    }
}
