package net.sadness.playerstats.bukkit.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sadness.playerstats.bukkit.message.MessageKey;
import net.sadness.playerstats.bukkit.message.Messages;
import net.sadness.playerstats.command.CommandSyntaxException;
import net.sadness.playerstats.command.StatsCommandParser;
import net.sadness.playerstats.command.StatsRequest;
import net.sadness.playerstats.service.StatsService;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatValues;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StatsCommand implements TabExecutor {

    private static final String TOP = "top";
    private static final int ARGUMENT_PLAYER_OR_TOP = 1;
    private static final int ARGUMENT_TOP_FIELD = 2;

    private final StatsService service;
    private final Messages messages;
    private final Replies replies;
    private final StatsPresenter presenter;
    private final SyntaxErrorReporter reporter;
    private final int pageSize;

    public StatsCommand(Plugin plugin, StatsService service, Messages messages, int pageSize) {
        this.service = service;
        this.messages = messages;
        this.replies = new Replies(plugin, messages, plugin.getLogger());
        this.presenter = new StatsPresenter(messages);
        this.reporter = new SyntaxErrorReporter(messages);
        this.pageSize = pageSize;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StatsRequest request;
        try {
            request = StatsCommandParser.parse(args);
        } catch (CommandSyntaxException error) {
            reporter.report(sender, error, MessageKey.USAGE_STATS);
            return true;
        }
        if (!sender.hasPermission(Permissions.VIEW)) {
            messages.send(sender, MessageKey.NO_PERMISSION);
        } else if (request instanceof StatsRequest.Self) {
            showSelf(sender);
        } else if (request instanceof StatsRequest.Other other) {
            showOther(sender, other.playerName());
        } else if (request instanceof StatsRequest.Top top) {
            replies.deliver(sender, service.top(top.field(), top.page(), pageSize), page -> presenter.showTop(sender, page));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == ARGUMENT_PLAYER_OR_TOP) {
            List<String> candidates = new ArrayList<>();
            candidates.add(TOP);
            if (sender.hasPermission(Permissions.VIEW_OTHERS)) {
                candidates.addAll(TabCompletions.onlinePlayers());
            }
            return TabCompletions.filter(candidates, args[0]);
        }
        if (args.length == ARGUMENT_TOP_FIELD && TOP.equalsIgnoreCase(args[0])) {
            return TabCompletions.fields(args[1]);
        }
        return List.of();
    }

    private void showSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, MessageKey.PLAYER_ONLY);
            return;
        }
        replies.deliver(sender, service.find(player.getUniqueId()), found -> presenter.showStats(
                sender, found.orElseGet(() -> new PlayerRecord(player.getUniqueId(), player.getName(), StatValues.ZERO))));
    }

    private void showOther(CommandSender sender, String name) {
        if (!sender.hasPermission(Permissions.VIEW_OTHERS)) {
            messages.send(sender, MessageKey.NO_PERMISSION);
            return;
        }
        replies.deliver(sender, service.findByName(name), found -> report(sender, name, found));
    }

    private void report(CommandSender sender, String name, Optional<PlayerRecord> found) {
        if (found.isPresent()) {
            presenter.showStats(sender, found.get());
        } else {
            messages.send(sender, MessageKey.PLAYER_NOT_FOUND, Placeholder.unparsed("player", name));
        }
    }
}
