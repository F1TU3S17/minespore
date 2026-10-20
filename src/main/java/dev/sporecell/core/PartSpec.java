package dev.sporecell.core;

public record PartSpec(int cost, double health, double thrust, double turn, double sight, double damage, int color) {
    public PartSpec {
        if (cost < 0 || cost > 1000 || !Double.isFinite(health + thrust + turn + sight + damage)
                || health < 0 || health > 1000 || thrust < 0 || thrust > 10 || turn < 0 || turn > 180
                || sight < 0 || sight > 64 || damage < 0 || damage > 1000) {
            throw new IllegalArgumentException("Invalid part statistics");
        }
    }
}
