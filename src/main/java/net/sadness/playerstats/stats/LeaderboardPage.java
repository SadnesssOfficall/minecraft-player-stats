package net.sadness.playerstats.stats;

import java.util.List;

public record LeaderboardPage(StatField field, Pagination pagination, List<LeaderboardEntry> entries) {

    public LeaderboardPage {
        entries = List.copyOf(entries);
    }
}
