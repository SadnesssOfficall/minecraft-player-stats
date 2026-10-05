package net.sadness.playerstats.command;

import net.sadness.playerstats.stats.StatField;

public sealed interface StatsRequest {

    record Self() implements StatsRequest {
    }

    record Other(String playerName) implements StatsRequest {
    }

    record Top(StatField field, int page) implements StatsRequest {
    }
}
