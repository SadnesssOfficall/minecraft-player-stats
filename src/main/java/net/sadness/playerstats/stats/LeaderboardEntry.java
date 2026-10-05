package net.sadness.playerstats.stats;

import java.util.UUID;

public record LeaderboardEntry(UUID id, String name, long value) {
}
