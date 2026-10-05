package net.sadness.playerstats.service;

public final class StatsNotLoadedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StatsNotLoadedException() {
        super("Player statistics are not loaded yet");
    }
}
