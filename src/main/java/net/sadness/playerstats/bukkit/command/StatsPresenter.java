package net.sadness.playerstats.bukkit.command;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sadness.playerstats.bukkit.message.MessageKey;
import net.sadness.playerstats.bukkit.message.Messages;
import net.sadness.playerstats.format.StatFormatter;
import net.sadness.playerstats.stats.LeaderboardEntry;
import net.sadness.playerstats.stats.LeaderboardPage;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatField;
import org.bukkit.command.CommandSender;

final class StatsPresenter {

    private final Messages messages;

    StatsPresenter(Messages messages) {
        this.messages = messages;
    }

    void showStats(CommandSender sender, PlayerRecord record) {
        List<Component> lines = new ArrayList<>();
        lines.add(messages.render(MessageKey.STATS_HEADER, Placeholder.unparsed("player", record.name())));
        for (StatField field : StatField.values()) {
            lines.add(messages.render(MessageKey.STATS_LINE,
                    Placeholder.component("label", messages.label(field)),
                    Placeholder.unparsed("value", StatFormatter.format(field, record.values().get(field)))));
        }
        sender.sendMessage(Component.join(JoinConfiguration.newlines(), lines));
    }

    void showTop(CommandSender sender, LeaderboardPage page) {
        if (page.entries().isEmpty()) {
            messages.send(sender, MessageKey.TOP_EMPTY, Placeholder.component("label", messages.label(page.field())));
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(messages.render(MessageKey.TOP_HEADER,
                Placeholder.component("label", messages.label(page.field())),
                Placeholder.unparsed("page", Integer.toString(page.pagination().page())),
                Placeholder.unparsed("pages", Integer.toString(page.pagination().totalPages()))));
        int rank = page.pagination().offset();
        for (LeaderboardEntry entry : page.entries()) {
            rank++;
            lines.add(messages.render(MessageKey.TOP_LINE,
                    Placeholder.unparsed("rank", Integer.toString(rank)),
                    Placeholder.unparsed("player", entry.name()),
                    Placeholder.unparsed("value", StatFormatter.format(page.field(), entry.value()))));
        }
        sender.sendMessage(Component.join(JoinConfiguration.newlines(), lines));
    }
}
