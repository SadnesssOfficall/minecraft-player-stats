package net.sadness.playerstats.bukkit.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.sadness.playerstats.stats.StatField;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class TabCompletions {

    private TabCompletions() {
    }

    static List<String> filter(Collection<String> candidates, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(lowered)).sorted().toList();
    }

    static List<String> fields(String prefix) {
        return filter(Stream.of(StatField.values()).map(StatField::key).toList(), prefix);
    }

    static List<String> onlinePlayers() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
