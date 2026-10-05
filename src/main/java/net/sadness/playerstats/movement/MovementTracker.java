package net.sadness.playerstats.movement;

import java.util.UUID;

public final class MovementTracker {

    private static final double CENTIMETERS_PER_BLOCK = 100.0;

    private final double minStep;
    private final double maxStep;
    private final double[] carry = new double[MovementMode.values().length];

    private UUID world;
    private double x;
    private double y;
    private double z;

    public MovementTracker(double minStep, double maxStep) {
        if (!(minStep >= 0) || !(maxStep > minStep) || Double.isInfinite(maxStep)) {
            throw new IllegalArgumentException("Require 0 <= minStep < maxStep");
        }
        this.minStep = minStep;
        this.maxStep = maxStep;
    }

    public long record(UUID worldId, double newX, double newY, double newZ, MovementMode mode) {
        if (!isFinite(newX, newY, newZ)) {
            return 0L;
        }
        if (world == null || !world.equals(worldId)) {
            reanchor(worldId, newX, newY, newZ);
            return 0L;
        }
        double distance = Math.sqrt(square(newX - x) + square(newY - y) + square(newZ - z));
        if (distance > maxStep) {
            reanchor(worldId, newX, newY, newZ);
            return 0L;
        }
        if (distance < minStep) {
            return 0L;
        }
        reanchor(worldId, newX, newY, newZ);
        double centimeters = distance * CENTIMETERS_PER_BLOCK + carry[mode.ordinal()];
        long whole = (long) centimeters;
        carry[mode.ordinal()] = centimeters - whole;
        return whole;
    }

    public void reanchor(UUID worldId, double newX, double newY, double newZ) {
        if (!isFinite(newX, newY, newZ)) {
            return;
        }
        world = worldId;
        x = newX;
        y = newY;
        z = newZ;
    }

    private static boolean isFinite(double a, double b, double c) {
        return Double.isFinite(a) && Double.isFinite(b) && Double.isFinite(c);
    }

    private static double square(double value) {
        return value * value;
    }
}
