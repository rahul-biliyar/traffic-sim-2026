package com.trafficgame.game.model;

/**
 * Per-type vehicle configuration parameters.
 */
public record VehicleConfig(
    VehicleType type,
    double maxSpeed,        // units per second
    double acceleration,    // units per second^2
    double deceleration,    // units per second^2 (braking)
    double length,          // visual size
    int priority,           // higher = more right-of-way (emergency = 100)
    double spawnWeight      // relative spawn probability
) {

    public static VehicleConfig car() {
        return new VehicleConfig(VehicleType.CAR, 60, 3.5, 5.0, 4.5, 1, 0.6);
    }

    public static VehicleConfig truck() {
        return new VehicleConfig(VehicleType.TRUCK, 40, 1.5, 3.0, 8.0, 1, 0.15);
    }

    public static VehicleConfig bus() {
        return new VehicleConfig(VehicleType.BUS, 45, 2.0, 4.0, 12.0, 2, 0.1);
    }

    public static VehicleConfig emergency() {
        return new VehicleConfig(VehicleType.EMERGENCY, 80, 5.0, 7.0, 5.5, 100, 0.05);
    }

    public static VehicleConfig taxi() {
        return new VehicleConfig(VehicleType.TAXI, 55, 3.5, 5.0, 4.5, 1, 0.08);
    }

    public static VehicleConfig motorcycle() {
        return new VehicleConfig(VehicleType.MOTORCYCLE, 70, 5.0, 6.0, 2.5, 1, 0.02);
    }

    public static VehicleConfig forType(VehicleType type) {
        return switch (type) {
            case CAR -> car();
            case TRUCK -> truck();
            case BUS -> bus();
            case EMERGENCY -> emergency();
            case TAXI -> taxi();
            case MOTORCYCLE -> motorcycle();
        };
    }
}
