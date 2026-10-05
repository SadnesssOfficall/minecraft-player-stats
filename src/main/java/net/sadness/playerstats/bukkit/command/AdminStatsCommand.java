package net.sadness.playerstats.bukkit.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sadness.playerstats.bukkit.message.MessageKey;
import net.sadness.playerstats.bukkit.message.Messages;
import net.sadness.playerstats.command.AdminCommandParser;
import net.sadness.playerstats.command.AdminRequest;
import net.sadness.playerstats.command.CommandSyntaxException;
import net.sadness.playerstats.format.StatFormatter;
import net.sadness.playerstats.service.StatsService;
import net.sadness.playerstats.stats.PlayerRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.Plugin;

public final class AdminStatsCommand implements TabExecutor {

    private static final String TOP = "top";
    private static final String RESET = "reset";
    private static final String SET = "set";
    private static final int ARGUMENT_FIRST = 1;
    private static final int ARGUMENT_ACTION = 2;
    private static final int ARGUMENT_FIELD = 3;

    private final StatsService service;
    private final Messages messages;
    private final Replies replies;
    private final StatsPresenter presenter;
    private final SyntaxErrorReporter reporter;
    private final int pageSize;

    public AdminStatsCommand(Plugin plugin, StatsService service, Messages messages, int pageSize) {
        this.service = service;
        this.messages = messages;
        this.replies = new Replies(plugin, messages, plugin.getLogger());
        this.presenter = new StatsPresenter(messages);
        this.reporter = new SyntaxErrorReporter(messages);
        this.pageSize = pageSize;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            messages.send(sender, MessageKey.NO_PERMISSION);
            return true;
        }
        AdminRequest request;
        try {
            request = AdminCommandParser.parse(args);
        } catch (CommandSyntaxException error) {
            reporter.report(sender, error, MessageKey.USAGE_ADMIN);
            return true;
        }
        if (request instanceof AdminRequest.Top top) {
            replies.deliver(sender, service.top(top.field(), top.page(), pageSize), page -> presenter.showTop(sender, page));
        } else if (request instanceof AdminRequest.Reset reset) {
            reset(sender, reset);
        } else if (request instanceof AdminRequest.Set set) {
            set(sender, set);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            return List.of();
        }
        if (args.length == ARGUMENT_FIRST) {
            List<String> candidates = new ArrayList<>(TabCompletions.onlinePlayers());
            candidates.add(TOP);
            return TabCompletions.filter(candidates, args[0]);
        }
        if (args.length == ARGUMENT_ACTION) {
            return TOP.equalsIgnoreCase(args[0]) ? TabCompletions.fields(args[1]) : TabCompletions.filter(List.of(RESET, SET), args[1]);
        }
        if (args.length == ARGUMENT_FIELD && SET.equalsIgnoreCase(args[1])) {
            return TabCompletions.fields(args[2]);
        }
        return List.of();
    }

    private void reset(CommandSender sender, AdminRequest.Reset request) {
        CompletableFuture<Optional<PlayerRecord>> outcome = service.findByName(request.playerName())
                .thenCompose(found -> found.isEmpty()
                        ? CompletableFuture.completedFuture(found)
                        : service.reset(found.get().id()).thenApply(done -> found));
        replies.deliver(sender, outcome, found -> {
            if (found.isPresent()) {
                messages.send(sender, MessageKey.ADMIN_RESET, Placeholder.unparsed("player", found.get().name()));
            } else {
                messages.send(sender, MessageKey.PLAYER_NOT_FOUND, Placeholder.unparsed("player", request.playerName()));
            }
        });
    }

    private void set(CommandSender sender, AdminRequest.Set request) {
        CompletableFuture<Optional<PlayerRecord>> outcome = service.findByName(request.playerName())
                .thenCompose(found -> found.isEmpty()
                        ? CompletableFuture.completedFuture(found)
                        : service.set(found.get().id(), found.get().name(), request.field(), request.displayValue())
                                .thenApply(done -> found));
        replies.deliver(sender, outcome, found -> {
            if (found.isPresent()) {
                messages.send(sender, MessageKey.ADMIN_SET,
                        Placeholder.unparsed("player", found.get().name()),
                        Placeholder.component("label", messages.label(request.field())),
                        Placeholder.unparsed("value", StatFormatter.format(request.field(), request.field().toStored(request.displayValue()))));
            } else {
                messages.send(sender, MessageKey.PLAYER_NOT_FOUND, Placeholder.unparsed("player", request.playerName()));
            }
        });
    }
}
